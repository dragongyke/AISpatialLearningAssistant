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

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextSanitizerTest {
    @Test
    fun truncatedSpeechTextStaysWithinTheProviderByteLimit() {
        val result = SpeechTextSanitizer.forPlayback("a".repeat(2_000))

        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 900)
    }
}
