package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAssistantRulesRegressionTest {
    @Test
    fun wordsInsideLearningQuestionsNeverStartPlayback() {
        listOf(
                "Can you continue with an example?",
                "Why does the algorithm continue iterating?",
                "Please continue the explanation",
                "可以继续举个例子吗？",
                "这个没问题，但是下一步为什么这样计算？",
            )
            .forEach { assertFalse(it, LearningAssistantText.isResumeIntent(it)) }
    }

    @Test
    fun politeExplicitPlaybackCommandsAreAccepted() {
        listOf("Please resume the video.", "Can you play the video?", "继续吧", "好的，继续播放").forEach {
            assertTrue(it, LearningAssistantText.isResumeIntent(it))
        }
    }

    @Test
    fun assistantContextUsesTheSameSubtitleOffsetAsTheScreen() {
        val state =
            LearningUiState(
                video = SampleLearningContent.video.copy(durationMs = 200_000L),
                subtitleOffsetMs = 5_000L,
                transcriptSegments =
                    listOf(
                        TranscriptSegment(
                            "v",
                            35_000L,
                            36_000L,
                            "Included after calibration",
                            "",
                            ""
                        ),
                        TranscriptSegment(
                            "v",
                            128_000L,
                            129_000L,
                            "Outside calibrated window",
                            "",
                            ""
                        ),
                    ),
            )
        assertEquals(
            listOf("Included after calibration"),
            LearningAssistantText.findContext(100_000L, state).map { it.text }
        )
    }

    @Test
    fun resumeIntentAcceptsStandaloneCommands() {
        assertTrue(LearningAssistantText.isResumeIntent("\u7ee7\u7eed\u64ad\u653e"))
        assertTrue(LearningAssistantText.isResumeIntent("continue"))
        assertTrue(LearningAssistantText.isResumeIntent("resume the video"))
    }

    @Test
    fun resumeIntentRejectsNegationAndContinueExplanationQuestions() {
        assertFalse(LearningAssistantText.isResumeIntent("\u4e0d\u8981\u7ee7\u7eed\u64ad\u653e"))
        assertFalse(
            LearningAssistantText.isResumeIntent("\u7ee7\u7eed\u89e3\u91ca\u8fd9\u4e2a\u6982\u5ff5")
        )
        assertFalse(LearningAssistantText.isResumeIntent("don't continue"))
        assertFalse(LearningAssistantText.isResumeIntent("continue explaining this topic"))
    }

    @Test
    fun chapterLookupUsesFirstChapterBeforeItsFirstBookmark() {
        val chapters =
            listOf(
                LearningChapter("first", "First", 28_000L, 60_000L, "First"),
                LearningChapter("second", "Second", 60_000L, 120_000L, "Second"),
            )
        val engine = DeepSeekLearningAssistantEngine(apiKey = "")

        assertEquals("first", engine.findChapter(0L, chapters).id)
    }

    @Test
    fun chapterLookupUsesLastChapterAfterVideoEnd() {
        val chapters =
            listOf(
                LearningChapter("first", "First", 0L, 60_000L, "First"),
                LearningChapter("last", "Last", 60_000L, 120_000L, "Last"),
            )
        val engine = DeepSeekLearningAssistantEngine(apiKey = "")

        assertEquals("last", engine.findChapter(180_000L, chapters).id)
    }

    @Test
    fun visualQuestionPolicyOnlyAnalyzesQuestionsThatDependOnThePicture() {
        assertTrue(VisualQuestionPolicy.requiresVisualContext("老师在黑板上写的公式是什么？"))
        assertTrue(
            VisualQuestionPolicy.requiresVisualContext("What does the diagram on screen show?")
        )
        assertFalse(VisualQuestionPolicy.requiresVisualContext("什么是梯度下降？"))
        assertFalse(
            VisualQuestionPolicy.requiresVisualContext("Can you explain this concept again?")
        )
    }

    @Test
    fun answerPromptUsesConversationalTutorVoiceInsteadOfReportLabels() {
        val prompt = LearningAssistantText.answerSystemPrompt(LearningTeachingMode.Example)

        assertTrue(prompt.contains("warm", ignoreCase = true))
        assertTrue(prompt.contains("spoken", ignoreCase = true))
        assertTrue(prompt.contains("concrete example", ignoreCase = true))
        assertFalse(prompt.contains("state whether claims come from", ignoreCase = true))
        assertFalse(prompt.contains("Short answer:", ignoreCase = true))
    }

    @Test
    fun learnerLanguagePolicyFollowsChineseAndEnglishQuestions() {
        assertEquals(LearningLanguage.Chinese, LearningLanguagePolicy.detect("这个公式是什么意思？"))
        assertEquals(LearningLanguage.English, LearningLanguagePolicy.detect("Why is this convex?"))
    }

    @Test
    fun answerPromptStatesEvidenceBoundaryWhenVisionIsUnavailable() {
        val prompt =
            LearningAssistantText.answerUserPrompt(
                question = "老师写了什么？",
                timestampMs = 1_000L,
                context = emptyList(),
                previousAnswers = emptyList(),
                state = LearningUiState(teachingMode = LearningTeachingMode.Socratic),
                visualContext = null,
            )

        assertTrue(prompt.contains("Do not claim to see", ignoreCase = true))
        assertTrue(prompt.contains("Reply in Chinese", ignoreCase = true))
        assertTrue(prompt.contains("guiding question", ignoreCase = true))
    }

    @Test
    fun localFallbackMatchesTheLearnersLanguage() {
        val english =
            LearningAssistantText.localAnswerFallback(
                question = "Why is this convex?",
                timestampMs = 1_000L,
                context = emptyList(),
                previousAnswers = emptyList(),
                state = LearningUiState(),
            )
        val chinese =
            LearningAssistantText.localAnswerFallback(
                question = "为什么这是凸函数？",
                timestampMs = 1_000L,
                context = emptyList(),
                previousAnswers = emptyList(),
                state = LearningUiState(),
            )

        assertTrue(english.contains("For your question"))
        assertFalse(english.contains("关于"))
        assertTrue(chinese.contains("关于"))
    }
}
