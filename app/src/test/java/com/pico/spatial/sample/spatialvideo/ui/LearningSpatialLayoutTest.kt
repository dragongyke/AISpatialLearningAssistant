package com.pico.spatial.sample.spatialvideo.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSpatialLayoutTest {
    @Test
    fun defaultPanelsUseVideoLocalCoordinatesAndKeepToolbarBelowIt() {
        val layout = LearningSpatialLayout.Default

        assertEquals(LearningStageLocalPose.Video, layout.video)

        assertEquals(-1.95f, layout.outline.x, 0.0001f)
        assertEquals(0f, layout.outline.y, 0.0001f)
        assertEquals(0.60f, layout.outline.z, 0.0001f)
        assertEquals(24f, layout.outline.yawDegrees, 0.0001f)

        assertEquals(0f, layout.toolbar.x, 0.0001f)
        assertEquals(-1.18f, layout.toolbar.y, 0.0001f)
        assertEquals(0.12f, layout.toolbar.z, 0.0001f)

        assertEquals(1.95f, layout.aiCoach.x, 0.0001f)
        assertEquals(0f, layout.aiCoach.y, 0.0001f)
        assertEquals(0.60f, layout.aiCoach.z, 0.0001f)
        assertEquals(-24f, layout.aiCoach.yawDegrees, 0.0001f)

        assertEquals(LearningStageLocalPose.VoiceControl, layout.voiceControl)
    }

    @Test
    fun panelsCanBeDraggedBelowEyeLevel() {
        val moved =
            LearningSpatialLayout.Default.move(
                panel = LearningPanel.Toolbar,
                deltaX = 0f,
                deltaY = -0.25f,
                deltaZ = 0f,
            )

        assertEquals(-1.43f, moved.toolbar.y, 0.0001f)
    }

    @Test
    fun stagePanelsUseExplicitRequestedSizes() {
        assertEquals(900.dp, LearningStageDimensions.OutlineWidth)
        assertEquals(900.dp, LearningStageDimensions.AiCoachWidth)
        assertEquals(1600.dp, LearningStageDimensions.SidePanelHeight)
        assertEquals(1770.dp, LearningStageDimensions.ToolbarWidth)
        assertEquals(420.dp, LearningStageDimensions.ToolbarHeight)
        assertEquals(2048.dp, LearningStageDimensions.SubtitleWidth)
        assertEquals(300.dp, LearningStageDimensions.SubtitleHeight)
        assertEquals(240.dp, LearningStageDimensions.VoiceControlSize)
        assertEquals(120.dp, LearningStageDimensions.DragBarWidth)
        assertEquals(56.dp, LearningStageDimensions.DragBarHeight)
        assertEquals(1.5f, LearningStageDimensions.PanelScale, 0.0001f)
        assertEquals(1.4f, LearningStageDimensions.PanelFontScale, 0.0001f)
        assertEquals(
            9f / 16f,
            LearningStageDimensions.OutlineWidth.value /
                LearningStageDimensions.SidePanelHeight.value,
            0.0001f
        )
        assertTrue(LearningStageDimensions.SidePanelHeight.value <= 2048f)
        assertTrue(LearningStageDimensions.ToolbarWidth.value <= 2048f)
    }

    @Test
    fun sanitizingSavedLayoutKeepsPanelsInTheirVisualLanes() {
        val unsafe =
            LearningSpatialLayout.Default.copy(
                outline = SpatialPanelPose(8f, 4f, -9f, 0f),
                toolbar = SpatialPanelPose(-8f, 4f, -9f, 12f),
                aiCoach = SpatialPanelPose(-8f, 4f, -9f, 0f),
            )

        val sanitized = unsafe.sanitized()

        assertTrue(sanitized.outline.x < 0f)
        assertTrue(sanitized.aiCoach.x > 0f)
        assertTrue(sanitized.toolbar.y < 0f)
        assertEquals(0f, sanitized.toolbar.yawDegrees, 0.0001f)
    }
}
