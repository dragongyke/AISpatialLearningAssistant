package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStagePoseTest {
    @Test
    fun placesStageAtEyeHeightInFrontOfForwardFacingHead() {
        val result =
            LearningStagePoseResolver.resolve(
                StageHeadPose(x = 1f, y = 1.68f, z = 2f, yawDegrees = 0f)
            )

        assertEquals(1f, result.x, 0.0001f)
        assertEquals(1.68f, result.y, 0.0001f)
        assertEquals(2f - LearningStagePoseResolver.ViewingDistanceMeters, result.z, 0.0001f)
        assertEquals(0f, result.yawDegrees, 0.0001f)
    }

    @Test
    fun usesHeadYawWithoutPitchOrRollToPlaceStage() {
        val result =
            LearningStagePoseResolver.resolve(
                StageHeadPose(x = 0f, y = 1.7f, z = 0f, yawDegrees = 90f)
            )

        assertEquals(-LearningStagePoseResolver.ViewingDistanceMeters, result.x, 0.0001f)
        assertEquals(1.7f, result.y, 0.0001f)
        assertEquals(0f, result.z, 0.0001f)
        assertEquals(90f, result.yawDegrees, 0.0001f)
    }

    @Test
    fun rearLearningWallStaysTenCentimetersBehindVideoForAnyHeadYaw() {
        for (yaw in listOf(0f, 90f, -35f, 180f)) {
            val head = StageHeadPose(1.2f, 1.72f, -0.4f, yaw)
            val hall = LearningStagePoseResolver.resolveLectureHall(head)
            val video = LearningStagePoseResolver.resolve(head)
            val hallRadians = Math.toRadians(hall.yawDegrees.toDouble())
            val headRadians = Math.toRadians(yaw.toDouble())
            // The authored rear wall's interior plane is +Z=7.295 in the Y-up asset.
            val wallX = hall.x + 7.295f * kotlin.math.sin(hallRadians).toFloat()
            val wallZ = hall.z + 7.295f * kotlin.math.cos(hallRadians).toFloat()
            assertEquals(video.x - 0.10f * kotlin.math.sin(headRadians).toFloat(), wallX, 0.0001f)
            assertEquals(video.z - 0.10f * kotlin.math.cos(headRadians).toFloat(), wallZ, 0.0001f)
            assertEquals(0f, hall.y, 0.0001f)
            assertEquals(yaw + 180f, hall.yawDegrees, 0.0001f)
        }
    }

    @Test
    fun fallbackUsesTheSameLearningWallAlignmentAsTrackedPose() {
        assertEquals(
            LearningStagePoseResolver.resolveLectureHall(StageHeadPose(0f, 1.55f, 0f, 0f)),
            LearningStagePoseResolver.LectureHallFallback,
        )
    }

    @Test
    fun keepsSubtitleAndVoiceControlRelativeToVideoOrigin() {
        assertEquals(0f, LearningStageLocalPose.Video.x, 0.0001f)
        assertEquals(0f, LearningStageLocalPose.Video.y, 0.0001f)
        assertEquals(0f, LearningStageLocalPose.Video.z, 0.0001f)
        assertEquals(-0.72f, LearningStageLocalPose.Subtitle.y, 0.0001f)
        assertEquals(0.04f, LearningStageLocalPose.Subtitle.z, 0.0001f)
        assertEquals(-0.86f, LearningStageLocalPose.VoiceControl.y, 0.0001f)
        val orbDistance =
            LearningStagePoseResolver.ViewingDistanceMeters - LearningStageLocalPose.VoiceControl.z
        org.junit.Assert.assertTrue(orbDistance in 1.4f..1.7f)
    }

    @Test
    fun stabilizesHeadTrackingSamplesBeforeResolvingTheStageRoot() {
        val stable =
            StageHeadPoseStabilizer.average(
                listOf(
                    StageHeadPose(0f, 1.60f, 0f, 9f),
                    StageHeadPose(0.03f, 1.70f, -0.02f, 11f),
                    StageHeadPose(-0.03f, 1.65f, 0.02f, 10f),
                )
            )

        assertEquals(0f, stable.x, 0.0001f)
        assertEquals(1.65f, stable.y, 0.0001f)
        assertEquals(0f, stable.z, 0.0001f)
        assertEquals(10f, stable.yawDegrees, 0.0001f)
    }
}
