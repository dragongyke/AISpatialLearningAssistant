package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.data.PlaybackState

enum class PlaybackCommand {
    Prepare,
    Play,
    Pause,
    Resume,
    Retry,
    None,
}

object PlaybackCommandPolicy {
    fun forState(state: PlaybackState): PlaybackCommand =
        when (state) {
            PlaybackState.INIT -> PlaybackCommand.Prepare
            PlaybackState.PREPARING -> PlaybackCommand.None
            PlaybackState.READY -> PlaybackCommand.Play
            PlaybackState.COMPLETED -> PlaybackCommand.Play
            PlaybackState.PLAYING -> PlaybackCommand.Pause
            PlaybackState.PAUSED -> PlaybackCommand.Resume
            PlaybackState.ERROR -> PlaybackCommand.Retry
        }
}
