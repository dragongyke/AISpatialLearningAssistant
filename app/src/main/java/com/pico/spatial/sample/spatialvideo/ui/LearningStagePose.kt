package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class StageHeadPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDegrees: Float,
)

data class StageRootPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDegrees: Float,
)

object StageHeadPoseStabilizer {
    fun isPlausible(sample: StageHeadPose): Boolean =
        sample.x.isFinite() &&
            sample.y.isFinite() &&
            sample.z.isFinite() &&
            sample.y in MIN_EYE_HEIGHT_METERS..MAX_EYE_HEIGHT_METERS &&
            sample.yawDegrees.isFinite()

    fun average(samples: List<StageHeadPose>): StageHeadPose {
        require(samples.isNotEmpty()) { "At least one head pose sample is required." }
        val count = samples.size.toFloat()
        val sinYaw = samples.sumOf { sin(Math.toRadians(it.yawDegrees.toDouble())) }
        val cosYaw = samples.sumOf { cos(Math.toRadians(it.yawDegrees.toDouble())) }
        return StageHeadPose(
            x = samples.sumOf { it.x.toDouble() }.toFloat() / count,
            y = samples.sumOf { it.y.toDouble() }.toFloat() / count,
            z = samples.sumOf { it.z.toDouble() }.toFloat() / count,
            yawDegrees = Math.toDegrees(atan2(sinYaw, cosYaw)).toFloat(),
        )
    }

    private const val MIN_EYE_HEIGHT_METERS = 0.8f
    private const val MAX_EYE_HEIGHT_METERS = 2.4f
}

object LearningStagePoseResolver {
    const val ViewingDistanceMeters = 2.8f

    fun resolve(headPose: StageHeadPose): StageRootPose {
        val yawRadians = Math.toRadians(headPose.yawDegrees.toDouble())
        val forwardX = -sin(yawRadians).toFloat()
        val forwardZ = -cos(yawRadians).toFloat()
        return StageRootPose(
            x = headPose.x + forwardX * ViewingDistanceMeters,
            y = headPose.y,
            z = headPose.z + forwardZ * ViewingDistanceMeters,
            yawDegrees = headPose.yawDegrees,
        )
    }

    fun resolveLectureHall(headPose: StageHeadPose): StageRootPose {
        val yaw = Math.toRadians(headPose.yawDegrees.toDouble())
        val setback = LearningWallLocalZ - ViewingDistanceMeters - LearningWallGapMeters
        return StageRootPose(
            x = headPose.x + sin(yaw).toFloat() * setback,
            y = 0f,
            z = headPose.z + cos(yaw).toFloat() * setback,
            yawDegrees = headPose.yawDegrees + 180f,
        )
    }

    // Interior plane of the authored rear wall, before rotating the library to face the viewer.
    const val LearningWallLocalZ = 7.295f
    const val LearningWallGapMeters = 0.10f

    val Fallback = StageRootPose(0f, 1.55f, -ViewingDistanceMeters, 0f)

    val LectureHallFallback = resolveLectureHall(StageHeadPose(0f, 1.55f, 0f, 0f))
}

object LearningStageLocalPose {
    val Video = SpatialPanelPose(0f, 0f, 0f, 0f)
    val Subtitle = SpatialPanelPose(0f, -0.72f, 0.04f, 0f)
    val VoiceControl = SpatialPanelPose(0f, -0.86f, 1.15f, 0f)
}
