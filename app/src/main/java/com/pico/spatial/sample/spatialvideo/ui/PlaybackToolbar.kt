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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.sample.spatialvideo.CATALOG_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.CATALOG_WINDOW_TAG
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.STAGE_ID
import com.pico.spatial.sample.spatialvideo.data.PlaybackManager.Companion.PLAYBACK_VOLUME_RANGE
import com.pico.spatial.sample.spatialvideo.data.PlaybackState
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.SymbolSlider
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.foundation.vibrant.Vibrant
import com.pico.spatial.ui.foundation.vibrant.withVibrant
import com.pico.spatial.ui.graphics.Vibrant
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.closeStage
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.getKoin

@Composable
fun PlaybackToolbar() {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val videoViewModel: VideoViewModel = remember(scope) { scope.get<VideoViewModel>() }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val spatialNavigator = LocalSpatialNavigator.current
    var volume by remember { mutableFloatStateOf(videoViewModel.getVolume()) }
    val duration = videoViewModel.getDuration()

    val videoProgress by videoViewModel.videoProgress.collectAsStateWithLifecycle()
    var seekingPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(videoViewModel, converter, context) {
        videoViewModel.initialize(converter, context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_DESTROY) {
                videoViewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            coroutineScope.launch(Dispatchers.Main.immediate) {
                closeStage()
                videoViewModel.toggleFullSpace(false)
                spatialNavigator.openWindowContainer(
                    CATALOG_WINDOW_CONTAINER_ID,
                    tag = CATALOG_WINDOW_TAG
                )
            }
        }
    }

    Toolbar(cornerSize = 55.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause button
            PlayPauseButton(videoViewModel)
            Spacer(modifier = Modifier.size(8.dp))

            // Current time text
            TimeText(videoProgress)
            Spacer(modifier = Modifier.size(16.dp))

            // Playback time slider
            Slider(
                modifier = Modifier.size(504.dp, 32.dp),
                value = videoProgress,
                valueRange = 0f..duration.toFloat(),
                sliderSpec = SliderDefaults.Small,
                onValueChange = {
                    seekingPosition = it
                    videoViewModel.setVideoProgress(seekingPosition.toLong(), true)
                },
                onValueChangeFinished = {
                    videoViewModel.setVideoProgress(seekingPosition.toLong(), false)
                }
            )
            Spacer(modifier = Modifier.size(16.dp))

            // Duration text
            TimeText(duration.toFloat())
            Spacer(modifier = Modifier.size(16.dp))

            // Volume slider
            SymbolSlider(
                modifier = Modifier.size(204.dp, 48.dp),
                icon = {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        painter = painterResource(id = R.drawable.ic_volume_control),
                        contentDescription = stringResource(R.string.volume_control)
                    )
                },
                value = volume,
                valueRange = PLAYBACK_VOLUME_RANGE,
                sliderSpec = SliderDefaults.Max,
                onValueChange = { volume = it },
                onValueChangeFinished = { videoViewModel.setVolume(volume) },
            )
            Spacer(modifier = Modifier.size(16.dp))

            // Immersive toggle
            ImmersiveButton(videoViewModel)
        }
    }
}

@Composable
fun PlayPauseButton(viewModel: VideoViewModel, modifier: Modifier = Modifier) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()

    IconButton(
        modifier = modifier.semantics { contentDescription = "play_pause_button" },
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = PicoTheme.colorScheme.fillPrimary
            ),
        onClick = { viewModel.onPlayPauseClicked() }
    ) {
        val iconRes =
            when (videoState) {
                PlaybackState.READY,
                PlaybackState.PAUSED -> R.drawable.ic_play
                PlaybackState.PLAYING -> R.drawable.ic_pause
                else -> R.drawable.ic_play
            }
        Icon(
            modifier = Modifier.size(48.dp),
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.play_pause_video)
        )
    }
}

@Composable
fun ImmersiveButton(viewModel: VideoViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var isFullSpace by remember { mutableStateOf(true) }
    val spatialNavigator = LocalSpatialNavigator.current

    IconButton(
        modifier = Modifier.semantics { contentDescription = "immersive_button" },
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
        onClick = {
            coroutineScope.launch(Dispatchers.Main.immediate) {
                if (!isFullSpace) {
                    spatialNavigator.openStage(STAGE_ID, StageStyle.Full)
                    viewModel.toggleFullSpace(true)
                } else {
                    viewModel.pause()
                    closeStage()
                    viewModel.toggleFullSpace(false)
                    spatialNavigator.closeWindowContainer()
                }
                isFullSpace = !isFullSpace
            }
        }
    ) {
        Image(
            modifier = Modifier.size(48.dp),
            painter = painterResource(R.drawable.ic_immersive_mode),
            contentDescription = stringResource(R.string.enter_exit_immersive_mode)
        )
    }
}

@Composable
fun TimeText(time: Float) {
    Row {
        Text(
            text = formatTime(time.roundToLong()),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Vibrant.withVibrant(Vibrant.Darker)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000f).roundToInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
