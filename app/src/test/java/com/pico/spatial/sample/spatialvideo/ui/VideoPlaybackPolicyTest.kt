package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackPolicyTest {
    @Test
    fun remoteVideosUseSparseOnDemandSamplingWithoutFullPrefetch() {
        assertTrue(VideoPlaybackPolicy.isRemote("https://example.com/lecture.mp4"))
        assertEquals(5_000L, VideoPlaybackPolicy.sampleIntervalMs(isRemote = true))
        assertFalse(VideoPlaybackPolicy.shouldPrefetchEntireVideo(isRemote = true))
    }

    @Test
    fun localVideosMayUseDenserBoundedSampling() {
        assertFalse(VideoPlaybackPolicy.isRemote("file:///sdcard/lecture.mp4"))
        assertEquals(750L, VideoPlaybackPolicy.sampleIntervalMs(isRemote = false))
        assertTrue(VideoPlaybackPolicy.shouldPrefetchEntireVideo(isRemote = false))
    }
}
