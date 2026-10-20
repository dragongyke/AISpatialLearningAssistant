package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests, including paid real-network cases: opt in with -e runVoiceNetworkTests true. Uses
 * configured production credentials without logging them or question/answer text. Playback-head
 * advancement proves device playback, not acoustic output at the listener's ear. Questions are
 * supplied as text: these tests do not benchmark microphones or real ASR. Optional -e arkModel
 * <model> overrides only the benchmark answer model.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LearningVoiceLatencyDeviceTest {
    @Before
    fun requireExplicitNetworkOptIn() {
        assumeTrue(
            "External paid voice tests require runVoiceNetworkTests=true.",
            InstrumentationRegistry.getArguments().getString("runVoiceNetworkTests") == "true",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent =
            checkNotNull(
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                ) {
                    "Target app has no launcher activity."
                }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "startActivitySync must run on the instrumentation thread, not Main."
        }
        instrumentation.startActivitySync(launchIntent)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertTrue(
            "Unmute media and set an audible media volume before running voice tests.",
            !audio.isStreamMute(AudioManager.STREAM_MUSIC) &&
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0,
        )
    }

    @Test
    fun chineseDerivativeAnswerStreamsLiveTextAndCompletesAudiblePlayback() = runBlocking {
        runAnswerCase(
            caseName = "zh_derivative",
            question =
                "\u5bfc\u6570\u662f\u4ec0\u4e48\u610f\u601d\uff1f" +
                    "\u8bf7\u7528\u4e2d\u6587\u56de\u7b54\u4e24\u5230\u4e09\u53e5\uff0c\u5e76\u4e3e\u4e00\u4e2a\u7b80\u5355\u7684\u751f\u6d3b\u4f8b\u5b50\u3002",
        )
    }

    @Test
    fun englishCodingAnswerStreamsLiveTextAndCompletesAudiblePlayback() = runBlocking {
        runAnswerCase(
            caseName = "en_coding",
            question =
                "What is a variable in programming? " +
                    "Answer in English in two or three short sentences with a simple example.",
        )
    }

    @Test
    fun defaultCourseVisualAnswerAtThirtySecondsCapturesFramesAndCompletesAudiblePlayback() =
        runBlocking {
            runVisualAnswerCase("visual_prefetched_course_30s", prefetchWhileSpeaking = true)
        }

    @Test
    fun coldDefaultCourseVisualAnswerAtThirtySecondsRequiresFramesAndLivePlayback() = runBlocking {
        runVisualAnswerCase("visual_cold_course_30s", prefetchWhileSpeaking = false)
    }

    private suspend fun runVisualAnswerCase(caseName: String, prefetchWhileSpeaking: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val course = LearningCourseCatalog.defaultVideo
        val video = course.toLearningVideo()
        runAnswerCase(
            caseName = caseName,
            question =
                "What is visible in the lecture video at this point? " +
                    "Answer in two or three short English sentences using the attached frames. " +
                    "Say when any text is unreadable instead of guessing.",
            state =
                LearningUiState(
                    // Do not let a previous test's shared frame cache disguise a cold capture.
                    sessionId = SystemClock.elapsedRealtimeNanos(),
                    video = video,
                    chapters = course.chapters,
                    transcriptSegments = TranscriptRepository(context).load(video),
                ),
            timestampMs = 30_000L,
            captureVideoFrames = true,
            prefetchWhileSpeaking = prefetchWhileSpeaking,
        )
    }

    @Test
    fun multiSentenceBargeInCancelsOldPlaybackAndImmediatelyStartsFreshSession() = runBlocking {
        val outputs = CopyOnWriteArrayList<ObservedPcmOutput>()
        val speaker = newSpeaker(outputs)
        val oldInput = Channel<String>(Channel.UNLIMITED)
        val freshInput = Channel<String>(Channel.UNLIMITED)
        val oldCurrent = AtomicBoolean(true)
        val freshCurrent = AtomicBoolean(true)
        val oldTiming = VoiceTiming("tts_interrupted")
        var freshTiming: VoiceTiming? = null
        try {
            enqueueSentences(
                oldInput,
                "A derivative measures how quickly a function changes at one point. " +
                    "Imagine following the distance of a moving train over time. " +
                    "Its speed tells you how quickly that distance is changing at each moment. ",
            )
            // Keep the old producer open so cancellation must interrupt an active session.
            enqueueSentences(
                freshInput,
                "A variable stores a value in a program. " +
                    "For example, a counter can start at zero. " +
                    "Adding one updates the value stored in that counter. ",
            )
            freshInput.close()
            withTimeout(NETWORK_TIMEOUT_MS) {
                val oldPlayback =
                    launch(Dispatchers.IO) {
                        speaker.speakStream(
                            text = oldInput,
                            isCurrent = oldCurrent::get,
                            onFirstText = oldTiming::markFirstText,
                            onPlaybackStarted = oldTiming::markFirstAudio,
                            onResult = oldTiming::markResult,
                        )
                    }
                withTimeout(FIRST_AUDIO_TIMEOUT_MS) {
                    select<Unit> {
                        oldTiming.firstAudio.onAwait {}
                        oldPlayback.onJoin {}
                    }
                }
                assertTrue(
                    "Old session must play actual audio before barge-in.",
                    oldTiming.firstAudio.isCompleted
                )
                assertFalse("Old session ended before barge-in.", oldPlayback.isCompleted)
                assertEquals("Expected one continuous old audio output.", 1, outputs.size)
                val interruptedOutput = outputs.single()
                val cancelledAt = SystemClock.elapsedRealtimeNanos()
                oldCurrent.set(false)
                speaker.stop()

                val nextTiming = VoiceTiming("tts_fresh")
                freshTiming = nextTiming
                // Deliberately start before joining the old job: exercise teardown/startup races.
                val freshPlayback =
                    launch(Dispatchers.IO) {
                        speaker.speakStream(
                            text = freshInput,
                            isCurrent = freshCurrent::get,
                            onFirstText = nextTiming::markFirstText,
                            onPlaybackStarted = nextTiming::markFirstAudio,
                            onResult = nextTiming::markResult,
                        )
                    }
                withTimeout(CANCEL_TIMEOUT_MS) { oldPlayback.join() }
                Log.i(
                    TAG,
                    "case=barge_in cancel_to_join_ms=" +
                        TimeUnit.NANOSECONDS.toMillis(
                            SystemClock.elapsedRealtimeNanos() - cancelledAt
                        )
                )
                assertTrue("Barge-in must cancel the old coroutine.", oldPlayback.isCancelled)
                assertTrue("Interrupted output must be stopped.", interruptedOutput.stopped.get())
                assertTrue("Interrupted output must be released.", interruptedOutput.closed.get())
                assertFalse(
                    "Interrupted session must not report a full drain.",
                    interruptedOutput.drained.get()
                )

                freshPlayback.join()
                nextTiming.assertCompleted()
                assertEquals("Each session must use one continuous output.", 2, outputs.size)
                outputs.last().assertDrained()
                assertEquals(
                    "Interrupted session must not publish a result.",
                    0,
                    oldTiming.resultCalls.get()
                )
                assertEquals(
                    "Interrupted session must not publish late playback starts.",
                    1,
                    oldTiming.audioCalls.get()
                )
            }
        } finally {
            oldCurrent.set(false)
            freshCurrent.set(false)
            speaker.stop()
            oldInput.cancel()
            freshInput.cancel()
            oldTiming.report()
            freshTiming?.report()
        }
    }

    @Test
    fun nativePcmHundredMillisecondToneDrainsAndReportsFirstAudioOnce() = runBlocking {
        runNativePcmCase(
            caseName = "pcm_short_100ms",
            pcm = hundredMillisecondTone(),
            expectedFrames = 2_400,
            toneMarkerFrame = 1L,
        )
    }

    @Test
    fun nativePcmLeadingSilenceDoesNotReportFirstAudioBeforeToneMarker() = runBlocking {
        runNativePcmCase(
            caseName = "pcm_silence_300ms_tone_100ms",
            pcm = ByteArray(14_400) + hundredMillisecondTone(),
            expectedFrames = 9_600,
            toneMarkerFrame = 7_201L,
        )
    }

    private suspend fun runNativePcmCase(
        caseName: String,
        pcm: ByteArray,
        expectedFrames: Int,
        toneMarkerFrame: Long,
    ) {
        assertEquals(
            "Fixture must contain the expected PCM16 frame count.",
            expectedFrames * 2,
            pcm.size
        )
        val callbackCount = AtomicInteger()
        val callbackHead = AtomicLong(-1L)
        val callbackAt = AtomicLong(-1L)
        val firstAudio = CompletableDeferred<Unit>()
        val trackReference = AtomicReference<AudioTrack>()
        val output = AndroidPcmSpeechOutput {
            val head = trackReference.get().playbackHeadPosition.toLong() and 0xffffffffL
            callbackHead.compareAndSet(-1L, head)
            callbackAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
            callbackCount.incrementAndGet()
            firstAudio.complete(Unit)
        }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        var drainedAt = -1L
        try {
            // Inspect the real native playback head without adding a production-only test API.
            // Frame positions avoid flaky wall-clock assumptions about output startup latency.
            val track = nativeAudioTrack(output)
            val initialUnderruns = track.underrunCount
            trackReference.set(track)
            withTimeout(PCM_TIMEOUT_MS) {
                output.write(pcm)
                output.finish()
                drainedAt = SystemClock.elapsedRealtimeNanos()
                Log.i(
                    TAG,
                    "case=$caseName pcm_underrun_delta=${track.underrunCount - initialUnderruns}"
                )
                firstAudio.await()
            }
            assertTrue(
                "finish() must drain every submitted PCM frame, including a sub-160ms clip.",
                (track.playbackHeadPosition.toLong() and 0xffffffffL) >= expectedFrames.toLong(),
            )
            assertEquals(
                "Real PCM playback must report first audio exactly once.",
                1,
                callbackCount.get()
            )
            assertTrue(
                "First audio must not be reported before the first non-silent PCM frame plays.",
                callbackHead.get() >= toneMarkerFrame,
            )
        } finally {
            output.close()
            fun elapsed(at: Long): Long =
                if (at < 0L) -1L else TimeUnit.NANOSECONDS.toMillis(at - startedAt)
            Log.i(
                TAG,
                "case=$caseName start_to_first_audio_ms=" +
                    elapsed(callbackAt.get()) +
                    " start_to_drain_ms=" +
                    elapsed(drainedAt)
            )
        }
    }

    private fun hundredMillisecondTone(): ByteArray =
        ByteBuffer.allocate(4_800)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(2_400) { frame ->
                    // Mono 24 kHz PCM16, 440 Hz at quarter scale; phase makes frame one nonzero.
                    val sample = (8_192 * sin(2.0 * PI * 440 * frame / 24_000 + PI / 2)).toInt()
                    putShort(sample.toShort())
                }
            }
            .array()

    private suspend fun runAnswerCase(
        caseName: String,
        question: String,
        state: LearningUiState = LearningUiState(),
        timestampMs: Long = 0L,
        captureVideoFrames: Boolean = false,
        prefetchWhileSpeaking: Boolean = false,
    ) {
        val observedFrameCount = AtomicInteger(-1)
        val defaultConfiguration = DoubaoAssistantConfiguration()
        val modelOverride =
            InstrumentationRegistry.getArguments()
                .getString("arkModel")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        val engine =
            DoubaoLearningAssistantEngine(
                configuration =
                    modelOverride?.let { defaultConfiguration.copy(model = it) }
                        ?: defaultConfiguration,
                onObservation = { observation ->
                    observedFrameCount.set(observation.frameCount)
                    // Retain the production-safe metadata log, never request or answer content.
                    Log.i("ArkAnswer", "case=$caseName $observation")
                },
            )
        assertTrue("Opted-in test requires configured Ark credentials.", engine.isConfigured)
        val outputs = CopyOnWriteArrayList<ObservedPcmOutput>()
        val speaker = newSpeaker(outputs, caseName)
        val input = Channel<String>(Channel.UNLIMITED)
        val current = AtomicBoolean(true)
        val timing = VoiceTiming(caseName)
        val rejectedChunk = AtomicBoolean(false)
        val changedPrefix = AtomicBoolean(false)
        val chunkCount = AtomicInteger()
        val chunker = StreamingSentenceChunker(lowLatency = true)
        var streamedAnswer = ""
        fun enqueue(sentence: String) {
            chunkCount.incrementAndGet()
            if (!input.trySend(sentence).isSuccess) rejectedChunk.set(true)
        }
        try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                val analyzer =
                    if (captureVideoFrames) {
                        DoubaoVideoVisualContextAnalyzer(
                            InstrumentationRegistry.getInstrumentation().targetContext,
                        )
                    } else null
                if (prefetchWhileSpeaking) {
                    checkNotNull(analyzer)
                    assertTrue("VM prefetch requires configured vision.", analyzer.isConfigured)
                    val speakingStartedAt = SystemClock.elapsedRealtimeNanos()
                    try {
                        analyzer.prefetchFrames(timestampMs = timestampMs, state = state)
                        // Simulates time spent speaking, not an ASR or microphone measurement.
                        delay(SIMULATED_SPEAKING_WINDOW_MS)
                    } finally {
                        timing.recordSimulatedSpeaking(speakingStartedAt)
                    }
                }
                // Capture wait remains in answer latency; only the speaking window is excluded.
                val capture =
                    if (analyzer != null) {
                        captureRealVideoFrames(analyzer, state, timestampMs, timing)
                    } else null
                val playback =
                    launch(Dispatchers.IO) {
                        speaker.speakStream(
                            text = input,
                            isCurrent = current::get,
                            onFirstText = timing::markFirstText,
                            onPlaybackStarted = timing::markFirstAudio,
                            onResult = timing::markResult,
                        )
                    }
                val answer =
                    try {
                        engine.answer(
                            question = question,
                            timestampMs = timestampMs,
                            previousAnswers = emptyList(),
                            state = state,
                            visualContext = null,
                            visualFrames = capture?.frames.orEmpty(),
                            onPartialAnswer = { partial ->
                                if (partial.isNotBlank()) timing.markFirstToken()
                                // Engine callbacks contain cumulative text, not individual deltas.
                                if (partial.startsWith(streamedAnswer)) {
                                    chunker
                                        .append(partial.substring(streamedAnswer.length))
                                        .forEach(::enqueue)
                                } else {
                                    changedPrefix.set(true)
                                }
                                streamedAnswer = partial
                            },
                        )
                    } finally {
                        chunker.finish()?.let(::enqueue)
                        input.close()
                    }
                assertTrue(
                    "Answer must be live, never fallback or error. " +
                        "case=$caseName mode=${answer.mode} status=${answer.statusMessage}",
                    answer.mode == AssistantResponseMode.Live
                )
                if (captureVideoFrames) {
                    assertTrue(
                        "Live Ark request must include captured video frames.",
                        observedFrameCount.get() > 0
                    )
                }
                assertTrue(
                    "Live answer must contain text.",
                    answer.questionAnswer.answerText.isNotBlank()
                )
                assertFalse("Live text must remain an append-only stream.", changedPrefix.get())
                assertTrue(
                    "Stream must include the full final answer.",
                    streamedAnswer.trim() == answer.questionAnswer.answerText.trim(),
                )
                assertFalse(
                    "No sentence may be dropped from the speech channel.",
                    rejectedChunk.get()
                )
                assertTrue("Chunker must produce speech input.", chunkCount.get() > 0)
                playback.join()
                timing.assertCompleted(expectToken = true)
                assertEquals("Entire answer must share one audio output.", 1, outputs.size)
                outputs.single().assertDrained()
                if (capture != null) {
                    // Verify the cache after playback, without adding verification work to TTFA.
                    assertTrue(
                        "The current video frame must remain available from the production cache.",
                        currentFrameIsCached(
                            checkNotNull(analyzer),
                            state,
                            timestampMs,
                            capture,
                            timing
                        ),
                    )
                }
            }
        } finally {
            current.set(false)
            speaker.stop()
            input.cancel()
            timing.report()
        }
    }

    private suspend fun captureRealVideoFrames(
        analyzer: DoubaoVideoVisualContextAnalyzer,
        state: LearningUiState,
        timestampMs: Long,
        timing: VoiceTiming,
    ): VisualFrameCaptureResult {
        val captureStartedAt = SystemClock.elapsedRealtimeNanos()
        val capture =
            try {
                // Cooperative guard only: MediaMetadataRetriever native calls can overrun it.
                withTimeout(VISUAL_CAPTURE_TIMEOUT_MS) {
                    analyzer.captureFrames(timestampMs = timestampMs, state = state)
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                null
            } finally {
                timing.recordFrameCapture(captureStartedAt)
            }
        if (capture == null || capture.frames.isEmpty()) {
            // Observe late completion, but never turn the failed initial capture into a pass.
            val lateCapture = observeLateCachedFrame(analyzer, state, timestampMs)
            val lateFrameCached =
                if (lateCapture != null) {
                    timing.markFrameObserved()
                    currentFrameIsCached(analyzer, state, timestampMs, lateCapture, timing)
                } else false
            // Production masks retrieval causes. This separate probe is diagnostic, not proof
            // that MediaMetadataRetriever failed for the same reason. Probe only after observation.
            val diagnostic = videoAccessDiagnostic(state.video.sourceUri)
            val reason = capture?.statusMessage ?: "Frame capture exceeded the cooperative timeout."
            throw AssertionError(
                "Real video capture at ${timestampMs}ms returned no usable frames: $reason " +
                    "late_frame_observed=${lateCapture != null} late_current_frame_cached=$lateFrameCached; " +
                    "$diagnostic; native retrieval may overrun the coroutine timeout.",
            )
        }
        timing.markFrameObserved()
        assertTrue(
            "Captured frames must belong to the current requested video timestamp.",
            capture.frames.all { it.timestampMs == timestampMs },
        )
        return capture
    }

    private suspend fun observeLateCachedFrame(
        analyzer: DoubaoVideoVisualContextAnalyzer,
        state: LearningUiState,
        timestampMs: Long,
    ): VisualFrameCaptureResult? {
        // Cache-only observation avoids restarting failed downloads. Use the publication lock.
        val worker =
            DoubaoVideoVisualContextAnalyzer::class.java.getDeclaredField("frameWorker").let {
                it.isAccessible = true
                it.get(analyzer) as BoundedVideoFrameCaptureWorker
            }
        val cacheField =
            BoundedVideoFrameCaptureWorker::class.java.getDeclaredField("cached").apply {
                isAccessible = true
            }
        return withTimeoutOrNull(LATE_FRAME_OBSERVATION_MS) {
            var frame: CapturedVideoFrame? = null
            while (frame == null) {
                frame =
                    synchronized(worker) {
                        val entry = cacheField.get(worker) as? Pair<*, *>
                        val request = entry?.first as? VideoFrameCaptureRequest
                        (entry?.second as? CapturedVideoFrame)?.takeIf {
                            request != null &&
                                request.videoId == state.video.id &&
                                request.sourceUri == state.video.sourceUri &&
                                request.sessionId == state.sessionId &&
                                request.timestampMs == timestampMs &&
                                it.timestampMs == timestampMs
                        }
                    }
                if (frame == null) delay(50L)
            }
            VisualFrameCaptureResult(listOf(checkNotNull(frame)), "Observed late cached frame.")
        }
    }

    private suspend fun currentFrameIsCached(
        analyzer: DoubaoVideoVisualContextAnalyzer,
        state: LearningUiState,
        timestampMs: Long,
        capture: VisualFrameCaptureResult,
        timing: VoiceTiming,
    ): Boolean {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val cached =
            try {
                withTimeout(VISUAL_CAPTURE_TIMEOUT_MS) {
                    analyzer.captureFrames(timestampMs = timestampMs, state = state)
                }
            } finally {
                timing.recordCacheRead(startedAt)
            }
        return capture.frames.isNotEmpty() &&
            capture.frames.all { it.timestampMs == timestampMs } &&
            cached.frames.size == capture.frames.size &&
            cached.frames.zip(capture.frames).all { (again, original) -> again === original }
    }

    private suspend fun videoAccessDiagnostic(sourceUri: String): String =
        withContext(Dispatchers.IO) {
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(8, TimeUnit.SECONDS)
                    .build()
            try {
                val request =
                    Request.Builder()
                        .url(sourceUri)
                        .header("Range", "bytes=0-0")
                        .header("Accept-Encoding", "identity")
                        .get()
                        .build()
                client.newCall(request).execute().use { "video_range_probe_http=${it.code}" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "video_range_probe_exception=${error.javaClass.simpleName}"
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }

    private fun newSpeaker(
        outputs: MutableList<ObservedPcmOutput>,
        caseName: String = "tts_barge_in",
    ): DoubaoBidirectionalSpeechAnswerSpeaker =
        DoubaoBidirectionalSpeechAnswerSpeaker(
                outputFactory = { onFirstAudio ->
                    ObservedPcmOutput(onFirstAudio, caseName).also { outputs.add(it) }
                },
            )
            .also {
                assertTrue(
                    "Opted-in test requires configured Doubao TTS credentials.",
                    it.isConfigured
                )
            }

    private fun enqueueSentences(channel: Channel<String>, text: String) {
        val chunker = StreamingSentenceChunker(lowLatency = true)
        var count = 0
        fun enqueue(sentence: String) {
            assertTrue(
                "Speech channel must accept every sentence.",
                channel.trySend(sentence).isSuccess
            )
            count++
        }
        text.chunked(11).forEach { delta -> chunker.append(delta).forEach(::enqueue) }
        chunker.finish()?.let(::enqueue)
        assertTrue("Fixture must exercise multiple sentence chunks.", count >= 2)
    }

    // Observes the real AudioTrack path; no mock audio sink and no status-message matching.
    private class ObservedPcmOutput(
        onFirstAudio: () -> Unit,
        private val caseName: String,
    ) : PcmSpeechOutput {
        private val delegate = AndroidPcmSpeechOutput(onFirstAudio)
        private val track = nativeAudioTrack(delegate)
        private val initialUnderruns = track.underrunCount
        private val nonSilentPcm = AtomicBoolean(false)
        val drained = AtomicBoolean(false)
        val stopped = AtomicBoolean(false)
        val closed = AtomicBoolean(false)

        override suspend fun write(pcm: ByteArray) {
            if (pcm.any { it != 0.toByte() }) nonSilentPcm.set(true)
            delegate.write(pcm)
        }

        override suspend fun finish() {
            delegate.finish()
            drained.set(true)
            // Report observed starvation; network jitter is not a zero-underrun test assertion.
            Log.i(
                TAG,
                "case=$caseName pcm_underrun_delta=${track.underrunCount - initialUnderruns}"
            )
        }

        override fun stop() {
            delegate.stop()
            stopped.set(true)
        }

        override fun close() {
            delegate.close()
            closed.set(true)
        }

        fun assertDrained() {
            assertTrue("TTS must produce non-silent PCM.", nonSilentPcm.get())
            assertTrue("All audio must drain successfully, not merely start.", drained.get())
            assertTrue("Completed output must be released.", closed.get())
        }
    }

    private class VoiceTiming(private val caseName: String) {
        private val startedAt = SystemClock.elapsedRealtimeNanos()
        private val tokenAt = AtomicLong(-1L)
        private val textAt = AtomicLong(-1L)
        private val audioAt = AtomicLong(-1L)
        private val resultAt = AtomicLong(-1L)
        private val frameCaptureMs = AtomicLong(-1L)
        private val speakingWindowNanos = AtomicLong(0L)
        private val frameObservedAt = AtomicLong(-1L)
        private val cacheReadMs = AtomicLong(-1L)
        private val textCalls = AtomicInteger()
        private val result = AtomicReference<SpeechPlaybackResult?>()
        val audioCalls = AtomicInteger()
        val resultCalls = AtomicInteger()
        val firstAudio = CompletableDeferred<Unit>()

        fun recordSimulatedSpeaking(speakingStartedAt: Long) {
            speakingWindowNanos.set(SystemClock.elapsedRealtimeNanos() - speakingStartedAt)
        }

        fun markFrameObserved() {
            frameObservedAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
        }

        fun recordCacheRead(cacheReadStartedAt: Long) {
            cacheReadMs.set(
                TimeUnit.NANOSECONDS.toMillis(
                    SystemClock.elapsedRealtimeNanos() - cacheReadStartedAt
                ),
            )
        }

        fun recordFrameCapture(captureStartedAt: Long) {
            frameCaptureMs.set(
                TimeUnit.NANOSECONDS.toMillis(
                    SystemClock.elapsedRealtimeNanos() - captureStartedAt
                ),
            )
        }

        fun markFirstToken() {
            tokenAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
        }

        fun markFirstText() {
            textCalls.incrementAndGet()
            textAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
        }

        fun markFirstAudio() {
            audioCalls.incrementAndGet()
            audioAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
            firstAudio.complete(Unit)
        }

        fun markResult(value: SpeechPlaybackResult) {
            resultCalls.incrementAndGet()
            result.set(value)
            resultAt.compareAndSet(-1L, SystemClock.elapsedRealtimeNanos())
        }

        fun assertCompleted(expectToken: Boolean = false) {
            assertEquals("One continuous stream must consume first text once.", 1, textCalls.get())
            assertEquals("First actual audio must be reported exactly once.", 1, audioCalls.get())
            assertEquals("Completed stream must publish exactly one result.", 1, resultCalls.get())
            assertTrue(
                "Playback result must confirm audible output.",
                result.get()?.wasPlayed == true
            )
            assertTrue(
                "Text must precede actual audio.",
                textAt.get() >= startedAt && audioAt.get() >= textAt.get()
            )
            assertTrue("Completion must follow actual audio.", resultAt.get() >= audioAt.get())
            if (expectToken) {
                assertTrue("Live stream must report a first token.", tokenAt.get() >= startedAt)
                assertTrue("First token must precede speech input.", textAt.get() >= tokenAt.get())
            }
        }

        fun report() {
            fun elapsed(event: AtomicLong, excludeSpeaking: Boolean = true): Long =
                event.get().let {
                    if (it < 0L) -1L
                    else
                        TimeUnit.NANOSECONDS.toMillis(
                            it -
                                startedAt -
                                (if (excludeSpeaking) speakingWindowNanos.get() else 0L),
                        )
                }
            // -1 means no event (e.g. no LLM in TTS-only tests, or a cancelled result).
            // Exclude only the measured simulated speaking window, never capture or retry time.
            Log.i(
                TAG,
                "case=$caseName prompt_to_first_token_ms=" +
                    elapsed(tokenAt) +
                    " prompt_to_first_text_ms=" +
                    elapsed(textAt) +
                    " prompt_to_first_audio_ms=" +
                    elapsed(audioAt) +
                    " prompt_to_result_ms=" +
                    elapsed(resultAt) +
                    " frame_capture_ms=" +
                    frameCaptureMs.get() +
                    " simulated_speaking_window_ms=" +
                    TimeUnit.NANOSECONDS.toMillis(speakingWindowNanos.get()) +
                    " total_start_to_first_audio_ms=" +
                    elapsed(audioAt, excludeSpeaking = false) +
                    " start_to_frame_observed_ms=" +
                    elapsed(frameObservedAt, excludeSpeaking = false) +
                    " frame_cache_read_ms=" +
                    cacheReadMs.get()
            )
        }
    }

    private companion object {
        fun nativeAudioTrack(output: AndroidPcmSpeechOutput): AudioTrack =
            AndroidPcmSpeechOutput::class.java.getDeclaredField("track").let { field ->
                field.isAccessible = true
                field.get(output) as AudioTrack
            }

        const val TAG = "VoiceLatency"
        // Hang guards only. Network latency is reported, never asserted against a five-second SLA.
        const val NETWORK_TIMEOUT_MS = 180_000L
        const val FIRST_AUDIO_TIMEOUT_MS = 90_000L
        const val VISUAL_CAPTURE_TIMEOUT_MS = 30_000L
        const val SIMULATED_SPEAKING_WINDOW_MS = 3_000L
        const val LATE_FRAME_OBSERVATION_MS = 30_000L
        const val PCM_TIMEOUT_MS = 10_000L
        const val CANCEL_TIMEOUT_MS = 10_000L
    }
}
