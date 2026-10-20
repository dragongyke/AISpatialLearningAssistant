package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArkAnswerRequestPolicyTest {
    @Test
    fun limitsFrameCountWithoutReorderingTimestamps() {
        val frames = (1L..10L).map { CapturedVideoFrame(it, "data:image/jpeg;base64,/9j/2Q==") }
        assertEquals(
            listOf(1L, 2L, 3L),
            ArkAnswerRequestPolicy.boundedFrames(frames).map { it.timestampMs }
        )
    }

    @Test
    fun rejectsInvalidAndOversizedFramesBeforeApplyingAggregateLimit() {
        val large = "data:image/jpeg;base64," + "A".repeat(200_000)
        val frames =
            listOf(
                CapturedVideoFrame(0, "data:image/jpeg;base64,"),
                CapturedVideoFrame(1, "data:image/jpeg;base64," + "A".repeat(300_000)),
                CapturedVideoFrame(2, "https://example.invalid/frame.jpg"),
                CapturedVideoFrame(3, large),
                CapturedVideoFrame(4, large),
                CapturedVideoFrame(5, large),
            )
        val bounded = ArkAnswerRequestPolicy.boundedFrames(frames)
        assertEquals(listOf(3L, 4L), bounded.map { it.timestampMs })
        assertTrue(bounded.sumOf { it.jpegDataUrl.length } <= 512 * 1024)
    }

    @Test
    fun serviceTierParsingOnlyReturnsDocumentedValues() {
        assertEquals(
            "default",
            ArkStreamingResponseParser.extractServiceTier("data: {\"service_tier\":\"default\"}")
        )
        assertEquals(
            "fast",
            ArkStreamingResponseParser.extractServiceTier("data: {\"service_tier\":\"fast\"}")
        )
        assertEquals(
            null,
            ArkStreamingResponseParser.extractServiceTier(
                "data: {\"service_tier\":\"secret user content\"}"
            )
        )
        assertEquals(null, ArkStreamingResponseParser.extractServiceTier("data: [DONE]"))
    }
}
