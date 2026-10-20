package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEnvironmentModeTest {
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
