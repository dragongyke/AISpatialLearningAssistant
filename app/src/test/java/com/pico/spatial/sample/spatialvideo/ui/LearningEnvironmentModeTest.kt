package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEnvironmentModeTest {
    @Test
    fun toggleLabelNamesTheDestinationNotTheCurrentEnvironment() {
        assertEquals(
            R.string.learning_view_reality,
            LearningEnvironmentMode.VirtualHall.toggleLabelRes
        )
        assertEquals(
            R.string.learning_return_lecture_hall,
            LearningEnvironmentMode.Reality.toggleLabelRes
        )
    }

    @Test
    fun startsInsideTheVirtualLectureHall() {
        assertEquals(LearningEnvironmentMode.VirtualHall, LearningEnvironmentModePolicy.initial)
        assertTrue(
            LearningEnvironmentModePolicy.showsVirtualHall(LearningEnvironmentModePolicy.initial)
        )
    }

    @Test
    fun togglesFromTheLectureHallToReality() {
        val mode = LearningEnvironmentModePolicy.toggle(LearningEnvironmentMode.VirtualHall)

        assertEquals(LearningEnvironmentMode.Reality, mode)
        assertFalse(LearningEnvironmentModePolicy.showsVirtualHall(mode))
    }

    @Test
    fun togglesFromRealityBackToTheLectureHall() {
        val mode = LearningEnvironmentModePolicy.toggle(LearningEnvironmentMode.Reality)

        assertEquals(LearningEnvironmentMode.VirtualHall, mode)
        assertTrue(LearningEnvironmentModePolicy.showsVirtualHall(mode))
    }
}
