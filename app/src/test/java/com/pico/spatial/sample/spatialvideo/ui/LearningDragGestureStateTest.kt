package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.*
import org.junit.Test

class LearningDragGestureStateTest {
    private val layout = LearningPanelDragState(LearningSpatialLayout.Default)
    private val gesture = LearningDragGestureState(layout)
    private val origin = SpatialDragPoint(0f, 0f, 0f)

    @Test
    fun repeatedDragsReleaseCaptureForEveryPanel() {
        for (panel in LearningPanel.entries) {
            val owner = Any()
            repeat(3) { index ->
                val before = layout.pose(panel)
                assertTrue(gesture.begin(owner, index.toLong(), panel, origin))
                val direction = if (panel == LearningPanel.Outline) -1f else 1f
                val moved =
                    gesture.move(
                        owner,
                        index.toLong(),
                        SpatialDragPoint(direction * 0.6f, 0.2f, 0f)
                    )
                assertNotNull(moved)
                assertNotEquals(before, moved!!.second)
                assertNotNull(gesture.end(owner, index.toLong()))
                assertFalse(gesture.owns(owner, index.toLong()))
            }
        }
    }

    @Test
    fun cancelledInputCanImmediatelyStartAnotherDrag() {
        val owner = Any()
        assertTrue(gesture.begin(owner, 1, LearningPanel.Video, origin))
        gesture.cancel(owner)
        assertTrue(gesture.begin(owner, 2, LearningPanel.Toolbar, origin))
        assertNotNull(gesture.move(owner, 2, SpatialDragPoint(0.6f, 0f, 0f)))
        assertNotNull(gesture.end(owner, 2))
    }

    @Test
    fun anotherPointerOrDetachedHandleCannotStealOrReleaseCapture() {
        val first = Any()
        val second = Any()
        assertTrue(gesture.begin(first, 1, LearningPanel.Video, origin))
        assertFalse(gesture.begin(second, 2, LearningPanel.AiCoach, origin))
        gesture.cancel(second)
        assertNull(gesture.end(first, 2))
        assertTrue(gesture.owns(first, 1))
        assertNull(gesture.move(second, 2, SpatialDragPoint(0.5f, 0f, 0f)))
        assertNotNull(gesture.move(first, 1, SpatialDragPoint(0.5f, 0f, 0f)))
        gesture.end(first, 1)
        assertTrue(gesture.begin(second, 2, LearningPanel.AiCoach, origin))
    }

    @Test
    fun tapDoesNotSnapOrReportALayoutChange() {
        val owner = Any()
        assertTrue(gesture.begin(owner, 1, LearningPanel.Toolbar, origin))
        assertNull(gesture.end(owner, 1))
        assertEquals(LearningSpatialLayout.Default, layout.snapshot())
        assertTrue(gesture.begin(owner, 2, LearningPanel.Toolbar, origin))
    }
}
