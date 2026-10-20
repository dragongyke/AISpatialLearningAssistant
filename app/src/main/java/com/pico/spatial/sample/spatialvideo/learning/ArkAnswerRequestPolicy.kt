package com.pico.spatial.sample.spatialvideo.learning

import org.json.JSONArray
import org.json.JSONObject

data class CapturedVideoFrame(
    val timestampMs: Long,
    val jpegDataUrl: String,
)

internal object ArkAnswerRequestPolicy {
    const val MAX_FRAMES = 3
    const val MAX_FRAME_DATA_URL_CHARS = 256 * 1024
    const val MAX_TOTAL_DATA_URL_CHARS = 512 * 1024
    const val ANSWER_TIMEOUT_MS = 30_000L
    // Cold remote decoders need metadata/range requests; voice prefetch hides most of this wait.
    const val FRAME_CAPTURE_TIMEOUT_MS = 5_000L
    private const val JPEG_PREFIX = "data:image/jpeg;base64,"

    fun boundedFrames(frames: List<CapturedVideoFrame>): List<CapturedVideoFrame> {
        var remaining = MAX_TOTAL_DATA_URL_CHARS
        return frames
            .asSequence()
            .filter { frame ->
                val url = frame.jpegDataUrl
                val valid =
                    frame.timestampMs >= 0 &&
                        url.startsWith(JPEG_PREFIX) &&
                        url.length > JPEG_PREFIX.length &&
                        url.length <= minOf(MAX_FRAME_DATA_URL_CHARS, remaining)
                if (valid) remaining -= url.length
                valid
            }
            .take(MAX_FRAMES)
            .toList()
    }

    fun visualEvidence(visualContext: String?, frames: List<CapturedVideoFrame>): String? =
        if (frames.isEmpty()) visualContext
        else
            buildString {
                appendLine(
                    "Attached video frames, in order: ${frames.joinToString { formatTime(it.timestampMs) }}."
                )
                appendLine(
                    "Use these images as visual evidence alongside the transcript. State uncertainty if text is unreadable; do not invent details."
                )
                if (!visualContext.isNullOrBlank()) appendLine(visualContext)
            }

    fun requestBody(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        stream: Boolean,
        visualFrames: List<CapturedVideoFrame> = emptyList(),
        serviceTier: String = "fast",
    ): JSONObject {
        val frames = boundedFrames(visualFrames)
        val content: Any =
            if (frames.isEmpty()) userPrompt
            else
                JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", userPrompt))
                    frames.forEach { frame ->
                        put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", frame.jpegDataUrl))
                        )
                    }
                }
        return JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.45)
            .put("stream", stream)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", content))
            )
            .apply {
                if (stream) {
                    // Interactive answers opt out of default thinking; notes keep model defaults.
                    put("thinking", JSONObject().put("type", "disabled"))
                    // Ark can downgrade fast to default when low-latency quota is unavailable.
                    put("service_tier", serviceTier)
                }
            }
    }
}

internal data class ArkAnswerObservation(
    val elapsedMs: Long,
    val firstContentMs: Long?,
    val frameCount: Int,
    val actualServiceTier: String?,
    val outcome: String,
    val requestedServiceTier: String = "fast",
    val httpStatus: Int? = null,
    val errorCode: String? = null,
    val errorType: String? = null,
    val errorParameter: String? = null,
    val requestAttempts: Int = 1,
    val fastTierFallback: Boolean = false,
)
