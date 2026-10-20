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
package com.pico.spatial.sample.spatialvideo.learning

data class LearningVideo(
    val id: String,
    val title: String,
    val sourceUri: String,
    val durationMs: Long,
    val transcriptStatus: ProcessingStatus,
    val summaryStatus: ProcessingStatus,
    val provider: String = "",
    val category: String = "",
    val sourcePageUri: String = "",
    val licenseNote: String = "",
)

enum class ProcessingStatus {
    Pending,
    Ready,
}

data class LearningChapter(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val summary: String,
)

data class TranscriptSegment(
    val videoId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val summary: String,
    val visualSummary: String,
    val sourceUri: String = "",
    val timingIsEstimated: Boolean = false,
)

data class LearningQuestionAnswer(
    val id: String,
    val timestampMs: Long,
    val questionText: String,
    val answerText: String,
    val contextStartMs: Long,
    val contextEndMs: Long,
    val createdAtMs: Long,
)

enum class AssistantResponseMode {
    Live,
    Fallback,
    Error,
}

data class LearningAssistantAnswer(
    val questionAnswer: LearningQuestionAnswer,
    val mode: AssistantResponseMode,
    val statusMessage: String,
)

data class LearningAssistantSummary(
    val markdown: String,
    val mode: AssistantResponseMode,
    val statusMessage: String,
)

data class LearningSummaryDocument(
    val markdown: String,
    val savedPath: String,
    val createdAtMs: Long,
)

enum class LearningNoteStatus {
    NotStarted,
    Draft,
    Organizing,
    Ready,
    Failed,
}

enum class LearningTeachingMode {
    Concise,
    DeepDive,
    Example,
    Socratic,
}

enum class LearningLanguage {
    Chinese,
    English,
}

enum class CourseAvailability {
    Unknown,
    Checking,
    Available,
    Unavailable,
}

data class LearningHistoryRecord(
    val videoId: String,
    val videoTitle: String,
    val category: String,
    val lastWatchedAtMs: Long,
    val progressMs: Long,
    val durationMs: Long,
    val summaryPath: String?,
    val noteStatus: LearningNoteStatus =
        if (summaryPath.isNullOrBlank()) LearningNoteStatus.NotStarted
        else LearningNoteStatus.Ready,
    val summaryUpdatedAtMs: Long = 0L,
)

data class LearningUiState(
    val sessionId: Long = 0L,
    val isCourseCompleted: Boolean = false,
    val video: LearningVideo = SampleLearningContent.video,
    val chapters: List<LearningChapter> = SampleLearningContent.chapters,
    val transcriptSegments: List<TranscriptSegment> = SampleLearningContent.transcriptSegments,
    val historyRecords: List<LearningHistoryRecord> = emptyList(),
    val questionAnswers: List<LearningQuestionAnswer> = emptyList(),
    val selectedQuestionId: String? = null,
    val recognizedQuestion: String = "",
    val draftedQuestion: String = "",
    val isListening: Boolean = false,
    val isAnswering: Boolean = false,
    val isAnalyzingVideo: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPreparingSpeech: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val streamingAnswerText: String = "",
    val assistantStatusMessage: String = "",
    val asrStatusMessage: String = "",
    val summaryDirectoryLabel: String = "",
    val visionStatusMessage: String = "",
    val ttsStatusMessage: String = "",
    val autoPauseOnQuestion: Boolean = true,
    val isAssistantConfigured: Boolean = false,
    val isAsrReady: Boolean = false,
    val isAsrInstalling: Boolean = false,
    val summaryDocument: LearningSummaryDocument? = null,
    val notePreviewTitle: String = "",
    val notePreviewVideoId: String? = null,
    val notePreviewMarkdown: String = "",
    val notePreviewPath: String? = null,
    val notePreviewStatus: LearningNoteStatus = LearningNoteStatus.NotStarted,
    val isLoadingNotePreview: Boolean = false,
    val noteSearchQuery: String = "",
    val isEditingNotePreview: Boolean = false,
    val noteEditDraft: String = "",
    val noteEditOriginal: String = "",
    val notePreviewStatusMessage: String = "",
    val nextSuggestedQuestionIndex: Int = 0,
    val isVisionConfigured: Boolean = false,
    val isTtsConfigured: Boolean = false,
    val teachingMode: LearningTeachingMode = LearningTeachingMode.Concise,
    val subtitleOffsetMs: Long = 0L,
    val interactionLatency: LearningInteractionLatency = LearningInteractionLatency(),
    val courseAvailability: Map<String, CourseAvailability> = emptyMap(),
)
