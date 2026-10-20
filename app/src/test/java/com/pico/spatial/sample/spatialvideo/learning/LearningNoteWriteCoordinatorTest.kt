package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningNoteWriteCoordinatorTest {
    @Test
    fun staleRealtimeWriteCannotOverwriteANewerFinalOrEditedNote() {
        val coordinator = LearningNoteWriteCoordinator()
        val realtime = coordinator.begin("video")
        val finalOrEdit = coordinator.begin("video")

        assertFalse(coordinator.isCurrent(realtime))
        assertTrue(coordinator.isCurrent(finalOrEdit))
        assertNull(coordinator.commitIfCurrent(realtime) { "stale" })
        assertEquals("latest", coordinator.commitIfCurrent(finalOrEdit) { "latest" })
    }

    @Test
    fun revisionsAreIndependentForDifferentCourses() {
        val coordinator = LearningNoteWriteCoordinator()
        val firstCourse = coordinator.begin("first")
        coordinator.begin("second")

        assertEquals("first", coordinator.commitIfCurrent(firstCourse) { "first" })
    }
}
