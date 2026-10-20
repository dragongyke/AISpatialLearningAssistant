package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.data.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCommandPolicyTest {
    @Test
    fun mapsStablePlaybackStatesToOnePlayerCommand() {
        assertEquals(PlaybackCommand.Prepare, PlaybackCommandPolicy.forState(PlaybackState.INIT))
        assertEquals(PlaybackCommand.Play, PlaybackCommandPolicy.forState(PlaybackState.READY))
        assertEquals(PlaybackCommand.Play, PlaybackCommandPolicy.forState(PlaybackState.COMPLETED))
        assertEquals(PlaybackCommand.Pause, PlaybackCommandPolicy.forState(PlaybackState.PLAYING))
        assertEquals(PlaybackCommand.Resume, PlaybackCommandPolicy.forState(PlaybackState.PAUSED))
        assertEquals(PlaybackCommand.Retry, PlaybackCommandPolicy.forState(PlaybackState.ERROR))
    }

    @Test
    fun ignoresClicksWhilePlayerIsPreparing() {
        assertEquals(PlaybackCommand.None, PlaybackCommandPolicy.forState(PlaybackState.PREPARING))
    }
}
