package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningBlackboardGeometryTest {
    @Test
    fun boardContainsVideoSubtitlesAndHandleButClearsToolbar() {
        for (metersPerDp in listOf(0.0008f, 0.001f)) {
            val stage = LearningStageGeometry(metersPerDp)
            val board = stage.blackboard()
            val layout = stage.alignPanels(LearningSpatialLayout.Default)
            assertTrue(board.innerWidth > 3168f * metersPerDp)
            assertTrue(board.top > stage.bodyHeight(LearningPanel.Video) / 2f)
            assertTrue(board.bottom < -stage.videoLowerExtent)
            val toolbarTop = layout.toolbar.y + stage.bodyHeight(LearningPanel.Toolbar) / 2f
            assertTrue(toolbarTop < board.bottom - board.frameWidth)
            assertTrue(board.surfaceZ < 0f)
            assertTrue(board.backZ > -LearningStagePoseResolver.LearningWallGapMeters)
        }
    }

    @Test
    fun dockingSavedLayoutReturnsVideoToFixedBoardWithoutErasingSidePreferences() {
        val moved =
            LearningSpatialLayout.Default.copy(
                video = SpatialPanelPose(0.6f, 0.2f, 0.3f, 0f),
                aiCoach = SpatialPanelPose(2.4f, 0.1f, 0.8f, -30f),
            )
        val restored = moved.dockedToBlackboard()
        assertEquals(LearningStageLocalPose.Video, restored.video)
        assertEquals(moved.aiCoach, restored.aiCoach)
        assertEquals(moved.voiceControl, restored.voiceControl)
    }
}
