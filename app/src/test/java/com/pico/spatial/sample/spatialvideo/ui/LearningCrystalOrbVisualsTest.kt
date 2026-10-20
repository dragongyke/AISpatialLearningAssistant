package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbVisualsTest {
    @Test
    fun everyVoiceStateHasADistinctVisualSignature() {
        val frames =
            LearningVoiceControlState.entries.map { learningCrystalOrbFrame(it, false, false, 0.0) }
        assertEquals(6, frames.map { it.tint }.distinct().size)
        assertTrue(frames.all { it.glow > 0f })
    }

    @Test
    fun gazeBrightensAndPressCompressesTheOrbWithoutChangingItsPose() {
        val idle = learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, 0.0)
        val hover = learningCrystalOrbFrame(LearningVoiceControlState.Ready, true, false, 0.0)
        val pressed = learningCrystalOrbFrame(LearningVoiceControlState.Ready, true, true, 0.0)
        assertTrue(hover.glow > idle.glow)
        assertTrue(hover.visualScale > idle.visualScale)
        assertTrue(pressed.visualScale < idle.visualScale)
    }

    @Test
    fun audioOnlyModulatesListeningAndSpeaking() {
        for (state in LearningVoiceControlState.entries) {
            val quiet = learningCrystalOrbFrame(state, false, false, 0.0, 0f)
            val loud = learningCrystalOrbFrame(state, false, false, 0.0, 1f)
            if (
                state == LearningVoiceControlState.Listening ||
                    state == LearningVoiceControlState.Speaking
            ) {
                assertTrue(loud.coreScale > quiet.coreScale)
                assertTrue(loud.glow > quiet.glow)
            } else {
                assertEquals(quiet, loud)
            }
        }
    }

    @Test
    fun animationIsRestrainedAndUnavailableIsStill() {
        val start = learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, 0.0)
        val later = learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, 0.9)
        assertNotEquals(start.coreScale, later.coreScale)
        assertEquals(
            learningCrystalOrbFrame(LearningVoiceControlState.Unavailable, false, false, 0.0),
            learningCrystalOrbFrame(LearningVoiceControlState.Unavailable, false, false, 10.0),
        )
        for (state in LearningVoiceControlState.entries) {
            repeat(200) { tick ->
                val frame = learningCrystalOrbFrame(state, true, true, tick / 10.0, 1f)
                assertTrue(frame.coreScale in 0.85f..1.3f)
                assertTrue(frame.visualScale in 0.9f..1.06f)
                assertTrue(frame.shellOpacity in 0.08f..0.35f)
                assertTrue(frame.orbitSpeedDegrees in 0f..30f)
                assertTrue(frame.glow in 0f..1f)
                assertTrue(
                    LearningCrystalOrbGeometry.maxVisualRadiusMeters * frame.visualScale < 0.2f
                )
            }
        }
    }

    @Test
    fun invalidAnimationInputsAreSanitized() {
        val safe =
            learningCrystalOrbFrame(LearningVoiceControlState.Listening, false, false, 0.0, 0f)
        for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            assertEquals(
                safe,
                learningCrystalOrbFrame(
                    LearningVoiceControlState.Listening,
                    false,
                    false,
                    time,
                    Float.NaN
                )
            )
        }
        assertEquals(
            safe,
            learningCrystalOrbFrame(LearningVoiceControlState.Listening, false, false, 0.0, -5f),
        )
        assertEquals(
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 1f),
            learningCrystalOrbFrame(LearningVoiceControlState.Speaking, false, false, 0.0, 8f),
        )
        assertTrue(
            learningCrystalOrbFrame(LearningVoiceControlState.Ready, false, false, Double.MAX_VALUE)
                .coreScale
                .isFinite()
        )
    }

    @Test
    fun colliderCoversTheCrystalAndItsSmallBaseInsideTheLocalEnvelope() {
        assertTrue(LearningCrystalOrbGeometry.shellRadiusMeters in 0.115f..0.125f)
        assertTrue(
            LearningCrystalOrbGeometry.colliderRadiusMeters >=
                LearningCrystalOrbGeometry.maxVisualRadiusMeters * 1.06f
        )
        assertTrue(LearningCrystalOrbGeometry.colliderRadiusMeters < 0.2f)
    }
}
