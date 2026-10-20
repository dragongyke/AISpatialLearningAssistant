package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningInteractionTrackerTest {
    @Test
    fun beginningANewInteractionInvalidatesThePreviousOne() {
        val tracker = LearningInteractionTracker()
        val first = tracker.begin()
        val second = tracker.begin()

        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }

    @Test
    fun invalidatingTheTrackerRejectsTheActiveInteraction() {
        val tracker = LearningInteractionTracker()
        val active = tracker.begin()

        tracker.invalidate()

        assertFalse(tracker.isCurrent(active))
    }
}
