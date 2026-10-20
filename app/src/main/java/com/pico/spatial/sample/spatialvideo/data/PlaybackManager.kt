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
package com.pico.spatial.sample.spatialvideo.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.ecs.video.CypressMediaPlayer
import com.pico.spatial.core.ecs.video.CypressMediaPlayerCallback
import com.pico.spatial.core.ecs.video.CypressMediaPlayerErrorCode

class PlaybackManager {
    var player = CypressMediaPlayer()
    var state by mutableStateOf(PlaybackState.INIT)
    var duration by mutableLongStateOf(1L)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var autoPlayOnPrepared = false
    private var pendingSeekMs = 0L
    private var playbackSpeed = 1f
    private var onStateChanged: () -> Unit = {}

    private val callBack =
        object : CypressMediaPlayerCallback {
            override fun onPrepared() {
                state = PlaybackState.READY
                errorMessage = null
                duration = player.getDuration().takeIf { it > 0 } ?: 1L
                player.setPlaybackSpeed(playbackSpeed)
                if (pendingSeekMs > 0L) {
                    player.seekTo(pendingSeekMs.coerceAtMost(duration))
                }
                if (autoPlayOnPrepared) {
                    updateStateIfSuccessful(player.play(), PlaybackState.PLAYING, "play")
                }
                onStateChanged()
                Log.i(PLAYBACK_TAG, "onPrepared Event triggered, duration: $duration")
            }

            override fun onStarted() {
                state = PlaybackState.PLAYING
                onStateChanged()
                Log.i(PLAYBACK_TAG, "onStarted Event triggered")
            }

            override fun onPaused() {
                state = PlaybackState.PAUSED
                onStateChanged()
                Log.i(PLAYBACK_TAG, "onPaused Event triggered")
            }

            override fun onStopped() {
                state = PlaybackState.READY
                onStateChanged()
                Log.i(PLAYBACK_TAG, "onStopped Event triggered")
            }

            override fun onCompleted() {
                state = PlaybackState.COMPLETED
                onStateChanged()
                Log.i(PLAYBACK_TAG, "onCompleted Event triggered, current state: $state")
            }

            override fun onSeekToCompleted() {
                Log.i(PLAYBACK_TAG, "onSeekToCompleted Event triggered, current state: $state")
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                Log.i(PLAYBACK_TAG, "onVideoSizeChanged triggered, width: $width, height: $height")
            }

            override fun onError(error: CypressMediaPlayerErrorCode) {
                state = PlaybackState.ERROR
                errorMessage = "Video playback failed: $error"
                onStateChanged()
                Log.e(PLAYBACK_TAG, "Playback error: $error")
            }
        }

    fun setup(videoSource: String, autoPlay: Boolean, onStateChanged: () -> Unit) {
        this.autoPlayOnPrepared = autoPlay
        this.onStateChanged = onStateChanged
        player.registerCypressMediaPlayerCallback(callBack)
        player.setDataSource(videoSource)
        player.setLoop(false)
        player.setVolume(PLAYBACK_INIT_VOLUME)
    }

    fun prepareAsync() {
        state = PlaybackState.PREPARING
        errorMessage = null
        onStateChanged()
        player.prepareAsync()
    }

    fun updateCurrentTime() =
        if (state == PlaybackState.COMPLETED) duration else player.getCurrentPosition()

    fun play() {
        if (state == PlaybackState.COMPLETED) player.seekTo(0L)
        updateStateIfSuccessful(player.play(), PlaybackState.PLAYING, "play")
        onStateChanged()
    }

    fun pause() {
        updateStateIfSuccessful(player.pause(), PlaybackState.PAUSED, "pause")
        onStateChanged()
    }

    fun resume() {
        updateStateIfSuccessful(player.resume(), PlaybackState.PLAYING, "resume")
        onStateChanged()
    }

    private fun updateStateIfSuccessful(
        successful: Boolean,
        targetState: PlaybackState,
        operation: String,
    ) {
        if (successful) {
            state = targetState
            errorMessage = null
        } else {
            errorMessage = "Video player rejected $operation. Please retry."
            Log.e(PLAYBACK_TAG, "$operation returned false in state $state")
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        if (state == PlaybackState.COMPLETED && ms < duration) {
            state = PlaybackState.READY
            onStateChanged()
        }
    }

    fun setVolume(volume: Float) {
        player.setVolume(volume)
    }

    fun getVolume(): Float {
        return player.getVolume()
    }

    fun setStartPosition(positionMs: Long) {
        pendingSeekMs = positionMs.coerceAtLeast(0L)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        if (state != PlaybackState.INIT && state != PlaybackState.PREPARING) {
            player.setPlaybackSpeed(playbackSpeed)
        }
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun reset() {
        player.apply {
            unregisterCypressMediaPlayerCallback()
            close()
        }
        player = CypressMediaPlayer()
        state = PlaybackState.INIT
        duration = 1L
        errorMessage = null
        autoPlayOnPrepared = false
        pendingSeekMs = 0L
        onStateChanged = {}
    }

    companion object {
        private const val PLAYBACK_TAG = "PlaybackManager"
        // Playback settings
        const val PLAYBACK_UNIT_TIME = 50L // in ms
        const val PLAYBACK_INIT_VOLUME = 0.5f
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 2.0f
        val PLAYBACK_VOLUME_RANGE = 0.0f..1.0f
    }
}
