package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PersistedLearningSession(
    val videoId: String,
    val progressMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val questionAnswers: List<LearningQuestionAnswer> = emptyList(),
    val summaryPath: String? = null,
    val updatedAtMs: Long = 0L,
    val subtitleOffsetMs: Long = 0L,
    val teachingMode: LearningTeachingMode = LearningTeachingMode.Concise,
)

class LearningSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(videoId: String): PersistedLearningSession =
        LearningSessionCodec.decode(
            preferences.getString(videoId.preferenceKey(), "").orEmpty(),
            fallbackVideoId = videoId,
        )

    fun save(session: PersistedLearningSession) {
        preferences
            .edit()
            .putString(session.videoId.preferenceKey(), LearningSessionCodec.encode(session))
            .apply()
    }

    private fun String.preferenceKey(): String = "session_${urlEncode()}"

    private companion object {
        const val PREFERENCES_NAME = "ai_learning_sessions_v1"
    }
}

object LearningSessionCodec {
    fun encode(session: PersistedLearningSession): String =
        buildString {
                appendLine(
                    listOf(
                            CURRENT_SESSION_MARKER,
                            session.videoId,
                            session.progressMs.toString(),
                            session.playbackSpeed.toString(),
                            session.summaryPath.orEmpty(),
                            session.updatedAtMs.toString(),
                            session.subtitleOffsetMs.toString(),
                            session.teachingMode.name,
                        )
                        .joinToString(SEPARATOR) { it.urlEncode() }
                )
                session.questionAnswers.forEach { answer ->
                    appendLine(
                        listOf(
                                ANSWER_MARKER,
                                answer.id,
                                answer.timestampMs.toString(),
                                answer.questionText,
                                answer.answerText,
                                answer.contextStartMs.toString(),
                                answer.contextEndMs.toString(),
                                answer.createdAtMs.toString(),
                            )
                            .joinToString(SEPARATOR) { it.urlEncode() }
                    )
                }
            }
            .trimEnd()

    fun decode(value: String, fallbackVideoId: String = ""): PersistedLearningSession {
        if (value.isBlank()) return PersistedLearningSession(videoId = fallbackVideoId)
        return runCatching {
                val lines = value.lineSequence().filter { it.isNotBlank() }.toList()
                val header = lines.first().split(SEPARATOR).map { it.urlDecode() }
                val isCurrent =
                    header.size == CURRENT_SESSION_FIELD_COUNT &&
                        header[0] == CURRENT_SESSION_MARKER
                val isLegacy =
                    header.size == LEGACY_SESSION_FIELD_COUNT && header[0] == LEGACY_SESSION_MARKER
                require(isCurrent || isLegacy)
                val videoId = header[1].ifBlank { fallbackVideoId }
                val answers =
                    lines.drop(1).mapNotNull { line ->
                        val fields = line.split(SEPARATOR).map { it.urlDecode() }
                        if (fields.size != ANSWER_FIELD_COUNT || fields[0] != ANSWER_MARKER) {
                            return@mapNotNull null
                        }
                        LearningQuestionAnswer(
                            id = fields[1],
                            timestampMs = fields[2].toLongOrNull() ?: return@mapNotNull null,
                            questionText = fields[3],
                            answerText = fields[4],
                            contextStartMs = fields[5].toLongOrNull() ?: return@mapNotNull null,
                            contextEndMs = fields[6].toLongOrNull() ?: return@mapNotNull null,
                            createdAtMs = fields[7].toLongOrNull() ?: return@mapNotNull null,
                        )
                    }
                PersistedLearningSession(
                    videoId = videoId,
                    progressMs = header[2].toLong().coerceAtLeast(0L),
                    playbackSpeed = header[3].toFloat().coerceIn(0.5f, 2f),
                    questionAnswers = answers,
                    summaryPath = header[4].ifBlank { null },
                    updatedAtMs = header[5].toLong(),
                    subtitleOffsetMs =
                        if (isCurrent) header[6].toLongOrNull()?.coerceIn(-5_000L, 5_000L) ?: 0L
                        else 0L,
                    teachingMode =
                        if (isCurrent) {
                            runCatching { LearningTeachingMode.valueOf(header[7]) }
                                .getOrDefault(LearningTeachingMode.Concise)
                        } else {
                            LearningTeachingMode.Concise
                        },
                )
            }
            .getOrElse { PersistedLearningSession(videoId = fallbackVideoId) }
    }

    private const val CURRENT_SESSION_MARKER = "session-v2"
    private const val LEGACY_SESSION_MARKER = "session-v1"
    private const val ANSWER_MARKER = "qa"
    private const val SEPARATOR = "\t"
    private const val CURRENT_SESSION_FIELD_COUNT = 8
    private const val LEGACY_SESSION_FIELD_COUNT = 6
    private const val ANSWER_FIELD_COUNT = 8
}

object LearningSummaryNaming {
    fun fileName(videoId: String): String = "${videoId.sanitizedIdentifier()}-learning-summary.md"
}

private fun String.sanitizedIdentifier(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(80).ifBlank { "video" }

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
