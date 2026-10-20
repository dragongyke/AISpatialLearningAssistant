package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLearningNoteBuilderTest {
    @Test
    fun liveDraftIncludesCoveredTranscriptAndLearnerQuestions() {
        val state =
            LearningUiState(
                video = SampleLearningContent.video,
                transcriptSegments =
                    listOf(
                        TranscriptSegment(
                            videoId = SampleLearningContent.video.id,
                            startMs = 0L,
                            endMs = 10_000L,
                            text = "Gradient descent updates parameters to reduce a loss function.",
                            summary = "Gradient descent basics",
                            visualSummary = "",
                        ),
                        TranscriptSegment(
                            videoId = SampleLearningContent.video.id,
                            startMs = 60_000L,
                            endMs = 70_000L,
                            text = "This segment has not been watched yet.",
                            summary = "Future content",
                            visualSummary = "",
                        ),
                    ),
                questionAnswers =
                    listOf(
                        LearningQuestionAnswer(
                            id = "qa-1",
                            timestampMs = 8_000L,
                            questionText = "Why does the loss decrease?",
                            answerText = "Because the update follows the negative gradient.",
                            contextStartMs = 0L,
                            contextEndMs = 10_000L,
                            createdAtMs = 1L,
                        )
                    ),
            )

        val markdown = LiveLearningNoteBuilder.build(state, progressMs = 15_000L)

        assertTrue(markdown.contains("Gradient descent updates parameters"))
        assertTrue(markdown.contains("Why does the loss decrease?"))
        assertTrue(markdown.contains("## 课程知识点"))
        assertTrue(markdown.contains("## 用户问题与 AI 解释"))
        assertTrue(markdown.contains("**00:00:08**"))
        assertFalse(markdown.contains("not been watched yet"))
    }

    @Test
    fun updatePolicyRefreshesOnIntervalOrNewQuestion() {
        assertTrue(
            LiveNoteUpdatePolicy.shouldUpdate(
                videoId = "video-1",
                progressMs = 5_000L,
                questionCount = 0,
                lastVideoId = null,
                lastProgressMs = 0L,
                lastQuestionCount = 0,
            )
        )
        assertFalse(
            LiveNoteUpdatePolicy.shouldUpdate(
                videoId = "video-1",
                progressMs = 10_000L,
                questionCount = 0,
                lastVideoId = "video-1",
                lastProgressMs = 5_000L,
                lastQuestionCount = 0,
            )
        )
        assertTrue(
            LiveNoteUpdatePolicy.shouldUpdate(
                videoId = "video-1",
                progressMs = 20_000L,
                questionCount = 0,
                lastVideoId = "video-1",
                lastProgressMs = 5_000L,
                lastQuestionCount = 0,
            )
        )
        assertTrue(
            LiveNoteUpdatePolicy.shouldUpdate(
                videoId = "video-1",
                progressMs = 10_000L,
                questionCount = 1,
                lastVideoId = "video-1",
                lastProgressMs = 5_000L,
                lastQuestionCount = 0,
            )
        )
    }
}
