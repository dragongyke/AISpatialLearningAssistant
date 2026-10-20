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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubaoSpeechResultParserTest {
    @Test
    fun extractsTextFromBigAsrResultArray() {
        val payload =
            """
            {
              "code": 1000,
              "result": [
                {
                  "text": "Teacher explains gradient descent.",
                  "utterances": []
                }
              ]
            }
            """
                .trimIndent()

        assertEquals(
            "Teacher explains gradient descent.",
            DoubaoSpeechResultParser.extractText(payload),
        )
    }

    @Test
    fun extractsTextFromNestedResultObject() {
        val payload = """{"result":{"text":"What is backpropagation?"}}"""

        assertEquals(
            "What is backpropagation?",
            DoubaoSpeechResultParser.extractText(payload),
        )
    }

    @Test
    fun returnsNullForStatusOnlyPayload() {
        assertNull(DoubaoSpeechResultParser.extractText("""{"code":1000,"message":"Success"}"""))
    }
}
