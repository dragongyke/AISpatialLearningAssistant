package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningSpatialLayoutCodecTest {
    @Test
    fun preBlackboardLayoutDocksVideoAndResetsVoiceButKeepsSidePanelPositions() {
        val decoded =
            LearningSpatialLayoutCodec.decode(
                "5|0.6;0.3;-0.3;0;-2.2;0;0.6;24;0;-1.2;0.12;0;2.3;0;0.6;-24;0.7;-0.7;0.3;0"
            )
        assertEquals(LearningSpatialLayout.Default.video, decoded.video)
        assertEquals(LearningSpatialLayout.Default.voiceControl, decoded.voiceControl)
        assertEquals(-2.2f, decoded.outline.x, 0.0001f)
        assertEquals(2.3f, decoded.aiCoach.x, 0.0001f)
    }

    @Test
    fun savedVideoCannotMoveBehindTheNewLearningWall() {
        val pose =
            LearningSpatialLayoutPolicy.constrain(
                LearningPanel.Video,
                SpatialPanelPose(0f, 0f, -0.4f, 0f)
            )
        assertEquals(0f, pose.z, 0.0001f)
    }

    @Test
    fun roundTripsUserAdjustedPanelPoses() {
        val layout =
            LearningSpatialLayout(
                video = SpatialPanelPose(0.1f, 0.2f, -0.1f, 2f),
                outline = SpatialPanelPose(-1.4f, 1.5f, -1.95f, 12f),
                toolbar = SpatialPanelPose(0f, 0.45f, -2.05f, 0f),
                aiCoach = SpatialPanelPose(1.5f, 1.5f, -1.9f, -12f),
                voiceControl = SpatialPanelPose(0.2f, -0.8f, 0.9f, 0f),
            )

        assertEquals(
            layout,
            LearningSpatialLayoutCodec.decode(LearningSpatialLayoutCodec.encode(layout))
        )
    }

    @Test
    fun invalidSavedLayoutFallsBackToDefault() {
        assertEquals(
            LearningSpatialLayout.Default,
            LearningSpatialLayoutCodec.decode("not-a-layout")
        )
    }

    @Test
    fun migratesThePreviousThreePanelLayout() {
        val decoded =
            LearningSpatialLayoutCodec.decode(
                "4|-1.4;1.5;-1.95;12.0;0.0;0.45;-2.05;0.0;1.5;1.5;-1.9;-12.0"
            )

        assertEquals(-1.4f, decoded.outline.x, 0.0001f)
        assertEquals(0.45f, decoded.toolbar.y, 0.0001f)
        assertEquals(1.5f, decoded.aiCoach.x, 0.0001f)
        assertEquals(LearningSpatialLayout.Default.video, decoded.video)
        assertEquals(LearningSpatialLayout.Default.voiceControl, decoded.voiceControl)
    }
}
