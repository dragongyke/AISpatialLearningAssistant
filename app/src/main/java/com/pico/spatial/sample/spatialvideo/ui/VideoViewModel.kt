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
package com.pico.spatial.sample.spatialvideo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.sample.spatialvideo.data.PlaybackManager
import com.pico.spatial.sample.spatialvideo.data.PlaybackManager.Companion.PLAYBACK_UNIT_TIME
import com.pico.spatial.sample.spatialvideo.data.PlaybackState
import com.pico.spatial.sample.spatialvideo.ecs.VideoEntityAssembler
import com.pico.spatial.sample.spatialvideo.ecs.VideoEntityConfig
import com.pico.spatial.sample.spatialvideo.learning.MediaTimelineSnapshot
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VideoViewModel : ViewModel() {
    private val _videoState = MutableStateFlow(PlaybackState.READY)
    val videoState: StateFlow<PlaybackState> = _videoState.asStateFlow()

    private val _playbackTime = MutableStateFlow(0L)
    val playbackTime: StateFlow<Long> = _playbackTime.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    private val _isSeeking = MutableStateFlow(false)
    private val _seekingPosition = MutableStateFlow(0f)

    val videoProgress: StateFlow<Float> =
        combine(_playbackTime, _isSeeking, _seekingPosition) {
                playbackTime,
                isSeekingProgress,
                seekingPosition ->
                if (isSeekingProgress) seekingPosition else playbackTime.toFloat()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0f
            )

    private var manager = PlaybackManager()
    private var initialized = false
    private var playbackPrepared = false
    private var pausedByLearningQuestion = false
    private val initializeMutex = Mutex()
    private var videoSource = DEFAULT_VIDEO_URL
    private var fallbackDurationMs = DEFAULT_VIDEO_DURATION_MS
    private var resumePositionMs = 0L
    val videoPanel = Entity()

    init {
        viewModelScope.launch {
            videoState.collectLatest { state ->
                if (state == PlaybackState.PLAYING) {
                    while (isActive) {
                        val currentTime = getCurrentTime()
                        _playbackTime.value = currentTime
                        delay(PLAYBACK_UNIT_TIME)

                        if (hasCompleted()) {
                            _videoState.value = PlaybackState.COMPLETED
                            break
                        }
                    }
                }
            }
        }
    }

    fun setVideoProgress(ms: Long, dragging: Boolean) {
        _playbackTime.update { ms }
        _seekingPosition.update { ms.toFloat() }
        _isSeeking.update { dragging }

        if (!dragging) {
            manager.seekTo(ms)
        }
    }

    suspend fun initialize(
        converter: PhysicalLengthConverter,
        @Suppress("UNUSED_PARAMETER") context: Context,
    ) {
        if (initialized) return
        initializeMutex.withLock {
            if (!initialized) {
                initializeInternal(converter)
            }
        }
    }

    private suspend fun initializeInternal(converter: PhysicalLengthConverter) {
        manager.setup(
            videoSource = videoSource,
            autoPlay = true,
            onStateChanged = {
                viewModelScope.launch {
                    _videoState.value = manager.state
                    _playbackError.value = manager.errorMessage
                    if (manager.state == PlaybackState.COMPLETED)
                        _playbackTime.value = getDuration()
                }
            },
        )
        manager.setStartPosition(resumePositionMs)
        manager.setPlaybackSpeed(_playbackSpeed.value)
        val panelWidth = converter.dpToLength(VideoEntityConfig.PANEL_WIDTH, LengthUnit.Meters)
        val panelHeight = converter.dpToLength(VideoEntityConfig.PANEL_HEIGHT, LengthUnit.Meters)
        VideoEntityAssembler.assembleVideoPanel(
            videoPanel,
            manager.player,
            panelWidth,
            panelHeight,
        )
        initialized = true
    }

    fun preparePlayback() {
        if (!initialized || playbackPrepared) return
        playbackPrepared = true
        manager.prepareAsync()
    }

    fun onPlayPauseClicked() {
        when (PlaybackCommandPolicy.forState(manager.state)) {
            PlaybackCommand.Prepare -> preparePlayback()
            PlaybackCommand.Play -> {
                manager.play()
            }
            PlaybackCommand.Pause -> {
                manager.pause()
            }
            PlaybackCommand.Resume -> {
                manager.resume()
            }
            PlaybackCommand.Retry -> retryPlayback()
            PlaybackCommand.None -> Unit
        }
        _videoState.value = manager.state
        _playbackError.value = manager.errorMessage
    }

    private fun getCurrentTime() = manager.updateCurrentTime()

    private fun hasCompleted() = manager.state == PlaybackState.COMPLETED

    fun getDuration() = manager.duration.takeIf { it > 1L } ?: fallbackDurationMs

    fun loadVideo(
        sourceUri: String,
        durationMs: Long,
        resumePositionMs: Long = 0L,
        playbackSpeed: Float = 1f,
    ) {
        reset()
        videoSource = sourceUri
        fallbackDurationMs = durationMs.coerceAtLeast(1L)
        this.resumePositionMs = resumePositionMs.coerceIn(0L, fallbackDurationMs)
        pausedByLearningQuestion = false
        _playbackTime.value = this.resumePositionMs
        _seekingPosition.value = this.resumePositionMs.toFloat()
        _isSeeking.value = false
        _playbackSpeed.value = playbackSpeed.coerceIn(0.5f, 2f)
        _playbackError.value = null
        _videoState.value = PlaybackState.INIT
    }

    fun setVolume(volume: Float) = manager.setVolume(volume)

    fun getVolume(): Float = manager.getVolume()

    fun setPlaybackSpeed(speed: Float) {
        val normalized = speed.coerceIn(0.5f, 2f)
        _playbackSpeed.value = normalized
        manager.setPlaybackSpeed(normalized)
    }

    fun getPlaybackSpeed(): Float = _playbackSpeed.value

    fun retryPlayback() {
        val source = videoSource
        val duration = fallbackDurationMs
        val position = _playbackTime.value
        val speed = _playbackSpeed.value
        loadVideo(source, duration, position, speed)
    }

    fun reset() {
        manager.reset()
        initialized = false
        playbackPrepared = false
        _playbackError.value = null
    }

    fun toggleFullSpace(isFullSpace: Boolean) {
        videoPanel.enabled = isFullSpace
    }

    fun pause() {
        if (manager.state == PlaybackState.PLAYING) {
            manager.pause()
        } else {
            _videoState.value = manager.state
        }
    }

    fun pauseForLearningQuestion(): Long {
        val currentTime = getCurrentTime().takeIf { it >= 0L } ?: _playbackTime.value
        _playbackTime.value = currentTime
        pausedByLearningQuestion =
            manager.state == PlaybackState.PLAYING || _videoState.value == PlaybackState.PLAYING
        if (pausedByLearningQuestion) {
            manager.pause()
            _videoState.value = manager.state
            _playbackError.value = manager.errorMessage
        } else {
            _videoState.value = manager.state
        }
        return currentTime
    }

    fun currentLearningTimestamp(): Long {
        val currentTime = getCurrentTime().takeIf { it >= 0L } ?: _playbackTime.value
        _playbackTime.value = currentTime
        return currentTime
    }

    fun mediaTimelineSnapshot(): MediaTimelineSnapshot =
        MediaTimelineSnapshot(
            positionMs = currentLearningTimestamp(),
            durationMs = getDuration(),
        )

    fun resumeAfterLearningAnswer() {
        if (manager.state == PlaybackState.COMPLETED) {
            manager.play()
            syncPlaybackAfterResume()
            pausedByLearningQuestion = false
            return
        }
        if (manager.state == PlaybackState.PAUSED || _videoState.value == PlaybackState.PAUSED) {
            if (manager.state == PlaybackState.PAUSED) {
                manager.resume()
            } else {
                manager.play()
            }
            syncPlaybackAfterResume()
        } else if (pausedByLearningQuestion && manager.state == PlaybackState.READY) {
            manager.play()
            syncPlaybackAfterResume()
        }
        pausedByLearningQuestion = false
    }

    private fun syncPlaybackAfterResume() {
        _videoState.value = manager.state
        _playbackError.value = manager.errorMessage
    }

    override fun onCleared() {
        super.onCleared()
        reset()
    }

    private companion object {
        const val DEFAULT_VIDEO_URL = "https://media.w3.org/2010/05/sintel/trailer.mp4"
        const val DEFAULT_VIDEO_DURATION_MS = 52_000L
    }
}
