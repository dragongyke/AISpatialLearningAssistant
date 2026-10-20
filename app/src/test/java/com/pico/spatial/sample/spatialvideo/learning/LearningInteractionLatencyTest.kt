package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningInteractionLatencyTest {
    @Test
    fun firstAudibleReplyExcludesTimeSpentAskingTheQuestion() {
        var now = 0L
        val tracker = LearningInteractionLatencyTracker { now }
        tracker.begin()
        now = 8_000
        tracker.markAsrFinal()
        tracker.beginResponse()
        now = 8_800
        tracker.beginTts()
        now = 10_000
        tracker.markTtsFirstAudio()
        now = 12_000
        tracker.markTtsFirstAudio()
        assertEquals(2_000L, tracker.snapshot().responseFirstAudioMs)
        assertEquals(1_200L, tracker.snapshot().ttsFirstAudioMs)
        tracker.begin()
        assertEquals(null, tracker.snapshot().responseFirstAudioMs)
    }

    @Test
    fun recordsFirstResultAndStageDurationsAgainstAMonotonicClock() {
        var now = 1_000L
        val tracker = LearningInteractionLatencyTracker { now }
        tracker.begin()

        now = 1_320L
        tracker.markAsrPartial()
        now = 2_100L
        tracker.markAsrFinal()
        tracker.beginVision()
        now = 2_400L
        tracker.finishVision()
        tracker.beginLlm()
        now = 3_050L
        tracker.markLlmFirstToken()
        tracker.beginTts()
        now = 3_600L
        tracker.markTtsFirstAudio()
        now = 4_000L
        tracker.finish()

        assertEquals(
            LearningInteractionLatency(
                asrFirstPartialMs = 320L,
                asrFinalMs = 1_100L,
                visionMs = 300L,
                llmFirstTokenMs = 650L,
                ttsFirstAudioMs = 550L,
                totalMs = 3_000L,
            ),
            tracker.snapshot(),
        )
    }

    @Test
    fun preservesOnlyTheFirstPartialTokenAndAudioMeasurements() {
        var now = 0L
        val tracker = LearningInteractionLatencyTracker { now }
        tracker.begin()
        now = 100L
        tracker.markAsrPartial()
        now = 300L
        tracker.markAsrPartial()
        tracker.beginLlm()
        now = 500L
        tracker.markLlmFirstToken()
        now = 800L
        tracker.markLlmFirstToken()
        tracker.beginTts()
        now = 1_000L
        tracker.markTtsFirstAudio()
        now = 1_500L
        tracker.markTtsFirstAudio()

        assertEquals(100L, tracker.snapshot().asrFirstPartialMs)
        assertEquals(200L, tracker.snapshot().llmFirstTokenMs)
        assertEquals(200L, tracker.snapshot().ttsFirstAudioMs)
    }
}
