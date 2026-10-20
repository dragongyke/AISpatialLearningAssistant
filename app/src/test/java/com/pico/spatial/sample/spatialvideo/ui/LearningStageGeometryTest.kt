package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStageGeometryTest {
    private val geometry = LearningStageGeometry(0.0008f)

    @Test
    fun floorLimitsUseOrbBoundsAndIncludeVideoHandle() {
        assertEquals(0.20f, geometry.lowerExtent(LearningPanel.VoiceControl), 0.0001f)
        assertEquals(0.9408f, geometry.lowerExtent(LearningPanel.Video), 0.0001f)
        val largerDensity = LearningStageGeometry(0.001f)
        assertEquals(1.017f, largerDensity.lowerExtent(LearningPanel.Video), 0.0001f)
    }

    @Test
    fun footerAndBodyShareOneCenteredAttachmentWithoutHalfPanelDrop() {
        val frame = SpatialPanelFrameGeometry(1.28f, 0.0224f, 0.0448f)
        assertEquals(-0.0336f, frame.surfaceY, 0.0001f)
        assertEquals(-0.6848f, frame.handleY, 0.0001f)
        assertEquals(0f, frame.surfaceY + (frame.gap + frame.handleHeight) / 2f, 0.0001f)
        assertTrue(frame.handleY + frame.handleHeight / 2f < -frame.bodyHeight / 2f)
    }

    @Test
    fun alignsSidePanelsToMovedVideoAndKeepsToolbarBelowSubtitles() {
        val saved =
            LearningSpatialLayout.Default.copy(
                video = LearningSpatialLayout.Default.video.copy(y = 0.3f),
                outline = LearningSpatialLayout.Default.outline.copy(y = -0.4f),
                aiCoach = LearningSpatialLayout.Default.aiCoach.copy(y = 0.7f),
            )
        val layout = geometry.alignPanels(saved)
        assertEquals(layout.video.y, layout.outline.y, 0.0001f)
        assertEquals(layout.video.y, layout.aiCoach.y, 0.0001f)
        assertTrue(
            layout.toolbar.y + geometry.bodyHeight(LearningPanel.Toolbar) / 2f <
                layout.video.y - geometry.videoLowerExtent
        )
    }

    @Test
    fun restoredLayoutUsesOriginalEyeHeightWithoutAccumulatingFloorLift() {
        val eyeHeight = 1.2394f
        val low =
            geometry.alignPanels(
                LearningSpatialLayout.Default.copy(
                    toolbar = LearningSpatialLayout.Default.toolbar.copy(y = -2.3f)
                )
            )
        val raised = geometry.rootHeight(eyeHeight, low)
        val restored =
            geometry.rootHeight(eyeHeight, geometry.alignPanels(LearningSpatialLayout.Default))
        assertTrue(restored < raised)
        assertEquals(1.8256f, restored, 0.0001f)
    }

    @Test
    fun toolbarClearsBlackboardFrameAndChalkTrayBelowVideo() {
        val layout = geometry.alignPanels(LearningSpatialLayout.Default)
        val toolbarTop = layout.toolbar.y + geometry.bodyHeight(LearningPanel.Toolbar) / 2f
        val boardBottom = -geometry.videoLowerExtent - 0.08f
        assertTrue(toolbarTop <= boardBottom - 0.07f)
    }

    @Test
    fun seatedAndStandingLayoutsKeepAllPanelFootersAboveTheFloor() {
        val layout = geometry.alignPanels(LearningSpatialLayout.Default)
        for (eyeHeight in listOf(0.8f, 1.2394f, 1.7f, 2.1f)) {
            val rootY = geometry.rootHeight(eyeHeight, layout)
            for (panel in LearningPanel.entries) {
                assertTrue(
                    "$panel at eye height $eyeHeight",
                    rootY + layout.pose(panel).y - geometry.lowerExtent(panel) >= 0.1199f
                )
            }
            assertTrue(rootY >= eyeHeight)
        }
    }
}
