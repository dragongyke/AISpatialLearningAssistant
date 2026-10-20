package com.pico.spatial.sample.spatialvideo.learning

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal data class VideoFrameCaptureRequest(
    val videoId: String,
    val sourceUri: String,
    val sessionId: Long,
    val timestampMs: Long,
) {
    fun matches(other: VideoFrameCaptureRequest): Boolean =
        videoId == other.videoId &&
            sourceUri == other.sourceUri &&
            sessionId == other.sessionId &&
            timestampMs >= 0 &&
            other.timestampMs >= 0 &&
            kotlin.math.abs(timestampMs - other.timestampMs) <= 1_000
}

internal class BoundedVideoFrameCaptureWorker(
    private val executor: Executor,
    private val onObservation: (VideoFrameCaptureObservation) -> Unit = {},
    private val captureFrame: (VideoFrameCaptureRequest, (CapturedVideoFrame) -> Unit) -> Unit,
) {
    private data class Capture(
        val request: VideoFrameCaptureRequest,
        val result: CompletableDeferred<List<CapturedVideoFrame>> = CompletableDeferred(),
        val startedAtNanos: Long = System.nanoTime(),
    )

    private var inFlight: Capture? = null
    private var cached: Pair<VideoFrameCaptureRequest, CapturedVideoFrame>? = null

    fun prefetch(request: VideoFrameCaptureRequest): Boolean = start(request) != null

    suspend fun capture(
        request: VideoFrameCaptureRequest,
        timeoutMs: Long = ArkAnswerRequestPolicy.FRAME_CAPTURE_TIMEOUT_MS,
    ): List<CapturedVideoFrame> {
        currentCoroutineContext().ensureActive()
        val result = start(request) ?: return emptyList()
        // The decoder is deliberately not a child job: native work can ignore cancellation.
        // Only waiting is cancelled; a late frame can still populate the bounded cache.
        return withTimeoutOrNull(timeoutMs) { result.await() }.orEmpty()
    }

    @Synchronized
    private fun start(
        request: VideoFrameCaptureRequest
    ): CompletableDeferred<List<CapturedVideoFrame>>? {
        cached
            ?.takeIf { it.first.matches(request) }
            ?.let {
                return CompletableDeferred(listOf(it.second))
            }
        inFlight?.let {
            return if (it.request.matches(request)) it.result else null
        }
        val capture = Capture(request)
        inFlight = capture
        try {
            executor.execute {
                var hasFrame = false
                fun observe(phase: String) {
                    runCatching {
                        onObservation(
                            VideoFrameCaptureObservation(
                                phase,
                                TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - capture.startedAtNanos
                                ),
                                hasFrame,
                            )
                        )
                    }
                }
                try {
                    captureFrame(request) { frame ->
                        val frames = ArkAnswerRequestPolicy.boundedFrames(listOf(frame))
                        if (frames.isNotEmpty()) {
                            synchronized(this) { cached = request to frames.single() }
                            hasFrame = true
                            observe("frame_ready")
                            // Publish before retriever cleanup, which may also block in native
                            // code.
                            capture.result.complete(frames)
                        }
                    }
                } catch (_: Exception) {
                    // Missing frames are an honest text-only fallback, not a failed answer.
                } finally {
                    synchronized(this) { inFlight = null }
                    capture.result.complete(emptyList())
                    observe("finished")
                }
            }
        } catch (_: RejectedExecutionException) {
            inFlight = null
            capture.result.complete(emptyList())
        }
        return capture.result
    }
}

internal data class VideoFrameCaptureObservation(
    val phase: String,
    val elapsedMs: Long,
    val hasFrame: Boolean,
)
