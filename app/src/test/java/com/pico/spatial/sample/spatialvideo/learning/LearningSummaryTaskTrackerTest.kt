package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSummaryTaskTrackerTest {
    @Test
    fun exitingAnUnchangedCompletedCourseReusesPendingAndCompletedSummary() {
        val tasks = LearningSummaryTaskTracker()
        val revision = LearningSummaryRevision(1L, 1000L, listOf("question-1"))
        val task = tasks.begin("course", revision)
        assertFalse(tasks.needsUpdate("course", revision))
        tasks.finish(task)
        assertFalse(tasks.needsUpdate("course", revision))
        assertTrue(
            tasks.needsUpdate(
                "course",
                revision.copy(questionIds = listOf("question-1", "question-2"))
            )
        )
        assertTrue(tasks.needsUpdate("course", revision.copy(sessionId = 2L)))
    }

    @Test
    fun failedOrCancelledSummaryCanBeRequestedAgain() {
        val tasks = LearningSummaryTaskTracker()
        val revision = LearningSummaryRevision(1L, 1000L, emptyList())
        tasks.finish(tasks.begin("course", revision), succeeded = false)
        assertTrue(tasks.needsUpdate("course", revision))
        tasks.begin("course", revision)
        tasks.cancel("course")
        assertTrue(tasks.needsUpdate("course", revision))
    }

    @Test
    fun cancellingBeforeCoroutineEntryDoesNotLeaveARunningTask() {
        val tasks = LearningSummaryTaskTracker()
        tasks.begin("course")
        tasks.cancel("course")
        assertFalse(tasks.isRunning("course"))
    }

    @Test
    fun noteWriteInvalidationCannotKeepSummaryBusy() {
        val tasks = LearningSummaryTaskTracker()
        val writes = LearningNoteWriteCoordinator()
        val task = tasks.begin("course")
        val oldWrite = writes.begin("course")
        writes.begin("course")
        assertFalse(writes.isCurrent(oldWrite))
        tasks.finish(task)
        assertFalse(tasks.isRunning("course"))
    }

    @Test
    fun cancelledOlderTaskCannotClearItsReplacement() {
        val tasks = LearningSummaryTaskTracker()
        val old = tasks.begin("course")
        val current = tasks.begin("course")
        tasks.finish(old)
        assertTrue(tasks.isRunning("course"))
        tasks.finish(current)
        assertFalse(tasks.isRunning("course"))
    }
}
