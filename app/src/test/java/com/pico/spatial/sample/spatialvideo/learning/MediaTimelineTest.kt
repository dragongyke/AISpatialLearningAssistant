package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimelineTest {
    @Test
    fun signedSubtitleOffsetDoesNotModifyTheMediaPosition() {
        val timeline = MediaTimelineSnapshot(positionMs = 10_000L, durationMs = 30_000L)

        assertEquals(10_000L, timeline.positionMs)
        assertEquals(9_500L, timeline.subtitlePositionMs(offsetMs = 500L))
        assertEquals(10_500L, timeline.subtitlePositionMs(offsetMs = -500L))
    }

    @Test
    fun samplesFramesAroundTheSameClampedPlayerTimestamp() {
        val timeline = MediaTimelineSnapshot(positionMs = 1_000L, durationMs = 4_000L)

        assertEquals(listOf(0L, 1_000L, 3_000L), timeline.nearbyFrameTimestamps())
    }
}
