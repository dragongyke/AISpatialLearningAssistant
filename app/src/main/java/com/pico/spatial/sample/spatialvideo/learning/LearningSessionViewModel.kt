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

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pico.spatial.sample.spatialvideo.data.PlaybackState
import com.pico.spatial.sample.spatialvideo.ui.VideoViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearningSessionViewModel(
    private val videoViewModel: VideoViewModel,
    private val context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val assistant: LearningAssistantEngine = DoubaoLearningAssistantEngine()
    private val summaryStore = LearningSummaryStore(appContext)
    private val historyStore = LearningHistoryStore(appContext)
    private val sessionStore = LearningSessionStore(appContext)
    private val transcriptRepository = TranscriptRepository(appContext)
    private val courseAvailabilityChecker = LearningCourseAvailabilityChecker()
    private val offlineSpeechQuestionTranscriber = VoskSpeechQuestionTranscriber(appContext)
    private val speechQuestionTranscriber: SpeechQuestionTranscriber =
        PreferredSpeechQuestionTranscriber(
            online = DoubaoSpeechQuestionTranscriber(appContext),
            offline = offlineSpeechQuestionTranscriber,
        )
    private val visualContextAnalyzer: VideoVisualContextAnalyzer =
        DoubaoVideoVisualContextAnalyzer(appContext)
    private val answerSpeaker: SpeechAnswerSpeaker =
        PreferredSpeechAnswerSpeaker(
            streaming = DoubaoBidirectionalSpeechAnswerSpeaker(),
            fallback = DoubaoSpeechAnswerSpeaker(appContext),
        )
    private var interactionJob: Job? = null
    private var speechJob: Job? = null
    private val interactionTracker = LearningInteractionTracker()
    private val latencyTracker = LearningInteractionLatencyTracker(SystemClock::elapsedRealtime)
    private val noteWriteCoordinator = LearningNoteWriteCoordinator()
    private val summaryTaskTracker = LearningSummaryTaskTracker()
    private var liveNoteJob: Job? = null
    private val summaryJobs = mutableMapOf<String, Job>()
    private val availabilityJobs = mutableMapOf<String, Job>()
    private var lastLiveNoteVideoId: String? = null
    private var lastLiveNoteProgressMs = 0L
    private var lastLiveNoteQuestionCount = 0
    private val sessionLifecycle = LearningSessionLifecycle()
    private val sessionVersion
        get() = sessionLifecycle.version

    private val sessionActive
        get() = sessionLifecycle.isActive

    private val _uiState =
        MutableStateFlow(
            createLearningState(
                LearningCourseCatalog.defaultVideo,
                sessionStore.load(LearningCourseCatalog.defaultVideo.id),
            )
        )
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    asrStatusMessage = speechQuestionTranscriber.readinessMessage,
                    isAsrInstalling = !speechQuestionTranscriber.isReady,
                )
            }
            val installResult = speechQuestionTranscriber.prepare()
            _uiState.update {
                it.copy(
                    asrStatusMessage =
                        installResult.fold(
                            onSuccess = { speechQuestionTranscriber.readinessMessage },
                            onFailure = { error ->
                                "Speech model setup failed: ${error.safeStatusMessage()}"
                            },
                        ),
                    isAsrReady = installResult.isSuccess,
                    isAsrInstalling = false,
                )
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                persistCurrentSession()
                updateLiveNote()
            }
        }
        LearningCourseCatalog.videos.forEach { retryCourseAvailability(it.id) }
        viewModelScope.launch {
            videoViewModel.videoState.collect { playback ->
                if (playback == PlaybackState.COMPLETED && sessionLifecycle.complete()) {
                    val progress = videoViewModel.getDuration()
                    persistCurrentSession(progressMs = progress)
                    _uiState.update { it.copy(isCourseCompleted = true) }
                    startFinalSummary(
                        _uiState.value,
                        progress,
                        showDocument = true,
                        deduplicate = true
                    )
                } else if (playback == PlaybackState.PLAYING && sessionActive) {
                    sessionLifecycle.resume()
                    _uiState.update { it.copy(isCourseCompleted = false) }
                }
            }
        }
    }

    fun updateDraftedQuestion(question: String) {
        _uiState.update { it.copy(draftedQuestion = question) }
    }

    fun toggleAutoPauseOnQuestion() {
        _uiState.update { it.copy(autoPauseOnQuestion = !it.autoPauseOnQuestion) }
    }

    fun selectTeachingMode(mode: LearningTeachingMode) {
        _uiState.update { it.copy(teachingMode = mode) }
        persistCurrentSession()
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        _uiState.update {
            it.copy(
                subtitleOffsetMs =
                    (it.subtitleOffsetMs + deltaMs).coerceIn(
                        MIN_SUBTITLE_OFFSET_MS,
                        MAX_SUBTITLE_OFFSET_MS,
                    )
            )
        }
        persistCurrentSession()
    }

    fun resetSubtitleOffset() {
        _uiState.update { it.copy(subtitleOffsetMs = 0L) }
        persistCurrentSession()
    }

    fun retryCourseAvailability(videoId: String) {
        val video = LearningCourseCatalog.videos.firstOrNull { it.id == videoId } ?: return
        availabilityJobs[videoId]?.cancel()
        _uiState.update {
            it.copy(
                courseAvailability =
                    it.courseAvailability + (videoId to CourseAvailability.Checking)
            )
        }
        availabilityJobs[videoId] =
            viewModelScope.launch {
                val status = courseAvailabilityChecker.check(video.sourceUri)
                _uiState.update {
                    it.copy(courseAvailability = it.courseAvailability + (videoId to status))
                }
            }
    }

    fun onSpeechPermissionDenied() {
        _uiState.update {
            it.copy(asrStatusMessage = "Microphone permission is required for voice questions.")
        }
    }

    fun askSuggestedVoiceQuestion() {
        askVoiceQuestion()
    }

    fun askVoiceQuestion() {
        if (_uiState.value.canBargeIn()) {
            interactionTracker.invalidate()
            interactionJob?.cancel()
            stopSpokenAnswer()
            _uiState.update {
                it.copy(
                    isAnswering = false,
                    isAnalyzingVideo = false,
                    isSpeaking = false,
                    streamingAnswerText = "",
                )
            }
        }
        val current = _uiState.value
        if (current.isBusy()) return
        if (!current.isAsrReady) {
            _uiState.update {
                it.copy(
                    asrStatusMessage = speechQuestionTranscriber.readinessMessage,
                )
            }
            return
        }
        val interactionId = interactionTracker.begin()
        latencyTracker.begin()
        interactionJob?.cancel()
        val timestampMs = beginQuestionTimestamp()
        if (visualContextAnalyzer.isConfigured) {
            visualContextAnalyzer.prefetchFrames(timestampMs, current)
        }
        val version = sessionVersion
        val videoId = current.video.id
        _uiState.update {
            it.copy(
                isListening = true,
                recognizedQuestion = "",
                summaryDocument = null,
                asrStatusMessage = "Listening...",
                interactionLatency = latencyTracker.snapshot(),
            )
        }
        interactionJob =
            viewModelScope.launch {
                try {
                    val result =
                        speechQuestionTranscriber.transcribeOnce { partial ->
                            if (isCurrentInteraction(interactionId, version, videoId)) {
                                latencyTracker.markAsrPartial()
                                _uiState.update {
                                    it.copy(
                                        recognizedQuestion = partial,
                                        asrStatusMessage = "Recognizing...",
                                        interactionLatency = latencyTracker.snapshot(),
                                    )
                                }
                            }
                        }
                    if (!isCurrentInteraction(interactionId, version, videoId)) return@launch
                    latencyTracker.markAsrFinal()
                    val question = result.text.trim()
                    if (question.isBlank()) {
                        _uiState.update {
                            it.copy(
                                recognizedQuestion = "",
                                asrStatusMessage = result.statusMessage,
                            )
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            recognizedQuestion = question,
                            asrStatusMessage = result.statusMessage,
                            interactionLatency = latencyTracker.snapshot(),
                        )
                    }
                    if (LearningAssistantText.isResumeIntent(question)) {
                        resumePlayback()
                    } else {
                        performAnswer(
                            question = question,
                            timestampMs = timestampMs,
                            advanceSuggestedQuestion = false,
                            version = version,
                            videoId = videoId,
                            interactionId = interactionId,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (isCurrentInteraction(interactionId, version, videoId)) {
                        _uiState.update {
                            it.copy(asrStatusMessage = "ASR failed: ${error.safeStatusMessage()}")
                        }
                    }
                } finally {
                    if (isCurrentInteraction(interactionId, version, videoId)) {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isAnswering = false,
                                isAnalyzingVideo = false,
                            )
                        }
                    }
                }
            }
    }

    fun askDraftedQuestion() {
        if (!_uiState.value.canSubmitDraftedQuestion()) return
        val question = _uiState.value.draftedQuestion.trim()
        if (question.isNotEmpty()) {
            if (LearningAssistantText.isResumeIntent(question)) {
                _uiState.update { it.copy(draftedQuestion = "", recognizedQuestion = question) }
                resumePlayback()
            } else {
                askQuestion(question, advanceSuggestedQuestion = false)
            }
        }
    }

    fun askQuestion(question: String, advanceSuggestedQuestion: Boolean) {
        val current = _uiState.value
        if (current.canBargeIn()) {
            interactionTracker.invalidate()
            interactionJob?.cancel()
            stopSpokenAnswer()
            _uiState.update {
                it.copy(
                    isAnswering = false,
                    isAnalyzingVideo = false,
                    isSpeaking = false,
                    streamingAnswerText = "",
                )
            }
        }
        if (_uiState.value.isBusy()) return
        val interactionId = interactionTracker.begin()
        latencyTracker.begin()
        val timestampMs = beginQuestionTimestamp()
        val version = sessionVersion
        val videoId = current.video.id
        interactionJob?.cancel()
        interactionJob =
            viewModelScope.launch {
                try {
                    performAnswer(
                        question = question,
                        timestampMs = timestampMs,
                        advanceSuggestedQuestion = advanceSuggestedQuestion,
                        version = version,
                        videoId = videoId,
                        interactionId = interactionId,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (isCurrentInteraction(interactionId, version, videoId)) {
                        _uiState.update {
                            it.copy(
                                assistantStatusMessage =
                                    "Question failed: ${error.safeStatusMessage()}",
                            )
                        }
                    }
                } finally {
                    if (isCurrentInteraction(interactionId, version, videoId)) {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isAnswering = false,
                                isAnalyzingVideo = false,
                            )
                        }
                    }
                }
            }
    }

    private suspend fun performAnswer(
        question: String,
        timestampMs: Long,
        advanceSuggestedQuestion: Boolean,
        version: Long,
        videoId: String,
        interactionId: Long,
    ) {
        val learningState = _uiState.value
        if (!isCurrentInteraction(interactionId, version, videoId)) return
        latencyTracker.beginResponse()
        val needsVisualContext =
            visualContextAnalyzer.isConfigured &&
                VisualQuestionPolicy.requiresVisualContext(question)
        _uiState.update {
            it.copy(
                recognizedQuestion = question,
                draftedQuestion = "",
                isListening = false,
                isAnswering = true,
                isAnalyzingVideo = needsVisualContext,
                streamingAnswerText = "",
                visionStatusMessage =
                    if (needsVisualContext) {
                        "Analyzing nearby video frames..."
                    } else {
                        "This question can be answered from the course context without visual analysis."
                    },
                summaryDocument = null,
                nextSuggestedQuestionIndex =
                    if (advanceSuggestedQuestion) it.nextSuggestedQuestionIndex + 1
                    else it.nextSuggestedQuestionIndex,
            )
        }

        val visualCapture =
            if (needsVisualContext) {
                latencyTracker.beginVision()
                try {
                    visualContextAnalyzer.captureFrames(
                        timestampMs = timestampMs,
                        state = learningState,
                    )
                } finally {
                    latencyTracker.finishVision()
                }
            } else {
                VisualFrameCaptureResult(
                    frames = emptyList(),
                    statusMessage =
                        "Answered from transcript and learning history; visual analysis was skipped.",
                )
            }
        if (!isCurrentInteraction(interactionId, version, videoId)) return
        _uiState.update {
            it.copy(
                isAnalyzingVideo = false,
                visionStatusMessage = visualCapture.statusMessage,
                interactionLatency = latencyTracker.snapshot(),
            )
        }

        val sentenceChunker = StreamingSentenceChunker(lowLatency = true)
        val speechChannel =
            if (answerSpeaker.isConfigured) Channel<String>(Channel.UNLIMITED) else null
        speechJob?.cancel()
        _uiState.update { it.copy(isPreparingSpeech = speechChannel != null) }
        speechJob =
            speechChannel?.let { channel ->
                viewModelScope.launch {
                    try {
                        SpeechAnswerQueue(answerSpeaker)
                            .play(
                                sentences = channel,
                                isCurrent = {
                                    isCurrentInteraction(interactionId, version, videoId)
                                },
                                onFirstSentence = {
                                    latencyTracker.beginTts()
                                    _uiState.update { it.copy(ttsStatusMessage = "正在准备语音回答...") }
                                },
                                onPlaybackStarted = {
                                    if (isCurrentInteraction(interactionId, version, videoId)) {
                                        latencyTracker.markTtsFirstAudio()
                                        Log.i(
                                            "LearningLatency",
                                            "first_audio visual=$needsVisualContext ${latencyTracker.snapshot()}"
                                        )
                                        _uiState.update {
                                            it.copy(
                                                isPreparingSpeech = false,
                                                isSpeaking = true,
                                                ttsStatusMessage = "豆包正在朗读回答...",
                                                interactionLatency = latencyTracker.snapshot()
                                            )
                                        }
                                    }
                                },
                                onResult = { result ->
                                    _uiState.update {
                                        it.copy(ttsStatusMessage = result.statusMessage)
                                    }
                                },
                            )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (isCurrentInteraction(interactionId, version, videoId)) {
                            _uiState.update {
                                it.copy(
                                    ttsStatusMessage =
                                        "Speech playback failed: ${error.safeStatusMessage()}"
                                )
                            }
                        }
                    } finally {
                        channel.cancel()
                        if (isCurrentInteraction(interactionId, version, videoId)) {
                            _uiState.update {
                                it.copy(isPreparingSpeech = false, isSpeaking = false)
                            }
                        }
                    }
                }
            }
        var streamedAnswer = ""
        latencyTracker.beginLlm()
        val result =
            try {
                assistant.answer(
                    question = question,
                    timestampMs = timestampMs,
                    previousAnswers = learningState.questionAnswers,
                    state = learningState,
                    visualContext = null,
                    visualFrames = visualCapture.frames,
                    onPartialAnswer = { partial ->
                        if (isCurrentInteraction(interactionId, version, videoId)) {
                            latencyTracker.markLlmFirstToken()
                            val delta =
                                if (partial.startsWith(streamedAnswer)) {
                                    partial.removePrefix(streamedAnswer)
                                } else {
                                    ""
                                }
                            streamedAnswer = partial
                            _uiState.update {
                                it.copy(
                                    streamingAnswerText = partial,
                                    interactionLatency = latencyTracker.snapshot(),
                                )
                            }
                            sentenceChunker.append(delta).forEach { sentence ->
                                speechChannel?.trySend(sentence)
                            }
                        }
                    },
                )
            } finally {
                sentenceChunker.finish()?.let { speechChannel?.trySend(it) }
                speechChannel?.close()
            }
        if (!isCurrentInteraction(interactionId, version, videoId)) return
        latencyTracker.finish()
        val updatedAnswers = _uiState.value.questionAnswers + result.questionAnswer
        val updatedHistory =
            historyStore.upsert(
                video = learningState.video,
                progressMs = timestampMs,
            )
        _uiState.update {
            it.copy(
                historyRecords = updatedHistory,
                isAnswering = false,
                streamingAnswerText = "",
                questionAnswers = updatedAnswers,
                selectedQuestionId = result.questionAnswer.id,
                assistantStatusMessage = result.statusMessage,
                interactionLatency = latencyTracker.snapshot(),
            )
        }
        persistCurrentSession(progressMs = timestampMs)
        updateLiveNote(progressMs = timestampMs, force = true)
    }

    fun resumePlayback() {
        interactionTracker.invalidate()
        interactionJob?.cancel()
        stopSpokenAnswer()
        _uiState.update {
            it.copy(
                isListening = false,
                isAnswering = false,
                isAnalyzingVideo = false,
                isSpeaking = false,
            )
        }
        videoViewModel.resumeAfterLearningAnswer()
    }

    fun selectQuestion(id: String) {
        _uiState.update { it.copy(selectedQuestionId = id) }
    }

    fun seekToQuestion(id: String) {
        val answer = _uiState.value.questionAnswers.firstOrNull { it.id == id } ?: return
        videoViewModel.setVideoProgress(answer.timestampMs, dragging = false)
        _uiState.update { it.copy(selectedQuestionId = id) }
    }

    fun seekToChapter(chapter: LearningChapter) {
        videoViewModel.setVideoProgress(chapter.startMs, dragging = false)
    }

    fun generateSummary() {
        val current = _uiState.value
        if (current.isGeneratingSummary) return
        startFinalSummary(
            state = current,
            progressMs = videoViewModel.currentLearningTimestamp(),
            showDocument = true,
        )
    }

    fun dismissSummary() {
        _uiState.update { it.copy(summaryDocument = null) }
    }

    fun openLearningNote(record: LearningHistoryRecord) {
        _uiState.update {
            it.copy(
                notePreviewTitle = record.videoTitle,
                notePreviewVideoId = record.videoId,
                notePreviewMarkdown = "",
                notePreviewPath = record.summaryPath,
                notePreviewStatus = record.noteStatus,
                isLoadingNotePreview = !record.summaryPath.isNullOrBlank(),
                noteSearchQuery = "",
                isEditingNotePreview = false,
                noteEditDraft = "",
                notePreviewStatusMessage = "",
            )
        }
        viewModelScope.launch {
            val note =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val video =
                            LearningCourseCatalog.videos.firstOrNull { it.id == record.videoId }
                        if (video == null) summaryStore.readSummary(record.summaryPath).orEmpty()
                        else summaryStore.readNote(video.toLearningVideo(), record.summaryPath)
                    }
                }
            _uiState.update { current ->
                if (current.notePreviewVideoId == record.videoId) {
                    current.copy(
                        notePreviewMarkdown = note.getOrDefault(""),
                        isLoadingNotePreview = false,
                        notePreviewStatusMessage =
                            note.exceptionOrNull()?.safeStatusMessage().orEmpty(),
                    )
                } else {
                    current
                }
            }
        }
    }

    fun updateNoteSearchQuery(query: String) {
        _uiState.update { it.copy(noteSearchQuery = query) }
    }

    fun beginEditingLearningNote() {
        val state = _uiState.value
        if (state.notePreviewVideoId == null || state.notePreviewMarkdown.isBlank()) return
        _uiState.update {
            it.copy(
                isEditingNotePreview = true,
                noteEditDraft = it.notePreviewMarkdown,
                noteEditOriginal = it.notePreviewMarkdown,
                notePreviewStatusMessage = "Editing Markdown",
            )
        }
    }

    fun updateNoteEditDraft(markdown: String) {
        _uiState.update { it.copy(noteEditDraft = markdown) }
    }

    fun cancelEditingLearningNote() {
        _uiState.update {
            it.copy(
                isEditingNotePreview = false,
                noteEditDraft = "",
                notePreviewStatusMessage = "Changes discarded",
            )
        }
    }

    fun saveEditedLearningNote() {
        val state = _uiState.value
        val videoId = state.notePreviewVideoId ?: return
        val video = LearningCourseCatalog.videos.firstOrNull { it.id == videoId } ?: return
        val markdown = state.noteEditDraft.trim()
        if (markdown.isBlank()) return
        summaryJobs[videoId]?.cancel()
        summaryTaskTracker.cancel(videoId)
        val token = noteWriteCoordinator.begin(videoId)
        _uiState.update {
            it.copy(
                isLoadingNotePreview = true,
                notePreviewStatusMessage = "Saving...",
                isGeneratingSummary = summaryTaskTracker.isRunning(it.video.id),
            )
        }
        viewModelScope.launch {
            try {
                val document =
                    withContext(Dispatchers.IO) {
                        noteWriteCoordinator.commitIfCurrent(token) {
                            summaryStore.saveSummary(
                                video = video.toLearningVideo(),
                                markdown =
                                    LearningNoteContent.mergeUserEdits(
                                        summaryStore.readNote(
                                            video.toLearningVideo(),
                                            state.notePreviewPath
                                        ),
                                        state.noteEditOriginal,
                                        markdown,
                                    ),
                                existingPath = state.notePreviewPath,
                            )
                        }
                    } ?: return@launch
                if (!noteWriteCoordinator.isCurrent(token)) return@launch
                persistSummaryReference(videoId, document.savedPath, document.createdAtMs)
                val history =
                    historyStore.upsert(
                        video = video.toLearningVideo(),
                        summaryPath = document.savedPath,
                        noteStatus = LearningNoteStatus.Ready,
                        summaryUpdatedAtMs = document.createdAtMs,
                    )
                _uiState.update { current ->
                    if (current.notePreviewVideoId != videoId) current
                    else {
                        current.copy(
                            historyRecords = history,
                            notePreviewMarkdown = document.markdown,
                            notePreviewPath = document.savedPath,
                            notePreviewStatus = LearningNoteStatus.Ready,
                            isLoadingNotePreview = false,
                            isEditingNotePreview = false,
                            noteEditDraft = "",
                            notePreviewStatusMessage = "Saved",
                            isGeneratingSummary =
                                if (current.video.id == videoId) false
                                else current.isGeneratingSummary,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingNotePreview = false,
                        notePreviewStatusMessage = "Save failed: ${error.safeStatusMessage()}",
                        isGeneratingSummary =
                            if (it.video.id == videoId) false else it.isGeneratingSummary,
                    )
                }
            }
        }
    }

    fun onLearningNoteCopied() {
        _uiState.update { it.copy(notePreviewStatusMessage = "Markdown copied") }
    }

    fun reorganizeOpenLearningNote() {
        val videoId = _uiState.value.notePreviewVideoId ?: return
        val video = LearningCourseCatalog.videos.firstOrNull { it.id == videoId } ?: return
        val persisted = sessionStore.load(videoId)
        val state = createLearningState(video, persisted)
        _uiState.update {
            it.copy(
                notePreviewStatus = LearningNoteStatus.Organizing,
                notePreviewStatusMessage = "AI is reorganizing this note...",
            )
        }
        startFinalSummary(state, persisted.progressMs, showDocument = false)
    }

    fun openLearningNoteTimestamp(timestampMs: Long): Boolean {
        val videoId = _uiState.value.notePreviewVideoId ?: return false
        val video = LearningCourseCatalog.videos.firstOrNull { it.id == videoId } ?: return false
        selectCourseVideo(video, startPositionMs = timestampMs)
        return true
    }

    fun selectCourseVideo(courseVideo: LearningCourseVideo, startPositionMs: Long? = null) {
        val availability = _uiState.value.courseAvailability
        persistCurrentSession()
        interactionTracker.invalidate()
        interactionJob?.cancel()
        liveNoteJob?.cancel()
        stopSpokenAnswer()
        sessionLifecycle.start()
        lastLiveNoteVideoId = null
        lastLiveNoteProgressMs = 0L
        lastLiveNoteQuestionCount = 0
        val stored = sessionStore.load(courseVideo.id)
        val persisted =
            if (startPositionMs == null) {
                if (stored.progressMs >= courseVideo.durationMs) stored.copy(progressMs = 0L)
                else stored
            } else stored.copy(progressMs = startPositionMs.coerceIn(0L, courseVideo.durationMs))
        videoViewModel.loadVideo(
            sourceUri = courseVideo.sourceUri,
            durationMs = courseVideo.durationMs,
            resumePositionMs = persisted.progressMs,
            playbackSpeed = persisted.playbackSpeed,
        )
        val selectedState = createLearningState(courseVideo, persisted)
        val updatedHistory =
            historyStore.upsert(
                video = selectedState.video,
                progressMs = persisted.progressMs,
                summaryPath = persisted.summaryPath,
            )
        _uiState.value =
            selectedState.copy(
                historyRecords = updatedHistory,
                courseAvailability = availability,
                sessionId = sessionVersion,
                isGeneratingSummary = summaryTaskTracker.isRunning(courseVideo.id),
            )
    }

    fun setPlaybackSpeed(speed: Float) {
        videoViewModel.setPlaybackSpeed(speed)
        persistCurrentSession()
    }

    fun onSessionExit(expectedSessionId: Long = sessionVersion): Boolean {
        if (!sessionLifecycle.canExit(expectedSessionId)) return false
        val state = _uiState.value
        val progressMs = videoViewModel.currentLearningTimestamp()
        persistCurrentSession(progressMs = progressMs)
        sessionLifecycle.exit(expectedSessionId)
        interactionTracker.invalidate()
        interactionJob?.cancel()
        liveNoteJob?.cancel()
        stopSpokenAnswer()
        videoViewModel.pause()
        startFinalSummary(
            state = state,
            progressMs = progressMs,
            showDocument = false,
            deduplicate = true
        )
        return true
    }

    fun currentChapter(timestampMs: Long): LearningChapter =
        assistant.findChapter(timestampMs, _uiState.value.chapters)

    fun setSummaryDirectory(uri: Uri, flags: Int) {
        summaryStore.persistSummaryDirectory(uri, flags)
        _uiState.update {
            it.copy(
                summaryDirectoryLabel = summaryStore.directoryLabel,
                assistantStatusMessage = "Markdown summaries will be saved to the selected folder.",
            )
        }
    }

    private fun updateLiveNote(
        progressMs: Long = videoViewModel.currentLearningTimestamp(),
        force: Boolean = false,
    ) {
        if (!sessionActive) return
        val state = _uiState.value
        if (summaryTaskTracker.isRunning(state.video.id)) return
        if (
            !force &&
                !LiveNoteUpdatePolicy.shouldUpdate(
                    videoId = state.video.id,
                    progressMs = progressMs,
                    questionCount = state.questionAnswers.size,
                    lastVideoId = lastLiveNoteVideoId,
                    lastProgressMs = lastLiveNoteProgressMs,
                    lastQuestionCount = lastLiveNoteQuestionCount,
                )
        ) {
            return
        }
        lastLiveNoteVideoId = state.video.id
        lastLiveNoteProgressMs = progressMs
        lastLiveNoteQuestionCount = state.questionAnswers.size
        liveNoteJob?.cancel()
        val writeToken = noteWriteCoordinator.begin(state.video.id)
        liveNoteJob =
            viewModelScope.launch {
                try {
                    val existingPath = sessionStore.load(state.video.id).summaryPath
                    val document =
                        withContext(Dispatchers.IO) {
                            noteWriteCoordinator.commitIfCurrent(writeToken) {
                                summaryStore.saveSummary(
                                    video = state.video,
                                    markdown =
                                        LearningNoteContent.mergeLive(
                                            summaryStore.readNote(state.video, existingPath),
                                            LiveLearningNoteBuilder.build(state, progressMs),
                                        ),
                                    existingPath = existingPath,
                                )
                            }
                        } ?: return@launch
                    if (!noteWriteCoordinator.isCurrent(writeToken)) return@launch
                    persistSummaryReference(
                        videoId = state.video.id,
                        savedPath = document.savedPath,
                        updatedAtMs = document.createdAtMs,
                    )
                    val history =
                        historyStore.upsert(
                            video = state.video,
                            summaryPath = document.savedPath,
                            noteStatus = LearningNoteStatus.Draft,
                            summaryUpdatedAtMs = document.createdAtMs,
                        )
                    _uiState.update { current ->
                        current.copy(
                            historyRecords = history,
                            notePreviewMarkdown =
                                if (current.notePreviewVideoId == state.video.id) document.markdown
                                else current.notePreviewMarkdown,
                            notePreviewPath =
                                if (current.notePreviewVideoId == state.video.id) document.savedPath
                                else current.notePreviewPath,
                            notePreviewStatus =
                                if (current.notePreviewVideoId == state.video.id) {
                                    LearningNoteStatus.Draft
                                } else {
                                    current.notePreviewStatus
                                },
                            isLoadingNotePreview =
                                if (current.notePreviewVideoId == state.video.id) false
                                else current.isLoadingNotePreview,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (!noteWriteCoordinator.isCurrent(writeToken)) return@launch
                    val history =
                        historyStore.upsert(
                            video = state.video,
                            noteStatus = LearningNoteStatus.Failed,
                        )
                    _uiState.update { current ->
                        current.copy(
                            historyRecords = history,
                            assistantStatusMessage =
                                if (current.video.id == state.video.id) {
                                    "实时笔记保存失败：${error.safeStatusMessage()}"
                                } else {
                                    current.assistantStatusMessage
                                },
                        )
                    }
                }
            }
    }

    private fun startFinalSummary(
        state: LearningUiState,
        progressMs: Long,
        showDocument: Boolean,
        deduplicate: Boolean = false,
    ) {
        val videoId = state.video.id
        val revision =
            LearningSummaryRevision(
                state.sessionId,
                progressMs,
                state.questionAnswers.map { it.id }
            )
        if (deduplicate && !summaryTaskTracker.needsUpdate(videoId, revision)) return
        summaryJobs[videoId]?.cancel()
        val summaryTask = summaryTaskTracker.begin(videoId, revision)
        val writeToken = noteWriteCoordinator.begin(videoId)
        val organizingHistory =
            historyStore.upsert(
                video = state.video,
                noteStatus = LearningNoteStatus.Organizing,
            )
        _uiState.update { current ->
            current.copy(
                historyRecords = organizingHistory,
                isGeneratingSummary =
                    if (current.video.id == videoId) true else current.isGeneratingSummary,
            )
        }
        summaryJobs[videoId] =
            viewModelScope.launch {
                var succeeded = false
                var savedPath = sessionStore.load(videoId).summaryPath
                try {
                    val liveDraft =
                        withContext(Dispatchers.IO) {
                            noteWriteCoordinator.commitIfCurrent(writeToken) {
                                summaryStore.saveSummary(
                                    video = state.video,
                                    markdown =
                                        LearningNoteContent.mergeLive(
                                            summaryStore.readNote(state.video, savedPath),
                                            LiveLearningNoteBuilder.build(state, progressMs),
                                        ),
                                    existingPath = savedPath,
                                )
                            }
                        } ?: return@launch
                    if (!noteWriteCoordinator.isCurrent(writeToken)) return@launch
                    savedPath = liveDraft.savedPath
                    persistSummaryReference(videoId, liveDraft.savedPath, liveDraft.createdAtMs)
                    val draftHistory =
                        historyStore.upsert(
                            video = state.video,
                            summaryPath = liveDraft.savedPath,
                            noteStatus = LearningNoteStatus.Organizing,
                            summaryUpdatedAtMs = liveDraft.createdAtMs,
                        )
                    _uiState.update { current ->
                        current.copy(
                            historyRecords = draftHistory,
                            notePreviewMarkdown =
                                if (current.notePreviewVideoId == videoId) liveDraft.markdown
                                else current.notePreviewMarkdown,
                            notePreviewPath =
                                if (current.notePreviewVideoId == videoId) liveDraft.savedPath
                                else current.notePreviewPath,
                            notePreviewStatus =
                                if (current.notePreviewVideoId == videoId) {
                                    LearningNoteStatus.Organizing
                                } else {
                                    current.notePreviewStatus
                                },
                            isLoadingNotePreview =
                                if (current.notePreviewVideoId == videoId) false
                                else current.isLoadingNotePreview,
                        )
                    }

                    val result = assistant.generateSummary(state, liveDraft.markdown, progressMs)
                    check(result.mode != AssistantResponseMode.Error) { result.statusMessage }
                    val noteStatus =
                        if (result.mode == AssistantResponseMode.Live) LearningNoteStatus.Ready
                        else LearningNoteStatus.Draft
                    val finalDocument =
                        withContext(Dispatchers.IO) {
                            noteWriteCoordinator.commitIfCurrent(writeToken) {
                                summaryStore.saveSummary(
                                    video = state.video,
                                    markdown =
                                        LearningNoteContent.mergeSummary(
                                            summaryStore.readNote(state.video, savedPath),
                                            result.markdown,
                                        ),
                                    existingPath = savedPath,
                                )
                            }
                        } ?: return@launch
                    if (!noteWriteCoordinator.isCurrent(writeToken)) return@launch
                    succeeded = true
                    savedPath = finalDocument.savedPath
                    persistSummaryReference(
                        videoId,
                        finalDocument.savedPath,
                        finalDocument.createdAtMs,
                    )
                    val readyHistory =
                        historyStore.upsert(
                            video = state.video,
                            summaryPath = finalDocument.savedPath,
                            noteStatus = noteStatus,
                            summaryUpdatedAtMs = finalDocument.createdAtMs,
                        )
                    _uiState.update { current ->
                        current.copy(
                            historyRecords = readyHistory,
                            summaryDocument =
                                if (showDocument && current.video.id == videoId) finalDocument
                                else current.summaryDocument,
                            notePreviewMarkdown =
                                if (current.notePreviewVideoId == videoId) {
                                    finalDocument.markdown
                                } else {
                                    current.notePreviewMarkdown
                                },
                            notePreviewPath =
                                if (current.notePreviewVideoId == videoId) finalDocument.savedPath
                                else current.notePreviewPath,
                            notePreviewStatus =
                                if (current.notePreviewVideoId == videoId) noteStatus
                                else current.notePreviewStatus,
                            notePreviewStatusMessage =
                                if (current.notePreviewVideoId == videoId) result.statusMessage
                                else current.notePreviewStatusMessage,
                            summaryDirectoryLabel = summaryStore.directoryLabel,
                            assistantStatusMessage =
                                if (current.video.id == videoId) result.statusMessage
                                else current.assistantStatusMessage,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (!noteWriteCoordinator.isCurrent(writeToken)) return@launch
                    val failedHistory =
                        historyStore.upsert(
                            video = state.video,
                            summaryPath = savedPath,
                            noteStatus = LearningNoteStatus.Failed,
                        )
                    _uiState.update { current ->
                        current.copy(
                            historyRecords = failedHistory,
                            notePreviewStatus =
                                if (current.notePreviewVideoId == videoId) LearningNoteStatus.Failed
                                else current.notePreviewStatus,
                            assistantStatusMessage =
                                if (current.video.id == videoId) {
                                    "学习笔记整理失败：${error.safeStatusMessage()}"
                                } else {
                                    current.assistantStatusMessage
                                },
                        )
                    }
                } finally {
                    summaryTaskTracker.finish(summaryTask, succeeded)
                    _uiState.update { current ->
                        current.copy(
                            isGeneratingSummary = summaryTaskTracker.isRunning(current.video.id)
                        )
                    }
                }
            }
    }

    private fun persistSummaryReference(
        videoId: String,
        savedPath: String,
        updatedAtMs: Long,
    ) {
        val persisted = sessionStore.load(videoId)
        sessionStore.save(
            persisted.copy(
                summaryPath = savedPath,
                updatedAtMs = updatedAtMs,
            )
        )
    }

    private fun beginQuestionTimestamp(): Long =
        if (_uiState.value.autoPauseOnQuestion) {
            videoViewModel.pauseForLearningQuestion()
        } else {
            videoViewModel.mediaTimelineSnapshot().positionMs
        }

    private fun stopSpokenAnswer() {
        speechJob?.cancel()
        speechJob = null
        answerSpeaker.stop()
        _uiState.update { it.copy(isPreparingSpeech = false, isSpeaking = false) }
    }

    private fun createLearningState(
        courseVideo: LearningCourseVideo,
        persisted: PersistedLearningSession,
    ): LearningUiState {
        val video = courseVideo.toLearningVideo()
        return LearningUiState(
            video = video,
            chapters = courseVideo.chapters,
            transcriptSegments = transcriptRepository.load(video),
            historyRecords = historyStore.loadRecords(),
            questionAnswers = persisted.questionAnswers,
            selectedQuestionId = persisted.questionAnswers.lastOrNull()?.id,
            assistantStatusMessage =
                if (assistant.isConfigured) "豆包 AI 助教已就绪。" else "豆包模型尚未配置，暂时使用本地课程内容回答。",
            asrStatusMessage = speechQuestionTranscriber.readinessMessage,
            visionStatusMessage = visualContextAnalyzer.readinessMessage,
            ttsStatusMessage = answerSpeaker.readinessMessage,
            isVisionConfigured = visualContextAnalyzer.isConfigured,
            isTtsConfigured = answerSpeaker.isConfigured,
            summaryDirectoryLabel = summaryStore.directoryLabel,
            isAssistantConfigured = assistant.isConfigured,
            isAsrReady = speechQuestionTranscriber.isReady,
            teachingMode = persisted.teachingMode,
            subtitleOffsetMs = persisted.subtitleOffsetMs,
        )
    }

    private fun persistCurrentSession(
        progressMs: Long? = null,
        summaryPath: String? = null,
    ) {
        if (!sessionActive) return
        val state = _uiState.value
        val previous = sessionStore.load(state.video.id)
        val resolvedProgressMs = progressMs ?: videoViewModel.currentLearningTimestamp()
        val session =
            PersistedLearningSession(
                videoId = state.video.id,
                progressMs = resolvedProgressMs.coerceIn(0L, state.video.durationMs),
                playbackSpeed = videoViewModel.getPlaybackSpeed(),
                questionAnswers = state.questionAnswers,
                summaryPath = summaryPath ?: previous.summaryPath,
                updatedAtMs = System.currentTimeMillis(),
                subtitleOffsetMs = state.subtitleOffsetMs,
                teachingMode = state.teachingMode,
            )
        sessionStore.save(session)
        val history =
            historyStore.upsert(
                video = state.video,
                progressMs = session.progressMs,
                summaryPath = session.summaryPath,
                nowMs = session.updatedAtMs,
            )
        _uiState.update { current ->
            if (current.video.id == session.videoId) current.copy(historyRecords = history)
            else current
        }
    }

    private fun isCurrentSession(version: Long, videoId: String): Boolean =
        sessionVersion == version && _uiState.value.video.id == videoId

    private fun isCurrentInteraction(
        interactionId: Long,
        version: Long,
        videoId: String,
    ): Boolean = interactionTracker.isCurrent(interactionId) && isCurrentSession(version, videoId)

    override fun onCleared() {
        persistCurrentSession()
        stopSpokenAnswer()
        interactionTracker.invalidate()
        interactionJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
        const val MIN_SUBTITLE_OFFSET_MS = -5_000L
        const val MAX_SUBTITLE_OFFSET_MS = 5_000L
    }
}

private fun LearningUiState.isBusy(): Boolean =
    isListening || isAnswering || isAnalyzingVideo || isPreparingSpeech

private fun LearningUiState.canBargeIn(): Boolean =
    !isListening && (isAnalyzingVideo || isAnswering || isPreparingSpeech || isSpeaking)

private fun Throwable.safeStatusMessage(): String =
    message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120).ifBlank { "unknown error" }
