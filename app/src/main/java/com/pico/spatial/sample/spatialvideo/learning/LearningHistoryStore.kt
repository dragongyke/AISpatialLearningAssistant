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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LearningHistoryStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("ai_learning_history", Context.MODE_PRIVATE)

    fun loadRecords(): List<LearningHistoryRecord> =
        LearningHistoryCodec.decode(preferences.getString(KEY_RECORDS, "").orEmpty())

    fun upsert(
        video: LearningVideo,
        progressMs: Long? = null,
        summaryPath: String? = null,
        noteStatus: LearningNoteStatus? = null,
        summaryUpdatedAtMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): List<LearningHistoryRecord> {
        val updated =
            LearningHistoryCodec.upsert(
                records = loadRecords(),
                videoId = video.id,
                videoTitle = video.title,
                category = video.category,
                progressMs = progressMs,
                durationMs = video.durationMs,
                summaryPath = summaryPath,
                noteStatus = noteStatus,
                summaryUpdatedAtMs = summaryUpdatedAtMs,
                nowMs = nowMs,
            )
        preferences.edit().putString(KEY_RECORDS, LearningHistoryCodec.encode(updated)).apply()
        return updated
    }

    private companion object {
        const val KEY_RECORDS = "records"
    }
}

object LearningHistoryCodec {
    fun encode(records: List<LearningHistoryRecord>): String =
        records.joinToString("\n") { record ->
            listOf(
                    record.videoId,
                    record.videoTitle,
                    record.category,
                    record.lastWatchedAtMs.toString(),
                    record.progressMs.toString(),
                    record.durationMs.toString(),
                    record.summaryPath.orEmpty(),
                    record.noteStatus.name,
                    record.summaryUpdatedAtMs.toString(),
                )
                .joinToString("\t") { it.urlEncode() }
        }

    fun decode(value: String): List<LearningHistoryRecord> =
        value
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t').map { it.urlDecode() }
                if (parts.size != LEGACY_FIELD_COUNT && parts.size != FIELD_COUNT) {
                    return@mapNotNull null
                }
                val summaryPath = parts[6].ifBlank { null }
                LearningHistoryRecord(
                    videoId = parts[0],
                    videoTitle = parts[1],
                    category = parts[2],
                    lastWatchedAtMs = parts[3].toLongOrNull() ?: return@mapNotNull null,
                    progressMs = parts[4].toLongOrNull() ?: return@mapNotNull null,
                    durationMs = parts[5].toLongOrNull() ?: return@mapNotNull null,
                    summaryPath = summaryPath,
                    noteStatus =
                        parts.getOrNull(7)?.let { encoded ->
                            runCatching { LearningNoteStatus.valueOf(encoded) }.getOrNull()
                        }
                            ?: if (summaryPath == null) LearningNoteStatus.NotStarted
                            else LearningNoteStatus.Ready,
                    summaryUpdatedAtMs = parts.getOrNull(8)?.toLongOrNull() ?: 0L,
                )
            }
            .sortedByDescending { it.lastWatchedAtMs }
            .toList()

    fun upsert(
        records: List<LearningHistoryRecord>,
        videoId: String,
        videoTitle: String,
        category: String,
        progressMs: Long?,
        durationMs: Long,
        summaryPath: String?,
        noteStatus: LearningNoteStatus? = null,
        summaryUpdatedAtMs: Long? = null,
        nowMs: Long,
    ): List<LearningHistoryRecord> {
        val previous = records.firstOrNull { it.videoId == videoId }
        val updated =
            LearningHistoryRecord(
                videoId = videoId,
                videoTitle = videoTitle,
                category = category,
                lastWatchedAtMs =
                    if (progressMs == null) previous?.lastWatchedAtMs ?: nowMs else nowMs,
                progressMs =
                    (progressMs ?: previous?.progressMs ?: 0L).coerceIn(
                        0L,
                        durationMs.coerceAtLeast(1L)
                    ),
                durationMs = durationMs.coerceAtLeast(1L),
                summaryPath = summaryPath ?: previous?.summaryPath,
                noteStatus =
                    noteStatus
                        ?: previous?.noteStatus
                        ?: if (summaryPath.isNullOrBlank()) LearningNoteStatus.NotStarted
                        else LearningNoteStatus.Ready,
                summaryUpdatedAtMs = summaryUpdatedAtMs ?: previous?.summaryUpdatedAtMs ?: 0L,
            )
        return (records.filterNot { it.videoId == videoId } + updated).sortedByDescending {
            it.lastWatchedAtMs
        }
    }

    private const val LEGACY_FIELD_COUNT = 7
    private const val FIELD_COUNT = 9
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
