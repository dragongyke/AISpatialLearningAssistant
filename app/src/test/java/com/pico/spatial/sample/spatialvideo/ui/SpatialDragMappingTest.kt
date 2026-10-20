package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialDragMappingTest {
    @Test
    fun standaloneHandleScaleDoesNotScaleItsVideoOrVoicePanelOffset() {
        val pose =
            SpatialDragBarGeometry.localPose(
                panelHeightMeters = 1.6f,
                gapMeters = 0.04f,
                barHeightMeters = 0.1f,
                forwardOffsetMeters = 0.035f,
                handleScale = 1.5f,
            )
        assertEquals(-0.935f, pose.y, 0.0001f)
        assertEquals(0.0525f, pose.z, 0.0001f)
    }

    @Test
    fun lowVideoLayoutDoesNotMakeToolbarJumpDuringHorizontalDragOrSave() {
        val geometry = LearningStageGeometry(0.0008f)
        val layout =
            geometry.alignPanels(
                LearningSpatialLayout.Default.copy(
                    video = LearningSpatialLayout.Default.video.copy(y = -0.7f)
                )
            )
        val state = LearningPanelDragState(layout)
        state.setFloorLimits(geometry.minimumCenterY(geometry.rootHeight(1.2394f, layout)))
        val start = state.pose(LearningPanel.Toolbar)
        val moved = state.moveFrom(LearningPanel.Toolbar, start, SpatialDragDelta(0.2f, 0f, 0f))
        assertEquals(start.y, moved.y, 0.0001f)
        assertEquals(start.y, state.finish(LearningPanel.Toolbar).y, 0.0001f)
        val saved =
            LearningSpatialLayoutCodec.decode(
                    LearningSpatialLayoutCodec.encode(state.snapshot().sanitized())
                )
                .sanitized()
        assertEquals(start.y, saved.toolbar.y, 0.0001f)
    }

    @Test
    fun draggingSnappingAndRestoringCannotPutFootersBelowFloor() {
        val geometry = LearningStageGeometry(0.0008f)
        val layout = geometry.alignPanels(LearningSpatialLayout.Default)
        val rootY = geometry.rootHeight(1.2394f, layout)
        val state = LearningPanelDragState(layout)
        val limits = geometry.minimumCenterY(rootY)
        state.setFloorLimits(limits)
        for (panel in LearningPanel.entries) {
            state.moveFrom(panel, state.pose(panel), SpatialDragDelta(0f, -10f, 0f))
            assertTrue(state.pose(panel).y >= limits.getValue(panel))
            assertTrue(state.finish(panel).y >= limits.getValue(panel))
        }
        state.replace(layout.copy(toolbar = layout.toolbar.copy(y = -10f)))
        assertTrue(state.pose(LearningPanel.Toolbar).y >= limits.getValue(LearningPanel.Toolbar))
    }

    @Test
    fun resolvesEveryDragUpdateFromTheOriginalPanelPose() {
        val state = LearningPanelDragState(LearningSpatialLayout.Default)
        val startPose = state.pose(LearningPanel.Toolbar)

        state.moveFrom(
            LearningPanel.Toolbar,
            startPose,
            SpatialDragDelta(0.1f, -0.1f, 0f),
        )
        val moved =
            state.moveFrom(
                LearningPanel.Toolbar,
                startPose,
                SpatialDragDelta(0.2f, -0.2f, 0.1f),
            )

        assertEquals(0.2f, moved.x, 0.0001f)
        assertEquals(-1.38f, moved.y, 0.0001f)
        assertEquals(0.22f, moved.z, 0.0001f)
        assertEquals(moved, state.snapshot().toolbar)
    }

    @Test
    fun accumulatesPointerPixelsFromTheImmutableGrabPoint() {
        val session =
            SpatialPanelDragSession(
                panel = LearningPanel.Video,
                startViewPoint = SpatialDragPoint(100f, 200f, 10f),
                startStagePoint = SpatialDragPoint(0f, 0f, 0f),
                startPanelPose = LearningSpatialLayout.Default.video,
            )

        session.addViewDelta(SpatialDragDelta(20f, -10f, 2f))
        val current = session.addViewDelta(SpatialDragDelta(5f, 4f, -1f))

        assertEquals(125f, current.x, 0.0001f)
        assertEquals(194f, current.y, 0.0001f)
        assertEquals(11f, current.z, 0.0001f)
    }

    @Test
    fun resolvesStageMovementFromTheOriginalGrabPoint() {
        val session =
            SpatialPanelDragSession(
                panel = LearningPanel.VoiceControl,
                startViewPoint = SpatialDragPoint(0f, 0f, 0f),
                startStagePoint = SpatialDragPoint(1f, 2f, 3f),
                startPanelPose = LearningSpatialLayout.Default.voiceControl,
            )

        val delta = session.stageDelta(SpatialDragPoint(1.25f, 1.9f, 3.4f))

        assertEquals(0.25f, delta.x, 0.0001f)
        assertEquals(-0.1f, delta.y, 0.0001f)
        assertEquals(0.4f, delta.z, 0.0001f)
    }

    @Test
    fun placesTheGrabBarBelowThePanel() {
        assertEquals(
            -0.89f,
            SpatialDragBarGeometry.centerY(
                panelHeightMeters = 1.6f,
                gapMeters = 0.04f,
                barHeightMeters = 0.1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun dragBarLocalPoseIsCenteredOnItsParentPanel() {
        val pose =
            SpatialDragBarGeometry.localPose(
                panelHeightMeters = 1.6f,
                gapMeters = 0.04f,
                barHeightMeters = 0.1f,
                forwardOffsetMeters = 0.035f,
            )

        assertEquals(0f, pose.x, 0.0001f)
        assertEquals(-0.89f, pose.y, 0.0001f)
        assertEquals(0.035f, pose.z, 0.0001f)
    }

    @Test
    fun smoothsNoisyDragDeltasBeforeMovingThePanel() {
        val filter = SpatialDragSmoothingFilter(factor = 0.25f)

        assertEquals(0.25f, filter.apply(SpatialDragDelta(1f, 0f, 0f)).x, 0.0001f)
        assertEquals(0.4375f, filter.apply(SpatialDragDelta(1f, 0f, 0f)).x, 0.0001f)
    }

    @Test
    fun snapsToolbarBackToItsRecommendedLane() {
        val state = LearningPanelDragState(LearningSpatialLayout.Default)
        val start = state.pose(LearningPanel.Toolbar)
        state.moveFrom(
            panel = LearningPanel.Toolbar,
            startPose = start,
            delta = SpatialDragDelta(0.06f, 0.04f, -0.05f),
        )

        val snapped = state.finish(LearningPanel.Toolbar)

        assertEquals(LearningSpatialLayout.Default.toolbar.x, snapped.x, 0.0001f)
        assertEquals(LearningSpatialLayout.Default.toolbar.y, snapped.y, 0.0001f)
        assertEquals(LearningSpatialLayout.Default.toolbar.z, snapped.z, 0.0001f)
    }

    @Test
    fun keepsSidePanelsInTheirOwnNonOverlappingLanesAndFacingTheViewer() {
        val state = LearningPanelDragState(LearningSpatialLayout.Default)
        val start = state.pose(LearningPanel.Outline)

        val moved =
            state.moveFrom(
                panel = LearningPanel.Outline,
                startPose = start,
                delta = SpatialDragDelta(5f, 5f, 5f),
            )

        assertEquals(-0.95f, moved.x, 0.0001f)
        assertEquals(0.8f, moved.y, 0.0001f)
        assertEquals(1.1f, moved.z, 0.0001f)
        assertTrue(moved.yawDegrees > 0f)
    }
}
