package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftedQuestionPolicyTest {
    @Test
    fun preparationAndPlaybackAllowNewQuestionOrResume() {
        for (question in listOf("Explain that again", "resume")) {
            val state = LearningUiState(draftedQuestion = question)
            assertTrue(state.copy(isPreparingSpeech = true).canSubmitDraftedQuestion())
            assertTrue(state.copy(isSpeaking = true).canSubmitDraftedQuestion())
        }
    }

    @Test
    fun blankDraftListeningAndOngoingGenerationRemainBlocked() {
        val state = LearningUiState(draftedQuestion = "A question")
        assertFalse(state.copy(draftedQuestion = " ").canSubmitDraftedQuestion())
        assertFalse(state.copy(isListening = true).canSubmitDraftedQuestion())
        assertFalse(state.copy(isAnswering = true).canSubmitDraftedQuestion())
        assertFalse(state.copy(isAnalyzingVideo = true).canSubmitDraftedQuestion())
    }
}
