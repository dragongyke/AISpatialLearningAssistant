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

class LearningAssistantEngineTest {
    @Test
    fun resumeIntentMatchesChineseAndEnglishContinueCommands() {
        assertTrue(LearningAssistantText.isResumeIntent("\u7ee7\u7eed\u64ad\u653e"))
        assertTrue(
            LearningAssistantText.isResumeIntent("\u6ca1\u95ee\u9898\u4e86\uff0c\u7ee7\u7eed")
        )
        assertTrue(LearningAssistantText.isResumeIntent("continue please"))
        assertTrue(LearningAssistantText.isResumeIntent("resume the video"))
    }

    @Test
    fun resumeIntentDoesNotMatchRegularQuestions() {
        assertFalse(
            LearningAssistantText.isResumeIntent(
                "\u8fd9\u91cc\u7684\u68af\u5ea6\u4e0b\u964d\u662f\u4ec0\u4e48\u610f\u601d\uff1f"
            )
        )
        assertFalse(LearningAssistantText.isResumeIntent("Can you explain convexity again?"))
    }
}
