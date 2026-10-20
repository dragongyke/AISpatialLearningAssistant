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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAssistantMultimodalPromptTest {
    @Test
    fun includesVisualEvidenceAlongsideTranscriptAndHistory() {
        val state =
            LearningUiState(
                questionAnswers =
                    listOf(
                        LearningQuestionAnswer(
                            id = "previous",
                            timestampMs = 2_000L,
                            questionText = "What is the symbol?",
                            answerText = "It is theta.",
                            contextStartMs = 0L,
                            contextEndMs = 4_000L,
                            createdAtMs = 1L,
                        )
                    )
            )

        val prompt =
            LearningAssistantText.answerUserPrompt(
                question = "What did the teacher write on the board?",
                timestampMs = 10_000L,
                context = state.transcriptSegments,
                previousAnswers = state.questionAnswers,
                state = state,
                visualContext = "The center frame shows the equation y = wx + b.",
            )

        assertTrue(prompt.contains("Visual evidence"))
        assertTrue(prompt.contains("y = wx + b"))
        assertTrue(prompt.contains("Nearby video context"))
        assertTrue(prompt.contains("What is the symbol?"))
        assertFalse(prompt.contains("Visual evidence is unavailable"))
    }
}
