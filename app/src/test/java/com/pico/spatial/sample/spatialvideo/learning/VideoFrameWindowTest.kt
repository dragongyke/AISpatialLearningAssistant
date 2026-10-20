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
import org.junit.Test

class VideoFrameWindowTest {
    @Test
    fun samplesBeforeCurrentAndAfterFrames() {
        assertEquals(
            listOf(8_000L, 10_000L, 12_000L),
            VideoFrameWindow.timestamps(currentMs = 10_000L, durationMs = 30_000L),
        )
    }

    @Test
    fun clampsAndDeduplicatesFramesAtVideoEdges() {
        assertEquals(
            listOf(0L, 1_000L, 3_000L),
            VideoFrameWindow.timestamps(currentMs = 1_000L, durationMs = 3_000L),
        )
        assertEquals(
            listOf(0L),
            VideoFrameWindow.timestamps(currentMs = 0L, durationMs = 0L),
        )
    }
}
