package com.pico.spatial.sample.spatialvideo.ui

import android.content.Context
import androidx.compose.ui.unit.dp
import kotlin.math.atan2

data class SpatialPanelPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDegrees: Float,
)

data class LearningSpatialLayout(
    val video: SpatialPanelPose,
    val outline: SpatialPanelPose,
    val toolbar: SpatialPanelPose,
    val aiCoach: SpatialPanelPose,
    val voiceControl: SpatialPanelPose,
) {
    companion object {
        val Default =
            LearningSpatialLayout(
                video = LearningStageLocalPose.Video,
                outline = SpatialPanelPose(-1.95f, 0f, 0.60f, 24f),
                toolbar = SpatialPanelPose(0f, -1.18f, 0.12f, 0f),
                aiCoach = SpatialPanelPose(1.95f, 0f, 0.60f, -24f),
                voiceControl = LearningStageLocalPose.VoiceControl,
            )
    }
}

enum class LearningPanel {
    Video,
    Outline,
    Toolbar,
    AiCoach,
    VoiceControl,
}

fun LearningSpatialLayout.pose(panel: LearningPanel): SpatialPanelPose =
    when (panel) {
        LearningPanel.Video -> video
        LearningPanel.Outline -> outline
        LearningPanel.Toolbar -> toolbar
        LearningPanel.AiCoach -> aiCoach
        LearningPanel.VoiceControl -> voiceControl
    }

fun LearningSpatialLayout.move(
    panel: LearningPanel,
    deltaX: Float,
    deltaY: Float,
    deltaZ: Float,
): LearningSpatialLayout {
    val current = pose(panel)
    val moved =
        LearningSpatialLayoutPolicy.constrain(
            panel,
            current.copy(
                x = current.x + deltaX,
                y = current.y + deltaY,
                z = current.z + deltaZ,
            ),
        )
    return withPose(panel, moved)
}

fun LearningSpatialLayout.withPose(
    panel: LearningPanel,
    pose: SpatialPanelPose,
): LearningSpatialLayout =
    when (panel) {
        LearningPanel.Video -> copy(video = pose)
        LearningPanel.Outline -> copy(outline = pose)
        LearningPanel.Toolbar -> copy(toolbar = pose)
        LearningPanel.AiCoach -> copy(aiCoach = pose)
        LearningPanel.VoiceControl -> copy(voiceControl = pose)
    }

fun LearningSpatialLayout.sanitized(): LearningSpatialLayout =
    LearningPanel.entries.fold(this) { layout, panel ->
        layout.withPose(panel, LearningSpatialLayoutPolicy.constrain(panel, layout.pose(panel)))
    }

fun LearningSpatialLayout.dockedToBlackboard(): LearningSpatialLayout =
    copy(video = LearningStageLocalPose.Video)

object LearningSpatialLayoutPolicy {
    private data class Bounds(
        val x: ClosedFloatingPointRange<Float>,
        val y: ClosedFloatingPointRange<Float>,
        val z: ClosedFloatingPointRange<Float>,
    )

    fun constrain(panel: LearningPanel, pose: SpatialPanelPose): SpatialPanelPose {
        val bounds = bounds(panel)
        val x = pose.x.coerceIn(bounds.x)
        val y = pose.y.coerceIn(bounds.y)
        val z = pose.z.coerceIn(bounds.z)
        val yaw =
            if (panel == LearningPanel.Outline || panel == LearningPanel.AiCoach) {
                Math.toDegrees(atan2(-x, VIEWER_DISTANCE_METERS - z).toDouble()).toFloat()
            } else {
                0f
            }
        return pose.copy(x = x, y = y, z = z, yawDegrees = yaw)
    }

    fun snap(panel: LearningPanel, pose: SpatialPanelPose): SpatialPanelPose {
        val recommended = LearningSpatialLayout.Default.pose(panel)
        return constrain(
            panel,
            pose.copy(
                x = pose.x.snapTo(recommended.x, SNAP_X_METERS),
                y = pose.y.snapTo(recommended.y, SNAP_Y_METERS),
                z = pose.z.snapTo(recommended.z, SNAP_Z_METERS),
            ),
        )
    }

    private fun bounds(panel: LearningPanel): Bounds =
        when (panel) {
            LearningPanel.Video -> Bounds(-1.4f..1.4f, -0.7f..0.7f, 0f..0.6f)
            LearningPanel.Outline -> Bounds(-3.2f..-0.95f, -1.2f..0.8f, 0.55f..1.1f)
            LearningPanel.Toolbar -> Bounds(-1.6f..1.6f, -2.4f..-0.5f, 0f..0.9f)
            LearningPanel.AiCoach -> Bounds(0.95f..3.2f, -1.2f..0.8f, 0.55f..1.1f)
            LearningPanel.VoiceControl -> Bounds(-1.2f..1.2f, -1.5f..-0.4f, 0.2f..1.4f)
        }

    private fun Float.snapTo(target: Float, threshold: Float): Float =
        if (kotlin.math.abs(this - target) <= threshold) target else this

    private const val VIEWER_DISTANCE_METERS = 2.8f
    private const val SNAP_X_METERS = 0.10f
    private const val SNAP_Y_METERS = 0.10f
    private const val SNAP_Z_METERS = 0.10f
}

object LearningSpatialLayoutCodec {
    fun encode(layout: LearningSpatialLayout): String =
        CURRENT_VERSION_PREFIX +
            listOf(
                    layout.video,
                    layout.outline,
                    layout.toolbar,
                    layout.aiCoach,
                    layout.voiceControl,
                )
                .flatMap { pose -> listOf(pose.x, pose.y, pose.z, pose.yawDegrees) }
                .joinToString(SEPARATOR)

    fun decode(value: String?): LearningSpatialLayout =
        when {
            value?.startsWith(CURRENT_VERSION_PREFIX) == true -> decodeCurrent(value)
            value?.startsWith(PRE_BLACKBOARD_PREFIX) == true ->
                decodeCurrent(CURRENT_VERSION_PREFIX + value.removePrefix(PRE_BLACKBOARD_PREFIX))
                    .copy(
                        video = LearningSpatialLayout.Default.video,
                        voiceControl = LearningSpatialLayout.Default.voiceControl,
                    )
            value?.startsWith(LEGACY_VERSION_PREFIX) == true -> decodeLegacy(value)
            else -> LearningSpatialLayout.Default
        }

    private fun decodeCurrent(value: String): LearningSpatialLayout {
        val values =
            value.values(CURRENT_VERSION_PREFIX, CURRENT_VALUE_COUNT)
                ?: return LearningSpatialLayout.Default
        return LearningSpatialLayout(
            video = values.toPose(0),
            outline = values.toPose(4),
            toolbar = values.toPose(8),
            aiCoach = values.toPose(12),
            voiceControl = values.toPose(16),
        )
    }

    private fun decodeLegacy(value: String): LearningSpatialLayout {
        val values =
            value.values(LEGACY_VERSION_PREFIX, LEGACY_VALUE_COUNT)
                ?: return LearningSpatialLayout.Default
        return LearningSpatialLayout.Default.copy(
            outline = values.toPose(0),
            toolbar = values.toPose(4),
            aiCoach = values.toPose(8),
        )
    }

    private fun String.values(prefix: String, expectedCount: Int): List<Float>? =
        removePrefix(prefix).split(SEPARATOR).mapNotNull(String::toFloatOrNull).takeIf {
            it.size == expectedCount
        }

    private fun List<Float>.toPose(startIndex: Int) =
        SpatialPanelPose(
            x = this[startIndex],
            y = this[startIndex + 1],
            z = this[startIndex + 2],
            yawDegrees = this[startIndex + 3],
        )

    private const val SEPARATOR = ";"
    private const val CURRENT_VERSION_PREFIX = "6|"
    private const val PRE_BLACKBOARD_PREFIX = "5|"
    private const val LEGACY_VERSION_PREFIX = "4|"
    private const val CURRENT_VALUE_COUNT = 20
    private const val LEGACY_VALUE_COUNT = 12
}

object LearningStageDimensions {
    const val VoiceOrbRadiusMeters = 0.20f
    const val PanelScale = 1.5f
    const val PanelFontScale = 1.4f
    val OutlineWidth = 900.dp
    val AiCoachWidth = 900.dp
    val SidePanelHeight = 1600.dp
    val ToolbarWidth = 1770.dp
    val ToolbarHeight = 420.dp
    val SubtitleWidth = 2048.dp
    val SubtitleHeight = 300.dp
    val VoiceControlSize = 240.dp
    val DragBarWidth = 120.dp
    const val DragBarHoverScale = 1.22f
    val StandaloneDragBarSurfaceWidth = DragBarWidth * DragBarHoverScale + 24.dp
    val DragBarHeight = 56.dp
    val DragBarGap = 28.dp
    val PanelFooterHeight
        get() = DragBarGap + DragBarHeight
}

class LearningSpatialLayoutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LearningSpatialLayout =
        LearningSpatialLayoutCodec.decode(preferences.getString(KEY_LAYOUT, null)).sanitized()

    fun save(layout: LearningSpatialLayout): Boolean =
        preferences
            .edit()
            .putString(KEY_LAYOUT, LearningSpatialLayoutCodec.encode(layout.sanitized()))
            .commit()

    private companion object {
        const val PREFERENCES_NAME = "ai_learning_spatial_layout"
        const val KEY_LAYOUT = "default_layout"
    }
}
