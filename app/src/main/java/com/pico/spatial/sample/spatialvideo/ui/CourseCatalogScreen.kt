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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.sample.spatialvideo.CATALOG_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.HISTORY_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.HISTORY_WINDOW_TAG
import com.pico.spatial.sample.spatialvideo.NOTE_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.NOTE_WINDOW_TAG
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.STAGE_ID
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.CourseAvailability
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseFilter
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseVideo
import com.pico.spatial.sample.spatialvideo.learning.LearningHistoryRecord
import com.pico.spatial.sample.spatialvideo.learning.LearningNoteBlock
import com.pico.spatial.sample.spatialvideo.learning.LearningNoteMarkdownParser
import com.pico.spatial.sample.spatialvideo.learning.LearningNoteStatus
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.sample.spatialvideo.learning.formatTime
import com.pico.spatial.sample.spatialvideo.learning.searchableText
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.getKoin

@Composable
fun CourseCatalogScreen(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val spatialNavigator = LocalSpatialNavigator.current
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val videos =
        LearningCourseFilter.filter(LearningCourseCatalog.videos, selectedCategory, searchQuery)

    val windowCoordinator = rememberLearningWindowCoordinator()

    LaunchedEffect(Unit) { windowCoordinator.showHistory() }

    DisposableEffect(Unit) {
        onDispose {
            spatialNavigator.closeWindowContainer(
                HISTORY_WINDOW_CONTAINER_ID,
                HISTORY_WINDOW_TAG,
            )
            spatialNavigator.closeWindowContainer(
                NOTE_WINDOW_CONTAINER_ID,
                NOTE_WINDOW_TAG,
            )
        }
    }

    fun openCourseVideo(video: LearningCourseVideo) {
        learningViewModel.selectCourseVideo(video)
        coroutineScope.launch(Dispatchers.Main.immediate) { windowCoordinator.enterStage() }
    }

    CourseCatalogSurface(
        modifier = modifier.fillMaxSize(),
        selectedCategory = selectedCategory,
        searchQuery = searchQuery,
        videos = videos,
        availability = learningState.courseAvailability,
        onCategorySelected = { selectedCategory = it },
        onSearchQueryChange = { searchQuery = it },
        onStart = ::openCourseVideo,
        onRetryAvailability = learningViewModel::retryCourseAvailability,
        onOpenHistory = windowCoordinator::showHistory,
    )
}

@Composable
fun LearningHistoryWindow(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val windowCoordinator = rememberLearningWindowCoordinator()
    val spatialNavigator = LocalSpatialNavigator.current

    Box(modifier = modifier.fillMaxSize().background(CATALOG_BACKGROUND).padding(20.dp)) {
        LearningHistoryPanel(
            modifier = Modifier.fillMaxSize(),
            records = learningState.historyRecords,
            onContinue = { record ->
                LearningCourseCatalog.videos
                    .firstOrNull { it.id == record.videoId }
                    ?.let { video ->
                        learningViewModel.selectCourseVideo(video)
                        coroutineScope.launch(Dispatchers.Main.immediate) {
                            windowCoordinator.enterStage()
                        }
                    }
            },
            onOpenSummary = { record ->
                learningViewModel.openLearningNote(record)
                spatialNavigator.openWindowContainer(
                    NOTE_WINDOW_CONTAINER_ID,
                    NOTE_WINDOW_TAG,
                )
            },
        )
    }
}

@Composable
fun LearningNoteWindow(modifier: Modifier = Modifier) {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val state by learningViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val windowCoordinator = rememberLearningWindowCoordinator()
    val blocks =
        remember(state.notePreviewMarkdown, state.noteSearchQuery) {
            val query = state.noteSearchQuery.trim()
            LearningNoteMarkdownParser.parse(state.notePreviewMarkdown).filter { block ->
                query.isBlank() || block.searchableText.contains(query, ignoreCase = true)
            }
        }

    Box(modifier = modifier.fillMaxSize().background(CATALOG_BACKGROUND).padding(20.dp)) {
        CatalogPanel(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "LEARNING NOTE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ACADEMIC_CARDINAL,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.notePreviewTitle.ifBlank { "Select a course note" },
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = ACADEMIC_INK,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = noteStatusLabel(state.notePreviewStatus),
                    fontSize = 13.sp,
                    color =
                        if (state.notePreviewStatus == LearningNoteStatus.Failed) {
                            Color(0xFFB42318)
                        } else {
                            ACADEMIC_CARDINAL
                        },
                )
                Spacer(modifier = Modifier.height(18.dp))
                Box(modifier = Modifier.width(96.dp).height(3.dp).background(ACADEMIC_BRASS))
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = state.noteSearchQuery,
                        onValueChange = learningViewModel::updateNoteSearchQuery,
                        modifier = Modifier.weight(1f).height(48.dp),
                        placeholder = { Text(text = "Search note") },
                        singleLine = true,
                    )
                    if (state.isEditingNotePreview) {
                        Button(onClick = learningViewModel::saveEditedLearningNote) {
                            Text(text = "Save")
                        }
                        Button(onClick = learningViewModel::cancelEditingLearningNote) {
                            Text(text = "Cancel")
                        }
                    } else {
                        Button(
                            enabled = state.notePreviewMarkdown.isNotBlank(),
                            onClick = learningViewModel::beginEditingLearningNote,
                        ) {
                            Text(text = "Edit")
                        }
                        Button(
                            enabled = state.notePreviewMarkdown.isNotBlank(),
                            onClick = {
                                clipboard.setText(AnnotatedString(state.notePreviewMarkdown))
                                learningViewModel.onLearningNoteCopied()
                            },
                        ) {
                            Text(text = "Copy")
                        }
                        Button(
                            enabled = state.notePreviewVideoId != null,
                            onClick = learningViewModel::reorganizeOpenLearningNote,
                        ) {
                            Text(text = "Reorganize")
                        }
                    }
                }
                state.notePreviewStatusMessage.takeIf(String::isNotBlank)?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = message, fontSize = 12.sp, color = ACADEMIC_CARDINAL)
                }
                Spacer(modifier = Modifier.height(14.dp))

                when {
                    state.isLoadingNotePreview -> {
                        Text(
                            text = "Loading note...",
                            fontSize = 16.sp,
                            color = ACADEMIC_MUTED,
                        )
                    }
                    state.isEditingNotePreview -> {
                        TextField(
                            value = state.noteEditDraft,
                            onValueChange = learningViewModel::updateNoteEditDraft,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            placeholder = { Text(text = "Markdown note") },
                            singleLine = false,
                        )
                    }
                    blocks.isEmpty() -> {
                        Text(
                            text =
                                "The note will appear here while you study. It updates during the session and is reorganized by AI after you leave the Stage.",
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                            color = ACADEMIC_MUTED,
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            blocks.forEach { block ->
                                LearningNoteBlockView(
                                    block = block,
                                    onTimestampSelected = { timestampMs ->
                                        if (
                                            learningViewModel.openLearningNoteTimestamp(timestampMs)
                                        ) {
                                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                                windowCoordinator.enterStage()
                                            }
                                        }
                                    },
                                )
                            }
                            state.notePreviewPath?.let { path ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Markdown: $path",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ACADEMIC_MUTED,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningNoteBlockView(
    block: LearningNoteBlock,
    onTimestampSelected: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (block) {
            is LearningNoteBlock.Heading ->
                Text(
                    text = block.text,
                    fontSize =
                        when (block.level) {
                            1 -> 28.sp
                            2 -> 23.sp
                            else -> 19.sp
                        },
                    lineHeight =
                        when (block.level) {
                            1 -> 34.sp
                            2 -> 29.sp
                            else -> 25.sp
                        },
                    fontWeight = FontWeight.Bold,
                    color = if (block.level == 1) ACADEMIC_INK else ACADEMIC_CARDINAL,
                )
            is LearningNoteBlock.Bullet ->
                Text(
                    text = "- ${block.text}",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = ACADEMIC_INK,
                )
            is LearningNoteBlock.Numbered ->
                Text(
                    text = "${block.number}. ${block.text}",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = ACADEMIC_INK,
                )
            is LearningNoteBlock.Quote ->
                Text(
                    text = block.text,
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(ACADEMIC_BRASS.copy(alpha = 0.12f))
                            .padding(12.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = ACADEMIC_WALNUT,
                )
            is LearningNoteBlock.Paragraph ->
                Text(
                    text = block.text,
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = ACADEMIC_INK,
                )
        }
        block.timestampMs?.let { timestampMs ->
            Button(
                modifier = Modifier.height(36.dp),
                onClick = { onTimestampSelected(timestampMs) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ACADEMIC_BRASS.copy(alpha = 0.18f),
                        contentColor = ACADEMIC_WALNUT,
                    ),
            ) {
                Text(text = "Play ${formatTime(timestampMs)}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun rememberLearningWindowCoordinator(): LearningWindowCoordinator {
    val spatialNavigator = LocalSpatialNavigator.current
    return remember(spatialNavigator) {
        LearningWindowCoordinator(
            openStage = {
                runCatching { spatialNavigator.openStage(STAGE_ID, StageStyle.Mixed) }
                    .getOrNull() is OpenStageResult.Allowed
            },
            minimizeCatalog = {
                spatialNavigator.minimizeWindowContainer(
                    CATALOG_WINDOW_CONTAINER_ID,
                    null,
                )
            },
            minimizeHistory = {
                spatialNavigator.minimizeWindowContainer(
                    HISTORY_WINDOW_CONTAINER_ID,
                    HISTORY_WINDOW_TAG,
                )
            },
            closeNote = {
                spatialNavigator.closeWindowContainer(
                    NOTE_WINDOW_CONTAINER_ID,
                    NOTE_WINDOW_TAG,
                )
            },
            restoreCatalog = {
                spatialNavigator.restoreWindowContainer(CATALOG_WINDOW_CONTAINER_ID, null)
            },
            restoreHistory = {
                spatialNavigator.restoreWindowContainer(
                    HISTORY_WINDOW_CONTAINER_ID,
                    HISTORY_WINDOW_TAG,
                )
            },
            openHistory = {
                spatialNavigator.openWindowContainer(
                    id = HISTORY_WINDOW_CONTAINER_ID,
                    tag = HISTORY_WINDOW_TAG,
                )
            },
        )
    }
}

@Composable
private fun CourseCatalogSurface(
    modifier: Modifier,
    selectedCategory: String,
    searchQuery: String,
    videos: List<LearningCourseVideo>,
    availability: Map<String, CourseAvailability>,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStart: (LearningCourseVideo) -> Unit,
    onRetryAvailability: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Box(modifier = modifier.background(CATALOG_BACKGROUND).padding(28.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "THE LEARNING COMMONS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ACADEMIC_CARDINAL,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "AI Spatial Learning",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = ACADEMIC_INK,
                )
                Button(
                    onClick = onOpenHistory,
                    size = ButtonDefaults.Regular,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ACADEMIC_CARDINAL,
                            contentColor = Color.White,
                        ),
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_summary_document),
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(R.string.learning_open_history), fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose a Stanford Engineering Everywhere lecture to start learning.",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = ACADEMIC_MUTED,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.lecture_hall_attribution),
                fontSize = 11.sp,
                color = ACADEMIC_MUTED,
            )

            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.width(96.dp).height(3.dp).background(ACADEMIC_BRASS))

            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LearningCourseCatalog.categories.forEach { category ->
                        Button(
                            onClick = { onCategorySelected(category) },
                            size = ButtonDefaults.Regular,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        if (category == selectedCategory) ACADEMIC_CARDINAL
                                        else ACADEMIC_SURFACE,
                                    contentColor =
                                        if (category == selectedCategory) Color.White
                                        else ACADEMIC_INK,
                                ),
                        ) {
                            Text(text = category)
                        }
                    }
                }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.width(300.dp).height(52.dp),
                    placeholder = { Text(text = "Search courses") },
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardWidth =
                    CourseCarouselLayout.cardWidth(
                            viewportWidth = maxWidth.value,
                            itemSpacing = COURSE_CARD_SPACING.value,
                        )
                        .dp
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(COURSE_CARD_SPACING),
                ) {
                    items(items = videos, key = { it.id }) { video ->
                        CourseVideoCard(
                            modifier = Modifier.width(cardWidth).fillParentMaxHeight(),
                            video = video,
                            availability = availability[video.id] ?: CourseAvailability.Unknown,
                            onStart = { onStart(video) },
                            onRetryAvailability = { onRetryAvailability(video.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningHistoryPanel(
    modifier: Modifier,
    records: List<LearningHistoryRecord>,
    onContinue: (LearningHistoryRecord) -> Unit,
    onOpenSummary: (LearningHistoryRecord) -> Unit,
) {
    CatalogPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Learning history",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ACADEMIC_INK,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Watched videos and their matching Markdown notes.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ACADEMIC_MUTED,
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (records.isEmpty()) {
                Text(
                    text = "No learning history yet.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ACADEMIC_MUTED,
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    records.forEach { record ->
                        HistoryRecordItem(
                            record = record,
                            onContinue = { onContinue(record) },
                            onOpenSummary = onOpenSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordItem(
    record: LearningHistoryRecord,
    onContinue: () -> Unit,
    onOpenSummary: (LearningHistoryRecord) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ACADEMIC_CARDINAL.copy(alpha = 0.07f))
                .border(1.dp, ACADEMIC_BRASS.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                .padding(14.dp)
    ) {
        Text(
            text = record.videoTitle,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = ACADEMIC_INK,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text =
                "${record.category}  ${formatTime(record.progressMs)} / ${formatTime(record.durationMs)}",
            fontSize = 12.sp,
            color = ACADEMIC_CARDINAL,
        )
        Spacer(modifier = Modifier.height(6.dp))
        val progress =
            if (record.durationMs <= 0L) 0f
            else (record.progressMs.toFloat() / record.durationMs).coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ACADEMIC_WALNUT.copy(alpha = 0.12f))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(progress).height(5.dp).background(ACADEMIC_CARDINAL)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Last studied ${formatHistoryDate(record.lastWatchedAtMs)}",
            fontSize = 11.sp,
            color = ACADEMIC_MUTED,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = noteStatusLabel(record.noteStatus),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color =
                if (record.noteStatus == LearningNoteStatus.Failed) {
                    Color(0xFFB42318)
                } else {
                    ACADEMIC_MUTED
                },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            onClick = onContinue,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ACADEMIC_CARDINAL,
                    contentColor = Color.White,
                ),
        ) {
            Text(text = "Continue")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            onClick = { onOpenSummary(record) },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ACADEMIC_WALNUT.copy(alpha = 0.10f),
                    contentColor = ACADEMIC_WALNUT,
                ),
        ) {
            Text(text = "Learning note")
        }
    }
}

private fun noteStatusLabel(status: LearningNoteStatus): String =
    when (status) {
        LearningNoteStatus.NotStarted -> "Note starts updating after learning begins"
        LearningNoteStatus.Draft -> "Live note"
        LearningNoteStatus.Organizing -> "AI is organizing the final note"
        LearningNoteStatus.Ready -> "AI note ready"
        LearningNoteStatus.Failed -> "Final organization failed; live note is still available"
    }

@Composable
private fun CourseVideoCard(
    modifier: Modifier,
    video: LearningCourseVideo,
    availability: CourseAvailability,
    onStart: () -> Unit,
    onRetryAvailability: () -> Unit,
) {
    CatalogPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(courseThumbnail(video.id)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(138.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = video.category,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = COURSE_ACCENT,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = video.courseTitle,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ACADEMIC_INK,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.lectureTitle,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = ACADEMIC_WALNUT,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                text =
                    "${video.description}\n\nProvider: ${video.provider}\nDuration: ${formatTime(video.durationMs)}\nLicense: ${video.licenseNote}",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = ACADEMIC_MUTED,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = courseAvailabilityLabel(availability),
                fontSize = 12.sp,
                color =
                    if (availability == CourseAvailability.Unavailable) Color(0xFFB42318)
                    else ACADEMIC_MUTED,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = availability != CourseAvailability.Unavailable,
                onClick = onStart,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ACADEMIC_CARDINAL,
                        contentColor = Color.White,
                    ),
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_play),
                        contentDescription = null,
                    )
                },
            ) {
                Text(text = "Start learning")
            }
            if (availability == CourseAvailability.Unavailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    onClick = onRetryAvailability,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ACADEMIC_WALNUT.copy(alpha = 0.10f),
                            contentColor = ACADEMIC_WALNUT,
                        ),
                ) {
                    Text(text = "Retry source")
                }
            }
        }
    }
}

@Composable
private fun CatalogPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(ACADEMIC_SURFACE)
                .backgroundMaterial(true, Material.Thin)
                .border(1.dp, COURSE_PANEL_BORDER, shape)
                .padding(22.dp)
    ) {
        content()
    }
}

private val CATALOG_BACKGROUND = Color(0xFFE8ECEA)
private val ACADEMIC_SURFACE = Color(0xFFF8F9F7)
private val ACADEMIC_CARDINAL = Color(0xFF8C2332)
private val ACADEMIC_WALNUT = Color(0xFF30211C)
private val ACADEMIC_BRASS = Color(0xFFB89150)
private val ACADEMIC_INK = Color(0xFF201D1B)
private val ACADEMIC_MUTED = Color(0xFF665F59)
private val COURSE_ACCENT = ACADEMIC_CARDINAL
private val COURSE_PANEL_BORDER = ACADEMIC_BRASS.copy(alpha = 0.60f)
private val COURSE_CARD_SPACING = 18.dp

private fun courseAvailabilityLabel(status: CourseAvailability): String =
    when (status) {
        CourseAvailability.Unknown -> "Source not checked"
        CourseAvailability.Checking -> "Checking video source..."
        CourseAvailability.Available -> "Video source available"
        CourseAvailability.Unavailable -> "Video source unavailable"
    }

private fun formatHistoryDate(timestampMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))

private fun courseThumbnail(videoId: String): Int =
    when (videoId) {
        "stanford-cs106a-lecture01" -> R.drawable.course_cs106a
        "stanford-cs229-lecture01" -> R.drawable.course_cs229
        "stanford-ee364a-lecture01" -> R.drawable.course_ee364a
        "stanford-cs107-lecture01" -> R.drawable.course_cs107
        "stanford-ee263-lecture01" -> R.drawable.course_ee263
        else -> R.drawable.course_cs106a
    }
