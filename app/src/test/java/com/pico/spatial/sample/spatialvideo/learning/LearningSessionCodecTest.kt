package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningSessionCodecTest {
    @Test
    fun roundTripsProgressSpeedQuestionsAndSummaryPath() {
        val answer =
            LearningQuestionAnswer(
                id = "qa-1",
                timestampMs = 42_000L,
                questionText = "Why is this convex?",
                answerText = "Because the defining inequality holds.",
                contextStartMs = 10_000L,
                contextEndMs = 60_000L,
                createdAtMs = 123L,
            )
        val session =
            PersistedLearningSession(
                videoId = "video-1",
                progressMs = 321_000L,
                playbackSpeed = 1.5f,
                questionAnswers = listOf(answer),
                summaryPath = "content://notes/video-1.md",
                updatedAtMs = 456L,
                subtitleOffsetMs = -500L,
                teachingMode = LearningTeachingMode.DeepDive,
            )

        assertEquals(session, LearningSessionCodec.decode(LearningSessionCodec.encode(session)))
    }

    @Test
    fun malformedSessionFallsBackToEmptyVideoSession() {
        assertEquals(
            PersistedLearningSession(videoId = "video-1"),
            LearningSessionCodec.decode("not-json", fallbackVideoId = "video-1"),
        )
    }

    @Test
    fun decodesLegacySessionWithoutNewPreferences() {
        val legacy = "session-v1\tvideo-1\t1000\t1.0\t\t456"

        val session = LearningSessionCodec.decode(legacy)

        assertEquals(0L, session.subtitleOffsetMs)
        assertEquals(LearningTeachingMode.Concise, session.teachingMode)
    }
}
