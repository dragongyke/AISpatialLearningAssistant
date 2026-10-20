package com.pico.spatial.sample.spatialvideo.learning

import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/** One text/audio stream per answer: synthesis can run ahead of playback. */
internal class DoubaoBidirectionalSpeechAnswerSpeaker(
    private val configuration: DoubaoStreamingTtsConfiguration = DoubaoStreamingTtsConfiguration(),
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build(),
    private val endpoint: String = "wss://openspeech.bytedance.com/api/v3/tts/bidirection",
    private val outputFactory: (() -> Unit) -> PcmSpeechOutput = { AndroidPcmSpeechOutput(it) },
) : SpeechAnswerSpeaker {
    private val activePlayback = ActiveSpeechPlayback()
    override val isConfigured
        get() = configuration.isConfigured

    override val readinessMessage
        get() =
            if (isConfigured) "Doubao continuous voice reply is ready."
            else "Doubao TTS is not configured; replies remain text-only."

    override suspend fun speak(text: String, onPlaybackStarted: () -> Unit): SpeechPlaybackResult {
        val input = Channel<String>(1)
        input.send(text)
        input.close()
        var result = SpeechPlaybackResult("Nothing to speak.", false)
        speakStream(input, { true }, {}, onPlaybackStarted) { result = it }
        return result
    }

    override suspend fun speakStream(
        text: ReceiveChannel<String>,
        isCurrent: () -> Boolean,
        onFirstText: () -> Unit,
        onPlaybackStarted: () -> Unit,
        onResult: (SpeechPlaybackResult) -> Unit,
    ) {
        if (!isConfigured) {
            onResult(SpeechPlaybackResult(readinessMessage, false))
            return
        }
        var audible = false
        try {
            withContext(Dispatchers.IO) {
                withTimeout(180_000L) {
                    stream(text, isCurrent, onFirstText) {
                        if (!audible && isCurrent()) {
                            audible = true
                            onPlaybackStarted()
                        }
                    }
                }
            }
            if (isCurrent())
                onResult(SpeechPlaybackResult("Doubao finished speaking the AI answer.", audible))
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            if (isCurrent())
                onResult(
                    SpeechPlaybackResult(
                        "Voice connection timed out. The answer remains available as text.",
                        audible
                    )
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            runCatching {
                Log.w(
                    "DoubaoTts",
                    "failure=${error.javaClass.simpleName} at=${error.stackTrace.firstOrNull()}"
                )
            }
            // Never replay an already audible answer through a fallback provider.
            if (isCurrent())
                onResult(
                    SpeechPlaybackResult(
                        "Voice connection failed. The complete answer remains available as text.",
                        audible
                    )
                )
        }
    }

    private suspend fun stream(
        text: ReceiveChannel<String>,
        isCurrent: () -> Boolean,
        onFirstText: () -> Unit,
        onPlaybackStarted: () -> Unit,
    ) = coroutineScope {
        val startedAt = System.nanoTime()
        fun observe(phase: String) {
            runCatching {
                Log.i(
                    "DoubaoTts",
                    "phase=$phase elapsed_ms=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)}"
                )
            }
        }
        val opened = CompletableDeferred<Unit>()
        val closing = AtomicBoolean(false)
        val frames = Channel<DoubaoTtsProtocol.Frame>(Channel.UNLIMITED)
        val queuedBytes = AtomicLong()
        val sessionId = UUID.randomUUID().toString()
        val output = outputFactory {
            observe("first_signal_playing")
            onPlaybackStarted()
        }
        val request =
            Request.Builder()
                .url(endpoint)
                .header("X-Api-App-Key", configuration.appId)
                .header("X-Api-Access-Key", configuration.accessToken)
                .header("X-Api-Resource-Id", configuration.resourceId)
                .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                .build()
        val socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            require(bytes.size <= 2 * 1024 * 1024) { "TTS packet too large." }
                            val frame = DoubaoTtsProtocol.decode(bytes.toByteArray())
                            if (
                                queuedBytes.addAndGet(frame.payload.size.toLong()) > 8 * 1024 * 1024
                            ) {
                                throw IOException("TTS playback backlog exceeded limit.")
                            }
                            frames.trySend(frame)
                        } catch (_: Exception) {
                            frames.close(IOException("Invalid TTS stream."))
                            webSocket.cancel()
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        if (!closing.get())
                            runCatching {
                                Log.w(
                                    "DoubaoTts",
                                    "transport_failure=${t.javaClass.simpleName} http=${response?.code ?: 0}"
                                )
                            }
                        val failure =
                            IOException("TTS connection failed (HTTP ${response?.code ?: 0}).")
                        opened.completeExceptionally(failure)
                        frames.close(failure)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        frames.close(IOException("TTS connection closed before completion."))
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                        frames.close(IOException("TTS connection is closing."))
                    }
                }
            )
        val job = currentCoroutineContext()[Job]!!
        val stopAction: () -> Unit = {
            closing.set(true)
            job.cancel(CancellationException("Voice reply interrupted."))
            socket.cancel()
            output.stop()
        }
        activePlayback.register(stopAction)
        fun send(event: Int, json: String = "{}") {
            check(socket.send(DoubaoTtsProtocol.encode(event, sessionId, json).toByteString())) {
                "TTS socket is not writable."
            }
        }
        suspend fun receive(): DoubaoTtsProtocol.Frame {
            while (true) {
                val frame = withTimeout(30_000L) { frames.receive() }
                queuedBytes.addAndGet(-frame.payload.size.toLong())
                check(frame.errorCode == null && frame.event !in listOf(51, 153)) {
                    "TTS server rejected the session."
                }
                if (frame.sessionId == null || frame.sessionId == sessionId) return frame
            }
        }
        try {
            withTimeout(8_000L) { opened.await() }
            send(1)
            check(receive().event == 50) { "TTS connection was not acknowledged." }
            send(100, payload(100, configuration.voiceType))
            check(receive().event == 150) { "TTS session was not acknowledged." }
            observe("session_ready")
            val sender = launch {
                var first = true
                for (part in text) {
                    ensureActive()
                    if (!isCurrent()) throw CancellationException("Stale voice reply.")
                    val plain = SpeechTextSanitizer.forPlayback(part)
                    if (plain.isBlank()) continue
                    if (first) {
                        first = false
                        observe("first_text")
                        onFirstText()
                    }
                    send(200, payload(200, configuration.voiceType, "$plain "))
                }
                send(102)
            }
            sender.invokeOnCompletion { failure ->
                if (failure is CancellationException) job.cancel(failure)
            }
            var firstPacket = true
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) throw CancellationException("Stale voice reply.")
                val frame = receive()
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) throw CancellationException("Stale voice reply.")
                if (frame.isAudio && frame.payload.isNotEmpty()) {
                    if (firstPacket) {
                        observe("first_pcm_packet")
                        firstPacket = false
                    }
                    output.write(frame.payload)
                }
                if (frame.event == 152) break
            }
            sender.join()
            if (!isCurrent()) throw CancellationException("Stale voice reply.")
            output.finish()
            observe("drained")
            send(2)
            socket.close(1000, "Complete")
        } finally {
            closing.set(true)
            activePlayback.clear(stopAction)
            socket.cancel()
            frames.cancel()
            output.close()
        }
    }

    override fun stop() = activePlayback.stop()

    private fun payload(event: Int, voice: String, text: String? = null): String =
        JSONObject()
            .put("user", JSONObject().put("uid", "spatial-learner"))
            .put("namespace", "BidirectionalTTS")
            .put("event", event)
            .put(
                "req_params",
                JSONObject()
                    .put("speaker", voice)
                    .put(
                        "audio_params",
                        JSONObject().put("format", "pcm").put("sample_rate", 24000)
                    )
                    .apply {
                        configuration.lowLatencyModel?.let { put("model", it) }
                        if (text != null) put("text", text)
                    }
            )
            .toString()
}
