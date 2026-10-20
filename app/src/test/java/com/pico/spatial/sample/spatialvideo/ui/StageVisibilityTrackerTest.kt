package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageVisibilityTrackerTest {
    @Test
    fun aVisitKeepsItsOwnerWhenSharedSessionStateChanges() {
        val tracker = StageVisibilityTracker()
        tracker.shouldEndSession(true, 1L)
        tracker.shouldEndSession(true, 2L)
        org.junit.Assert.assertEquals(1L, tracker.ownerSessionId)
        assertTrue(tracker.shouldEndSession(false, 2L))
        org.junit.Assert.assertEquals(1L, tracker.ownerSessionId)
        tracker.shouldEndSession(true, 2L)
        org.junit.Assert.assertEquals(2L, tracker.ownerSessionId)
    }

    @Test
    fun endsSessionOnlyAfterAnOpenedStageLeavesOnstageState() {
        val tracker = StageVisibilityTracker()

        assertFalse(tracker.shouldEndSession(isOnstage = false))
        assertFalse(tracker.shouldEndSession(isOnstage = true))
        assertTrue(tracker.shouldEndSession(isOnstage = false))
    }

    @Test
    fun tracksEachStageVisitIndependently() {
        val tracker = StageVisibilityTracker()

        assertFalse(tracker.shouldEndSession(isOnstage = true))
        assertTrue(tracker.shouldEndSession(isOnstage = false))
        assertFalse(tracker.shouldEndSession(isOnstage = false))

        assertFalse(tracker.shouldEndSession(isOnstage = true))
        assertTrue(tracker.shouldEndSession(isOnstage = false))
    }
}
