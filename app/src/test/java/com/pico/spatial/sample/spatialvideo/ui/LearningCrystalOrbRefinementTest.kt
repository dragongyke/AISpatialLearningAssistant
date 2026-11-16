package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbRefinementTest {
    @Test
    fun filamentsFlowAtRestAndGatherThroughoutBothThinkingStates() {
        repeat(100) { tick ->
            val time = tick / 10.0
            val idle = learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, time)
            assertTrue("Idle filaments must flow", idle.swirlSpeedDegrees > 0f)
            for (state in
                listOf(LearningVoiceControlState.Analyzing, LearningVoiceControlState.Answering)) {
                val thinking = learningCrystalOrbFrame(state, false, false, time)
                assertTrue(thinking.swirlSpeedDegrees >= idle.swirlSpeedDegrees * 4f)
                assertTrue(
                    "Streams must gather inward",
                    thinking.gatheringScale < idle.gatheringScale
                )
            }
        }
    }

    @Test
    fun counterRotatingDetailsCompleteWholeTurnsBeforeTheirClockWraps() {
        for (stream in LearningCrystalOrbStream.entries) {
            assertEquals(
                0f,
                (LearningCrystalOrbMotion.swirlRepeatDegrees * stream.speedRatio) % 360f,
                0.001f
            )
        }
    }

    @Test
    fun waitingAndSpeakingHaveDifferentMotionEvenWithoutAudio() {
        for (state in LearningVoiceControlState.entries) {
            val frame = learningCrystalOrbFrame(state, false, false, 0.0)
            val waiting =
                state == LearningVoiceControlState.Analyzing ||
                    state == LearningVoiceControlState.Answering
            assertEquals(waiting, frame.gatheringScale < 1f)
            assertEquals(
                state != LearningVoiceControlState.Unavailable,
                frame.swirlSpeedDegrees > 0f
            )
            assertEquals(state == LearningVoiceControlState.Speaking, frame.speakingScale < 1f)
            assertTrue(frame.veilOpacity > 0f && frame.threadLight > 0f)
        }
        val quiet =
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 0f)
        val loud =
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 1f)
        assertTrue(loud.speakingScale > quiet.speakingScale)
        assertTrue(loud.threadLight > quiet.threadLight)
        assertTrue(loud.veilOpacity > quiet.veilOpacity)
        val breathing =
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.45)
        assertTrue(breathing.sweepScale > quiet.sweepScale)
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
                assertTrue(frame.veilOpacity in 0f..1f)
                assertTrue(frame.gatheringScale in 0.75f..1.1f)
                assertTrue(frame.speakingScale in 0.8f..1.16f)
                assertTrue(kotlin.math.abs(frame.veilOpacity - previous.veilOpacity) < 0.02f)
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
                    "Thinking filaments must be unmistakably active",
                    waiting.swirlSpeedDegrees >= idle.swirlSpeedDegrees * 4f
                )
            }
        }
    }
}
