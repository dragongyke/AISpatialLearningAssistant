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

class LearningHistoryCodecTest {
    @Test
    fun noteCompletionPreservesLatestProgressAndWatchTime() {
        val original =
            LearningHistoryRecord(
                videoId = "video-1",
                videoTitle = "Lecture 1",
                category = "Programming",
                lastWatchedAtMs = 2000L,
                progressMs = 900_000L,
                durationMs = 3_000_000L,
                summaryPath = null,
            )
        val updated =
            LearningHistoryCodec.upsert(
                    records = listOf(original),
                    videoId = original.videoId,
                    videoTitle = original.videoTitle,
                    category = original.category,
                    progressMs = null,
                    durationMs = original.durationMs,
                    summaryPath = "content://summary/lecture-1.md",
                    noteStatus = LearningNoteStatus.Ready,
                    summaryUpdatedAtMs = 5000L,
                    nowMs = 5000L,
                )
                .single()
        assertEquals(900_000L, updated.progressMs)
        assertEquals(2000L, updated.lastWatchedAtMs)
        assertEquals(5000L, updated.summaryUpdatedAtMs)
        assertEquals(LearningNoteStatus.Ready, updated.noteStatus)
    }

    @Test
    fun roundTripsHistoryRecords() {
        val records =
            listOf(
                LearningHistoryRecord(
                    videoId = "video-1",
                    videoTitle = "Lecture 1",
                    category = "Programming",
                    lastWatchedAtMs = 1000L,
                    progressMs = 240_000L,
                    durationMs = 3_000_000L,
                    summaryPath = "content://summary/lecture-1.md",
                    noteStatus = LearningNoteStatus.Ready,
                    summaryUpdatedAtMs = 900L,
                )
            )

        assertEquals(records, LearningHistoryCodec.decode(LearningHistoryCodec.encode(records)))
    }

    @Test
    fun upsertKeepsOneRecordPerVideoAndPreservesExistingSummaryPathWhenNewOneIsMissing() {
        val oldRecord =
            LearningHistoryRecord(
                videoId = "video-1",
                videoTitle = "Lecture 1",
                category = "Programming",
                lastWatchedAtMs = 1000L,
                progressMs = 120_000L,
                durationMs = 3_000_000L,
                summaryPath = "content://summary/lecture-1.md",
            )
        val updated =
            LearningHistoryCodec.upsert(
                records = listOf(oldRecord),
                videoId = "video-1",
                videoTitle = "Lecture 1 Updated",
                category = "Programming",
                progressMs = 900_000L,
                durationMs = 3_000_000L,
                summaryPath = null,
                nowMs = 2000L,
            )

        assertEquals(1, updated.size)
        assertEquals("Lecture 1 Updated", updated.single().videoTitle)
        assertEquals(900_000L, updated.single().progressMs)
        assertEquals("content://summary/lecture-1.md", updated.single().summaryPath)
    }

    @Test
    fun decodesLegacyHistoryWithoutNoteStatus() {
        val legacy =
            listOf(
                    "video-1",
                    "Lecture 1",
                    "Programming",
                    "1000",
                    "120000",
                    "3000000",
                    "content://summary/lecture-1.md",
                )
                .joinToString("\t") { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()) }

        val record = LearningHistoryCodec.decode(legacy).single()

        assertEquals(LearningNoteStatus.Ready, record.noteStatus)
        assertEquals(0L, record.summaryUpdatedAtMs)
    }
}
