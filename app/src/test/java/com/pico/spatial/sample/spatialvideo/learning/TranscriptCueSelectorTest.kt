package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptCueSelectorTest {
    private val first = segment(startMs = 0L, endMs = 1_000L, text = "First")
    private val second = segment(startMs = 1_000L, endMs = 2_000L, text = "Second")

    @Test
    fun selectsTheCueContainingTheCurrentPlaybackTime() {
        assertEquals(first, TranscriptCueSelector.current(listOf(first, second), 999L))
        assertEquals(second, TranscriptCueSelector.current(listOf(first, second), 1_000L))
    }

    @Test
    fun returnsNoCueOutsideTranscriptRanges() {
        assertNull(TranscriptCueSelector.current(listOf(first, second), 2_000L))
    }

    @Test
    fun delaysCuePresentationWithoutChangingMediaClock() {
        assertNull(
            TranscriptCueSelector.current(
                segments = listOf(second),
                playbackTimeMs = 2_200L,
                presentationDelayMs = 1_500L,
            )
        )
        assertEquals(second, TranscriptCueSelector.current(listOf(second), 2_500L, 1_500L))
    }

    @Test
    fun advancesCuePresentationWithANegativeOffset() {
        assertEquals(
            second,
            TranscriptCueSelector.current(
                segments = listOf(second),
                playbackTimeMs = 500L,
                presentationDelayMs = -500L,
            ),
        )
    }

    @Test
    fun labelsEstimatedTranscriptTiming() {
        assertEquals("Estimated transcript", TranscriptCueSelector.sourceLabel(first))
        assertEquals(
            "Transcript",
            TranscriptCueSelector.sourceLabel(first.copy(timingIsEstimated = false)),
        )
    }

    private fun segment(startMs: Long, endMs: Long, text: String) =
        TranscriptSegment(
            videoId = "video",
            startMs = startMs,
            endMs = endMs,
            text = text,
            summary = text,
            visualSummary = "",
            timingIsEstimated = true,
        )
}
