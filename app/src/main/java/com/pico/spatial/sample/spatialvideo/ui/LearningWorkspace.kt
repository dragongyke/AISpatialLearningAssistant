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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.data.PlaybackManager.Companion.PLAYBACK_VOLUME_RANGE
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.LearningChapter
import com.pico.spatial.sample.spatialvideo.learning.LearningQuestionAnswer
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.sample.spatialvideo.learning.LearningSummaryDocument
import com.pico.spatial.sample.spatialvideo.learning.LearningTeachingMode
import com.pico.spatial.sample.spatialvideo.learning.LearningUiState
import com.pico.spatial.sample.spatialvideo.learning.canSubmitDraftedQuestion
import com.pico.spatial.sample.spatialvideo.learning.formatTime
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.SymbolSlider
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.Material
import org.koin.androidx.compose.getKoin

@Composable
fun LearningWorkspace(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val videoViewModel: VideoViewModel = remember(scope) { scope.get<VideoViewModel>() }
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val playbackTime by videoViewModel.playbackTime.collectAsStateWithLifecycle()
    val videoProgress by videoViewModel.videoProgress.collectAsStateWithLifecycle()
    val askVoiceQuestion = rememberAskVoiceQuestionAction(learningViewModel)
    var volume by remember { mutableFloatStateOf(videoViewModel.getVolume()) }
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LearningOutlinePanel(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                state = learningState,
                playbackTime = playbackTime,
                onChapterClick = learningViewModel::seekToChapter,
                onQuestionClick = learningViewModel::seekToQuestion,
            )

            LearningCenterPanel(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                state = learningState,
                playbackTime = playbackTime,
                progress = videoProgress,
                duration = videoViewModel.getDuration(),
                volume = volume,
                currentChapter = learningViewModel.currentChapter(playbackTime),
                onVolumeChange = { volume = it },
                onVolumeChangeFinished = { videoViewModel.setVolume(volume) },
                onProgressChange = {
                    seekingPosition = it
                    videoViewModel.setVideoProgress(it.toLong(), dragging = true)
                },
                onProgressChangeFinished = {
                    videoViewModel.setVideoProgress(seekingPosition.toLong(), dragging = false)
                },
                onGenerateSummary = learningViewModel::generateSummary,
                onDismissSummary = learningViewModel::dismissSummary,
                videoViewModel = videoViewModel,
            )

            AiCoachPanel(
                modifier = Modifier.width(420.dp).fillMaxHeight(),
                state = learningState,
                onDraftChange = learningViewModel::updateDraftedQuestion,
                onAskDraft = learningViewModel::askDraftedQuestion,
                onAskVoice = askVoiceQuestion,
                onToggleAutoPause = learningViewModel::toggleAutoPauseOnQuestion,
                onResume = learningViewModel::resumePlayback,
                onQuestionClick = learningViewModel::selectQuestion,
                onTeachingModeChange = learningViewModel::selectTeachingMode,
            )
        }
    }
}

@Composable
fun LearningPlaybackToolbarPanel(
    modifier: Modifier = Modifier,
    layoutStatus: String? = null,
    environmentMode: LearningEnvironmentMode = LearningEnvironmentModePolicy.initial,
    onToggleEnvironment: () -> Unit = {},
    onExit: () -> Unit = {},
    onRestoreLayout: () -> Unit = {},
    onSaveLayout: () -> Unit = {},
) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val videoViewModel: VideoViewModel = remember(scope) { scope.get<VideoViewModel>() }
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current

    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val playbackTime by videoViewModel.playbackTime.collectAsStateWithLifecycle()
    val videoProgress by videoViewModel.videoProgress.collectAsStateWithLifecycle()
    val playbackError by videoViewModel.playbackError.collectAsStateWithLifecycle()
    var volume by remember { mutableFloatStateOf(videoViewModel.getVolume()) }
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentChapter = learningViewModel.currentChapter(playbackTime)

    Panel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onExit,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.10f)
                            ),
                    ) {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            painter = painterResource(R.drawable.ic_nav_back),
                            contentDescription = "Exit learning session",
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = learningState.video.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = PicoTheme.colorScheme.labelPrimaryLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (learningState.isCourseCompleted) "Course complete"
                                else "${formatTime(playbackTime)}  ${currentChapter.title}",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onToggleEnvironment,
                            size = ButtonDefaults.Small,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8C2332),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (environmentMode == LearningEnvironmentMode.VirtualHall)
                                            R.string.learning_view_reality
                                        else R.string.learning_return_lecture_hall
                                    )
                            )
                        }
                        Button(
                            onClick = onRestoreLayout,
                            size = ButtonDefaults.Small,
                        ) {
                            Text(text = stringResource(R.string.learning_restore_layout))
                        }
                        Button(
                            onClick = onSaveLayout,
                            size = ButtonDefaults.Small,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                                ),
                        ) {
                            Text(text = stringResource(R.string.learning_save_layout))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = learningViewModel::generateSummary,
                        enabled =
                            !learningState.isGeneratingSummary &&
                                !learningState.isListening &&
                                !learningState.isAnswering,
                        size = ButtonDefaults.Regular,
                        leadingIcon = {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(R.drawable.ic_summary_document),
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.learning_generate_summary))
                    }
                }

                layoutStatus?.let { message ->
                    Text(text = message, fontSize = 16.sp, color = Color.White.copy(alpha = 0.62f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text =
                            "Subtitle ${formatSignedOffset(learningState.subtitleOffsetMs)}" +
                                learningState.interactionLatency
                                    .compactLabel()
                                    .takeIf(String::isNotBlank)
                                    ?.let { "  ·  $it" }
                                    .orEmpty(),
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Button(
                        onClick = { learningViewModel.adjustSubtitleOffset(-500L) },
                        size = ButtonDefaults.Small,
                    ) {
                        Text("Earlier")
                    }
                    Button(
                        onClick = learningViewModel::resetSubtitleOffset,
                        size = ButtonDefaults.Small,
                    ) {
                        Text("Reset")
                    }
                    Button(
                        onClick = { learningViewModel.adjustSubtitleOffset(500L) },
                        size = ButtonDefaults.Small,
                    ) {
                        Text("Later")
                    }
                }
                playbackError?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = message,
                            fontSize = 16.sp,
                            color = Color(0xFFFFD166),
                        )
                        Button(
                            onClick = videoViewModel::retryPlayback,
                            size = ButtonDefaults.Small
                        ) {
                            Text(text = "Retry")
                        }
                    }
                }
                Text(
                    text =
                        stringResource(
                            R.string.learning_summary_folder_status,
                            learningState.summaryDirectoryLabel,
                        ),
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            PlaybackControlRow(
                progress = videoProgress,
                duration = videoViewModel.getDuration(),
                volume = volume,
                onVolumeChange = { volume = it },
                onVolumeChangeFinished = { videoViewModel.setVolume(volume) },
                onProgressChange = {
                    seekingPosition = it
                    videoViewModel.setVideoProgress(it.toLong(), dragging = true)
                },
                onProgressChangeFinished = {
                    videoViewModel.setVideoProgress(seekingPosition.toLong(), dragging = false)
                },
                videoViewModel = videoViewModel,
            )
        }
    }
}

@Composable
fun LearningOutlineSpacePanel(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val videoViewModel: VideoViewModel = remember(scope) { scope.get<VideoViewModel>() }
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val playbackTime by videoViewModel.playbackTime.collectAsStateWithLifecycle()

    LearningOutlinePanel(
        modifier = modifier.fillMaxSize(),
        state = learningState,
        playbackTime = playbackTime,
        onChapterClick = learningViewModel::seekToChapter,
        onQuestionClick = learningViewModel::seekToQuestion,
    )
}

@Composable
fun LearningAiCoachSpacePanel(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val askVoiceQuestion = rememberAskVoiceQuestionAction(learningViewModel)

    AiCoachPanel(
        modifier = modifier.fillMaxSize(),
        state = learningState,
        onDraftChange = learningViewModel::updateDraftedQuestion,
        onAskDraft = learningViewModel::askDraftedQuestion,
        onAskVoice = askVoiceQuestion,
        onToggleAutoPause = learningViewModel::toggleAutoPauseOnQuestion,
        onResume = learningViewModel::resumePlayback,
        onQuestionClick = learningViewModel::selectQuestion,
        onTeachingModeChange = learningViewModel::selectTeachingMode,
        showVoiceButton = false,
    )
}

@Composable
private fun LearningOutlinePanel(
    modifier: Modifier,
    state: LearningUiState,
    playbackTime: Long,
    onChapterClick: (LearningChapter) -> Unit,
    onQuestionClick: (String) -> Unit,
) {
    Panel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.learning_outline_title),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = PicoTheme.colorScheme.labelPrimaryLight
            )
            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.chapters.forEach { chapter ->
                    val active = playbackTime in chapter.startMs..chapter.endMs
                    OutlineChapterItem(
                        chapter = chapter,
                        active = active,
                        onClick = { onChapterClick(chapter) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.learning_question_markers),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = PicoTheme.colorScheme.labelPrimaryLight
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (state.questionAnswers.isEmpty()) {
                Text(
                    text = stringResource(R.string.learning_no_questions),
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    color = Color.White.copy(alpha = 0.62f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.questionAnswers.forEach { answer ->
                        QuestionMarkerItem(
                            answer = answer,
                            selected = answer.id == state.selectedQuestionId,
                            onClick = { onQuestionClick(answer.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningCenterPanel(
    modifier: Modifier,
    state: LearningUiState,
    playbackTime: Long,
    progress: Float,
    duration: Long,
    volume: Float,
    currentChapter: LearningChapter,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    onGenerateSummary: () -> Unit,
    onDismissSummary: () -> Unit,
    videoViewModel: VideoViewModel,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.learning_workspace_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = PicoTheme.colorScheme.labelPrimaryLight
                )
                Text(
                    text = stringResource(R.string.learning_workspace_subtitle),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.64f)
                )
            }
            Button(
                onClick = onGenerateSummary,
                enabled = !state.isGeneratingSummary,
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.ic_summary_document),
                        contentDescription = null,
                    )
                },
            ) {
                Text(text = stringResource(R.string.learning_generate_summary))
            }
        }

        Panel(modifier = Modifier.fillMaxWidth().height(350.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.learning_video_stage_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PicoTheme.colorScheme.labelPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.video.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.86f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.learning_video_stage_description),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.White.copy(alpha = 0.64f)
                    )
                }

                Column {
                    Text(
                        text = "${formatTime(playbackTime)}  ${currentChapter.title}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PicoTheme.colorScheme.labelPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProgressBar(progress = progress, duration = duration)
                }
            }
        }

        PlaybackControlStrip(
            progress = progress,
            duration = duration,
            volume = volume,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            onProgressChange = onProgressChange,
            onProgressChangeFinished = onProgressChangeFinished,
            videoViewModel = videoViewModel,
        )

        QuestionTimeline(
            modifier = Modifier.fillMaxWidth(),
            answers = state.questionAnswers,
            duration = duration,
        )

        val summary = state.summaryDocument
        if (summary != null) {
            SummaryDocumentPanel(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                summary = summary,
                onDismiss = onDismissSummary,
            )
        } else {
            SessionGuidancePanel(modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun PlaybackControlStrip(
    progress: Float,
    duration: Long,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    videoViewModel: VideoViewModel,
) {
    Panel(modifier = Modifier.fillMaxWidth().height(112.dp), contentPadding = 16.dp) {
        PlaybackControlRow(
            progress = progress,
            duration = duration,
            volume = volume,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            onProgressChange = onProgressChange,
            onProgressChangeFinished = onProgressChangeFinished,
            videoViewModel = videoViewModel,
        )
    }
}

@Composable
private fun PlaybackControlRow(
    progress: Float,
    duration: Long,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    videoViewModel: VideoViewModel,
    modifier: Modifier = Modifier.fillMaxWidth().height(88.dp),
) {
    val playbackSpeed by videoViewModel.playbackSpeed.collectAsStateWithLifecycle()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlayPauseButton(videoViewModel, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(formatTime(progress.toLong()), fontSize = 26.sp, color = Color.White)
        Spacer(modifier = Modifier.width(12.dp))
        Slider(
            modifier = Modifier.weight(1f).height(56.dp),
            value = progress,
            valueRange = 0f..duration.toFloat(),
            sliderSpec = SliderDefaults.Small,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(formatTime(duration), fontSize = 26.sp, color = Color.White)
        Spacer(modifier = Modifier.width(10.dp))
        PlaybackSpeedButton(
            speed = playbackSpeed,
            onSpeedChange = videoViewModel::setPlaybackSpeed,
        )
        Spacer(modifier = Modifier.width(16.dp))
        SymbolSlider(
            modifier = Modifier.size(176.dp, 48.dp),
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
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
        )
    }
}

@Composable
private fun PlaybackSpeedButton(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    Button(
        modifier = Modifier.width(112.dp).height(64.dp),
        onClick = {
            val currentIndex = PLAYBACK_SPEED_OPTIONS.indexOfFirst { it == speed }.coerceAtLeast(0)
            onSpeedChange(PLAYBACK_SPEED_OPTIONS[(currentIndex + 1) % PLAYBACK_SPEED_OPTIONS.size])
        },
        size = ButtonDefaults.Small,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
    ) {
        Text(text = "${speed}x", fontSize = 20.sp)
    }
}

@Composable
private fun AiCoachPanel(
    modifier: Modifier,
    state: LearningUiState,
    onDraftChange: (String) -> Unit,
    onAskDraft: () -> Unit,
    onAskVoice: () -> Unit,
    onToggleAutoPause: () -> Unit,
    onResume: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onTeachingModeChange: (LearningTeachingMode) -> Unit,
    showVoiceButton: Boolean = true,
) {
    Panel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.learning_ai_panel_title),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = PicoTheme.colorScheme.labelPrimaryLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    when {
                        state.isListening -> stringResource(R.string.learning_listening)
                        state.isAnalyzingVideo -> stringResource(R.string.learning_analyzing_video)
                        state.isAnswering || state.isPreparingSpeech ->
                            stringResource(R.string.learning_answering)
                        state.isSpeaking -> stringResource(R.string.learning_speaking)
                        else -> stringResource(R.string.learning_ready)
                    },
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.64f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.assistantStatusMessage,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color =
                    if (state.isAssistantConfigured) Color.White.copy(alpha = 0.56f)
                    else Color(0xFFFFD166),
            )
            Spacer(modifier = Modifier.height(14.dp))
            TeachingModeSelector(
                selected = state.teachingMode,
                onSelected = onTeachingModeChange,
            )
            state.interactionLatency.compactLabel().takeIf(String::isNotBlank)?.let { latency ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = latency,
                    fontSize = 17.sp,
                    color = Color(0xFFE7C27D),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.asrStatusMessage,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color =
                    if (state.isAsrReady) Color.White.copy(alpha = 0.56f) else Color(0xFFFFD166),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.visionStatusMessage,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color =
                    if (state.isVisionConfigured) Color.White.copy(alpha = 0.56f)
                    else Color(0xFFFFD166),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.ttsStatusMessage,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color =
                    if (state.isTtsConfigured) Color.White.copy(alpha = 0.56f)
                    else Color(0xFFFFD166),
            )

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                onClick = onToggleAutoPause,
                size = ButtonDefaults.Small,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (state.autoPauseOnQuestion) ACADEMIC_CARDINAL
                            else Color.White.copy(alpha = 0.12f),
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            if (state.autoPauseOnQuestion) R.string.learning_auto_pause_on
                            else R.string.learning_auto_pause_off
                        ),
                    fontSize = 18.sp,
                )
            }
            if (showVoiceButton) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    onClick = onAskVoice,
                    enabled = learningVoiceControlPolicy(state).isEnabled,
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(30.dp),
                            painter = painterResource(R.drawable.ic_voice_question),
                            contentDescription = null,
                        )
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ACADEMIC_CARDINAL,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.learning_voice_question),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = state.draftedQuestion,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().height(84.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.learning_question_placeholder),
                        fontSize = 20.sp
                    )
                },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = state.canSubmitDraftedQuestion(),
                onClick = onAskDraft,
                size = ButtonDefaults.Regular,
            ) {
                Text(
                    text = stringResource(R.string.learning_ask_draft),
                    fontSize = 20.sp,
                )
            }

            if (state.recognizedQuestion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "\"${state.recognizedQuestion}\"",
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    color = PicoTheme.colorScheme.labelPrimaryLight
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                onClick = onResume,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(28.dp),
                        painter = painterResource(R.drawable.ic_resume_video),
                        contentDescription = null,
                    )
                },
            ) {
                Text(
                    text = stringResource(R.string.learning_resume_video),
                    fontSize = 20.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.streamingAnswerText.isNotBlank()) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ACADEMIC_CARDINAL.copy(alpha = 0.26f))
                                .border(
                                    1.dp,
                                    ACADEMIC_BRASS.copy(alpha = 0.58f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(18.dp)
                    ) {
                        Text(
                            text = "AI 助教",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD99A),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.streamingAnswerText,
                            fontSize = 24.sp,
                            lineHeight = 34.sp,
                            color = PicoTheme.colorScheme.labelPrimaryLight,
                        )
                    }
                }
                if (state.questionAnswers.isEmpty() && state.streamingAnswerText.isBlank()) {
                    Text(
                        text = stringResource(R.string.learning_no_questions),
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                } else if (state.questionAnswers.isNotEmpty()) {
                    state.questionAnswers.asReversed().forEach { answer ->
                        AnswerBubble(
                            answer = answer,
                            selected = answer.id == state.selectedQuestionId,
                            onClick = { onQuestionClick(answer.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeachingModeSelector(
    selected: LearningTeachingMode,
    onSelected: (LearningTeachingMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LearningTeachingMode.entries.forEach { mode ->
            Button(
                modifier = Modifier.weight(1f).height(52.dp),
                onClick = { onSelected(mode) },
                size = ButtonDefaults.Small,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (mode == selected) ACADEMIC_CARDINAL
                            else Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White,
                    ),
            ) {
                Text(
                    text =
                        when (mode) {
                            LearningTeachingMode.Concise -> "Brief"
                            LearningTeachingMode.DeepDive -> "Deep"
                            LearningTeachingMode.Example -> "Example"
                            LearningTeachingMode.Socratic -> "Guide"
                        },
                    fontSize = 15.sp,
                )
            }
        }
    }
}

private fun formatSignedOffset(offsetMs: Long): String =
    when {
        offsetMs > 0L -> "+${"%.1f".format(offsetMs / 1_000f)}s"
        offsetMs < 0L -> "${"%.1f".format(offsetMs / 1_000f)}s"
        else -> "0.0s"
    }

@Composable
private fun OutlineChapterItem(
    chapter: LearningChapter,
    active: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (active) ACADEMIC_CARDINAL else Color.White.copy(alpha = 0.08f),
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        size = ButtonDefaults.Regular,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = formatTime(chapter.startMs), fontSize = 19.sp)
            Text(text = chapter.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuestionMarkerItem(
    answer: LearningQuestionAnswer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth().height(104.dp),
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) ACADEMIC_CARDINAL else Color.White.copy(alpha = 0.08f),
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        size = ButtonDefaults.Regular,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = formatTime(answer.timestampMs), fontSize = 18.sp)
            Text(text = answer.questionText, fontSize = 22.sp)
        }
    }
}

@Composable
private fun AnswerBubble(
    answer: LearningQuestionAnswer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (selected) ACADEMIC_WALNUT else Color.White.copy(alpha = 0.08f),
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        size = ButtonDefaults.buttonSize(minHeight = 220.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${formatTime(answer.timestampMs)}  ${answer.questionText}",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = answer.answerText,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                color = Color.White.copy(alpha = 0.76f)
            )
        }
    }
}

@Composable
private fun QuestionTimeline(
    modifier: Modifier,
    answers: List<LearningQuestionAnswer>,
    duration: Long,
) {
    Panel(modifier = modifier.height(84.dp), contentPadding = 16.dp) {
        Column {
            Text(
                text = stringResource(R.string.learning_question_markers),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PicoTheme.colorScheme.labelPrimaryLight
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.16f))
            ) {
                answers.forEach { answer ->
                    val fraction =
                        (answer.timestampMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(Color.Transparent)
                    )
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(Color.Transparent),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ACADEMIC_CARDINAL)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, duration: Long) {
    val fraction = (progress / duration.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.16f))
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ACADEMIC_CARDINAL)
        )
    }
}

@Composable
private fun SummaryDocumentPanel(
    modifier: Modifier,
    summary: LearningSummaryDocument,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isUserAccessible = summary.savedPath.startsWith("content://")
    Panel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.learning_summary_saved),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PicoTheme.colorScheme.labelPrimaryLight
                    )
                    Text(
                        text = summary.savedPath,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.54f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = isUserAccessible,
                        onClick = { openSummaryDocument(context, summary.savedPath) },
                        size = ButtonDefaults.Small,
                    ) {
                        Text(text = "Open")
                    }
                    Button(
                        enabled = isUserAccessible,
                        onClick = { shareSummaryDocument(context, summary.savedPath) },
                        size = ButtonDefaults.Small,
                    ) {
                        Text(text = "Share")
                    }
                    Button(onClick = onDismiss, size = ButtonDefaults.Small) {
                        Text(text = stringResource(R.string.learning_dismiss_summary))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                text = summary.markdown,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun SessionGuidancePanel(modifier: Modifier) {
    Panel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = "MVP flow",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PicoTheme.colorScheme.labelPrimaryLight
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text =
                        "1. Watch the video in the immersive stage.\n" +
                            "2. Ask a voice question to pause playback.\n" +
                            "3. Review the timestamped AI answer.\n" +
                            "4. Generate a Markdown learning summary.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
            Text(
                text =
                    "Doubao answers use the course transcript, learning history, and visual context when the question depends on the current frame.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(ACADEMIC_PANEL)
                .backgroundMaterial(true, Material.Thin)
                .border(1.dp, ACADEMIC_BRASS.copy(alpha = 0.68f), shape)
                .padding(contentPadding)
    ) {
        content()
    }
}

private fun openSummaryDocument(context: Context, savedPath: String) {
    if (!savedPath.startsWith("content://")) return
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.parse(savedPath), "text/markdown")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}

private fun shareSummaryDocument(context: Context, savedPath: String) {
    if (!savedPath.startsWith("content://")) return
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/markdown")
            .putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(savedPath))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Share learning summary")) }
}

@Composable
internal fun rememberAskVoiceQuestionAction(
    learningViewModel: LearningSessionViewModel,
): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                learningViewModel.askVoiceQuestion()
            } else {
                learningViewModel.onSpeechPermissionDenied()
            }
        }
    return remember(context, launcher, learningViewModel) {
        {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) {
                learningViewModel.askVoiceQuestion()
            } else {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

private val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private val ACADEMIC_PANEL = Color(0xF02A211F)
private val ACADEMIC_CARDINAL = Color(0xFF8C2332)
private val ACADEMIC_WALNUT = Color(0xFF3A2720)
private val ACADEMIC_BRASS = Color(0xFFB89150)
