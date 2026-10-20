/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.pico.spatial.sample.spatialvideo.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal data class VisualContextResult(
    val summary: String?,
    val statusMessage: String,
    val isLive: Boolean,
)

internal data class VisualFrameCaptureResult(
    val frames: List<CapturedVideoFrame>,
    val statusMessage: String,
)

internal interface VideoVisualContextAnalyzer {
    val isConfigured: Boolean
    val readinessMessage: String

    fun prefetchFrames(timestampMs: Long, state: LearningUiState) {}

    suspend fun captureFrames(
        timestampMs: Long,
        state: LearningUiState,
    ): VisualFrameCaptureResult =
        VisualFrameCaptureResult(
            emptyList(),
            "Video frames are unavailable; the answer will use transcript context only.",
        )

    suspend fun analyze(
        question: String,
        timestampMs: Long,
        state: LearningUiState,
    ): VisualContextResult
}

internal data class ArkVisionConfiguration(
    val apiKey: String = BuildConfig.ARK_API_KEY,
    val model: String = BuildConfig.ARK_VISION_MODEL,
    val baseUrl: String = BuildConfig.ARK_BASE_URL,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

internal object VideoFrameWindow {
    fun timestamps(currentMs: Long, durationMs: Long): List<Long> =
        MediaTimelineSnapshot(currentMs, durationMs).nearbyFrameTimestamps()
}

internal class DoubaoVideoVisualContextAnalyzer(
    private val context: Context,
    private val configuration: ArkVisionConfiguration = ArkVisionConfiguration(),
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build(),
) : VideoVisualContextAnalyzer {
    private val frameWorker = sharedFrameWorker(context)

    override val isConfigured: Boolean
        get() = configuration.isConfigured

    override val readinessMessage: String
        get() =
            if (isConfigured) {
                "Doubao video understanding is ready."
            } else {
                "Doubao vision is not configured; answers will use transcript context only."
            }

    override suspend fun analyze(
        question: String,
        timestampMs: Long,
        state: LearningUiState,
    ): VisualContextResult {
        if (!isConfigured) {
            return VisualContextResult(null, readinessMessage, isLive = false)
        }
        val capture = captureFrames(timestampMs, state)
        if (capture.frames.isEmpty()) {
            return VisualContextResult(null, capture.statusMessage, isLive = false)
        }
        return requestVisualSummary(question, timestampMs, state, capture.frames)
    }

    override suspend fun captureFrames(
        timestampMs: Long,
        state: LearningUiState,
    ): VisualFrameCaptureResult {
        currentCoroutineContext().ensureActive()
        val startedAt = System.nanoTime()
        val bounded = frameWorker.capture(frameRequest(timestampMs, state))
        runCatching {
            Log.i(
                "ArkCapture",
                "wait_ms=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)} frames=${bounded.size}"
            )
        }
        return VisualFrameCaptureResult(
            frames = bounded,
            statusMessage =
                if (bounded.isEmpty()) {
                    "Video frames could not be sampled; the answer will use transcript context only."
                } else {
                    "Captured ${bounded.size} nearby video frame(s) for the answer."
                },
        )
    }

    override fun prefetchFrames(timestampMs: Long, state: LearningUiState) {
        frameWorker.prefetch(frameRequest(timestampMs, state))
    }

    private fun frameRequest(timestampMs: Long, state: LearningUiState) =
        VideoFrameCaptureRequest(
            videoId = state.video.id,
            sourceUri = state.video.sourceUri,
            sessionId = state.sessionId,
            timestampMs = timestampMs.coerceIn(0L, state.video.durationMs.coerceAtLeast(0L)),
        )

    private suspend fun requestVisualSummary(
        question: String,
        timestampMs: Long,
        state: LearningUiState,
        frames: List<CapturedVideoFrame>,
    ): VisualContextResult =
        withContext(Dispatchers.IO) {
            try {
                val content =
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "text")
                                .put(
                                    "text",
                                    buildVisualPrompt(question, timestampMs, state, frames),
                                )
                        )
                frames.forEach { frame ->
                    content.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", frame.jpegDataUrl),
                            )
                    )
                }
                val body =
                    JSONObject()
                        .put("model", configuration.model)
                        .put("max_tokens", 700)
                        .put("stream", false)
                        .put(
                            "messages",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("role", "system")
                                        .put(
                                            "content",
                                            "You analyze nearby frames from an educational video. Report only visible evidence, distinguish uncertainty, preserve equations and code exactly, and answer in the learner's language.",
                                        )
                                )
                                .put(JSONObject().put("role", "user").put("content", content)),
                        )
                val request =
                    Request.Builder()
                        .url("${configuration.baseUrl.trimEnd('/')}/chat/completions")
                        .header("Authorization", "Bearer ${configuration.apiKey}")
                        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                executeVisionRequest(request).use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    check(response.isSuccessful) { "Ark vision HTTP ${response.code}" }
                    val summary =
                        JSONObject(responseBody)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                            ?.takeIf(String::isNotBlank)
                            ?: error("Ark vision returned an empty response.")
                    VisualContextResult(
                        summary = summary,
                        statusMessage = "Doubao analyzed ${frames.size} nearby video frame(s).",
                        isLive = true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                VisualContextResult(
                    summary = null,
                    statusMessage =
                        "Doubao vision was unavailable (${error.safeVisualMessage()}); transcript context was retained.",
                    isLive = false,
                )
            }
        }

    private suspend fun executeVisionRequest(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response)
                        else response.close()
                    }
                }
            )
        }

    private fun buildVisualPrompt(
        question: String,
        timestampMs: Long,
        state: LearningUiState,
        frames: List<CapturedVideoFrame>,
    ): String = buildString {
        appendLine("Video: ${state.video.title}")
        appendLine("Current timestamp: ${formatTime(timestampMs)}")
        appendLine("Learner question: ${question}")
        appendLine(
            "Attached frames, in order: ${frames.joinToString { formatTime(it.timestampMs) }}."
        )
        appendLine("Nearby transcript:")
        val contextSegments = LearningAssistantText.findContext(timestampMs, state)
        if (contextSegments.isEmpty()) {
            appendLine("- No transcript is available around this timestamp.")
        } else {
            contextSegments.take(8).forEach { segment ->
                appendLine(
                    "- ${formatTime(segment.startMs)}-${formatTime(segment.endMs)}: ${segment.text}"
                )
            }
        }
        appendLine("Describe what the frames add beyond the transcript.")
        appendLine("Focus on blackboard writing, slides, equations, code, diagrams, and gestures.")
        appendLine(
            "If text is unreadable or frames do not support an answer, state that explicitly."
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private var captureWorker: BoundedVideoFrameCaptureWorker? = null

        @Synchronized
        fun sharedFrameWorker(context: Context): BoundedVideoFrameCaptureWorker =
            captureWorker
                ?: run {
                    val provider = VideoFrameContextProvider(context.applicationContext)
                    val executor =
                        ThreadPoolExecutor(
                                1,
                                1,
                                30,
                                TimeUnit.SECONDS,
                                SynchronousQueue<Runnable>(),
                                { task ->
                                    Thread(task, "ArkFrameCapture").apply { isDaemon = true }
                                },
                            )
                            .apply { allowCoreThreadTimeOut(true) }
                    BoundedVideoFrameCaptureWorker(
                            executor,
                            onObservation = { Log.i("ArkCapture", it.toString()) }
                        ) { request, publish ->
                            provider.captureCurrentFrame(
                                request.sourceUri,
                                request.timestampMs,
                                publish
                            )
                        }
                        .also { captureWorker = it }
                }
    }
}

internal class VideoFrameContextProvider(private val context: Context) {
    fun captureCurrentFrame(
        sourceUri: String,
        timestampMs: Long,
        onFrame: (CapturedVideoFrame) -> Unit,
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            if (sourceUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(sourceUri))
            } else {
                retriever.setDataSource(sourceUri, emptyMap<String, String>())
            }
            retriever.loadFrame(timestampMs)?.let { bitmap ->
                try {
                    bitmap.toJpegDataUrl()?.let { dataUrl ->
                        onFrame(
                            CapturedVideoFrame(timestampMs = timestampMs, jpegDataUrl = dataUrl)
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.loadFrame(timestampMs: Long): Bitmap? {
        val timeUs = timestampMs.coerceIn(0L, Long.MAX_VALUE / 1_000L) * 1_000L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                FRAME_WIDTH,
                FRAME_HEIGHT,
            )
        } else {
            @Suppress("DEPRECATION")
            getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { source ->
                val scaled = Bitmap.createScaledBitmap(source, FRAME_WIDTH, FRAME_HEIGHT, true)
                if (scaled !== source) source.recycle()
                scaled
            }
        }
    }

    private fun Bitmap.toJpegDataUrl(): String? {
        val bytes =
            ByteArrayOutputStream().use { stream ->
                if (!compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) return null
                stream.toByteArray()
            }
        val encodedLength =
            ((bytes.size.toLong() + 2L) / 3L) * 4L + "data:image/jpeg;base64,".length
        if (encodedLength > ArkAnswerRequestPolicy.MAX_FRAME_DATA_URL_CHARS) return null
        return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private companion object {
        const val FRAME_WIDTH = 640
        const val FRAME_HEIGHT = 360
        const val JPEG_QUALITY = 72
    }
}

private fun Throwable.safeVisualMessage(): String =
    if (this is IOException) "network error" else "frame analysis failed"
