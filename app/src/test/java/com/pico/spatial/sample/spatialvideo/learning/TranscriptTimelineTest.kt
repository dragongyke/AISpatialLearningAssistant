package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTimelineTest {
    @Test
    fun resetsEstimatedTimingAtSemanticAnchorsAndProducesReadableCues() {
        val segments =
            TranscriptSegmentation.segmentAnchored(
                videoId = "course-1",
                durationMs = 20_000L,
                paragraphs =
                    listOf(
                        "Welcome to the course. This opening section explains what students will learn and how the class is organized.",
                        "Second topic begins here. This section introduces the main technical idea with a concrete example for learners.",
                    ),
                sourceUri = "https://example.com/transcript.html",
                anchors =
                    listOf(
                        TranscriptTimelineAnchor(0L, "Welcome to the course"),
                        TranscriptTimelineAnchor(10_000L, "Second topic begins here"),
                    ),
            )

        val secondTopic = segments.first { it.text.startsWith("Second topic") }
        assertEquals(10_000L, secondTopic.startMs)
        assertEquals(20_000L, segments.last().endMs)
        assertTrue(segments.all { it.text.length <= 96 })
        assertTrue(segments.all { it.timingIsEstimated })
    }
}
