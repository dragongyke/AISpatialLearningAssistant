package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbRefinementTest {
    @Test
    fun counterRotatingDetailsCompleteWholeTurnsBeforeTheirClockWraps() {
        for (ratio in listOf(1f, -0.6f, 0.8f, -0.72f)) {
            assertEquals(0f, (LearningCrystalOrbMotion.orbitRepeatDegrees * ratio) % 360f, 0.001f)
        }
        for (ratio in listOf(1f, -0.35f)) {
            assertEquals(0f, (LearningCrystalOrbMotion.swirlRepeatDegrees * ratio) % 360f, 0.001f)
        }
    }

    @Test
    fun waitingAndSpeakingHaveDifferentMotionEvenWithoutAudio() {
        for (state in LearningVoiceControlState.entries) {
            val frame = learningCrystalOrbFrame(state, false, false, 0.0)
            val waiting =
                state == LearningVoiceControlState.Analyzing ||
                    state == LearningVoiceControlState.Answering
            assertEquals(waiting, frame.waitingGlow > 0f)
            assertEquals(waiting, frame.swirlSpeedDegrees > 0f)
            assertEquals(state == LearningVoiceControlState.Speaking, frame.speakingGlow > 0f)
        }
        val quiet =
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 0f)
        val loud =
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 1f)
        assertTrue(loud.speakingScale > quiet.speakingScale)
        assertTrue(loud.speakingGlow > quiet.speakingGlow)
    }

    @Test
    fun gatheringAndSpeakingPulsesAreSmoothBoundedAndSanitizeTime() {
        for (state in LearningVoiceControlState.entries) {
            val start = learningCrystalOrbFrame(state, false, false, 0.0)
            for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY, -2.0)) {
                assertEquals(start, learningCrystalOrbFrame(state, false, false, time))
            }
            var previous = start
            repeat(1200) { tick ->
                val frame = learningCrystalOrbFrame(state, false, false, tick / 60.0)
                assertTrue(frame.waitingGlow in 0f..1f)
                assertTrue(frame.speakingGlow in 0f..1f)
                assertTrue(frame.gatheringScale in 0.75f..1.1f)
                assertTrue(frame.speakingScale in 0.8f..1.16f)
                assertTrue(kotlin.math.abs(frame.waitingGlow - previous.waitingGlow) < 0.02f)
                assertTrue(kotlin.math.abs(frame.gatheringScale - previous.gatheringScale) < 0.02f)
                assertTrue(kotlin.math.abs(frame.speakingScale - previous.speakingScale) < 0.02f)
                previous = frame
            }
        }
    }

    @Test
    fun waitingIsRecognizableWithoutRelyingOnTintOrHover() {
        for (state in
            listOf(LearningVoiceControlState.Analyzing, LearningVoiceControlState.Answering)) {
            repeat(360) { tick ->
                val time = tick / 30.0
                val idle =
                    learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, time)
                val waiting = learningCrystalOrbFrame(state, false, false, time)
                assertTrue(
                    "Waiting needs a consistently brighter heart",
                    waiting.glow > idle.glow + 0.1f
                )
                assertTrue(
                    "Waiting needs a larger heart",
                    waiting.coreScale > idle.coreScale + 0.02f
                )
                assertTrue(
                    "Waiting orbits must be unmistakably active",
                    waiting.orbitSpeedDegrees >= idle.orbitSpeedDegrees * 4f
                )
            }
        }
    }
}
