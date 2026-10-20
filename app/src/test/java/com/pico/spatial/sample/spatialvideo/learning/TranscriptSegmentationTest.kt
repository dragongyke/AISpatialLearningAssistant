package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptSegmentationTest {
    @Test
    fun splitsOfficialTranscriptAcrossTheLectureDuration() {
        val paragraphs =
            listOf(
                "Welcome to the course and the teaching staff.",
                "Programming methodology is about solving problems with programs.",
                "Karel provides a small world for learning program structure.",
            )

        val segments =
            TranscriptSegmentation.segment(
                videoId = "course-1",
                durationMs = 90_000L,
                paragraphs = paragraphs,
                sourceUri = "https://example.com/transcript.html",
            )

        assertEquals(3, segments.size)
        assertEquals(0L, segments.first().startMs)
        assertEquals(90_000L, segments.last().endMs)
        assertTrue(segments.all { it.timingIsEstimated })
        assertEquals(paragraphs.joinToString(" "), segments.joinToString(" ") { it.text })
    }

    @Test
    fun keepsEverySegmentInsideAOneMillisecondVideo() {
        val segments =
            TranscriptSegmentation.segment(
                videoId = "course-1",
                durationMs = 1L,
                paragraphs = listOf("a", "b", "c"),
                sourceUri = "https://example.com/transcript.html",
            )

        assertEquals(3, segments.size)
        segments.forEach { segment ->
            assertEquals(0L, segment.startMs)
            assertEquals(1L, segment.endMs)
        }
    }
}
