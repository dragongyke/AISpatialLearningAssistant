package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.ecs.VideoEntityConfig
import kotlin.math.max

data class SpatialPanelFrameGeometry(
    val bodyHeight: Float,
    val gap: Float,
    val handleHeight: Float
) {
    val surfaceY: Float
        get() = -(gap + handleHeight) / 2f

    val handleY: Float
        get() = SpatialDragBarGeometry.centerY(bodyHeight, gap, handleHeight)
}

class LearningStageGeometry(private val metersPerDp: Float) {
    fun frame(panel: LearningPanel): SpatialPanelFrameGeometry =
        SpatialPanelFrameGeometry(
            bodyHeight(panel) / LearningStageDimensions.PanelScale,
            LearningStageDimensions.DragBarGap.value * metersPerDp,
            LearningStageDimensions.DragBarHeight.value * metersPerDp,
        )

    fun bodyHeight(panel: LearningPanel): Float =
        when (panel) {
            LearningPanel.Video -> VideoEntityConfig.PANEL_HEIGHT.value * metersPerDp
            LearningPanel.Outline,
            LearningPanel.AiCoach ->
                LearningStageDimensions.SidePanelHeight.value *
                    metersPerDp *
                    LearningStageDimensions.PanelScale
            LearningPanel.Toolbar ->
                LearningStageDimensions.ToolbarHeight.value *
                    metersPerDp *
                    LearningStageDimensions.PanelScale
            LearningPanel.VoiceControl -> LearningStageDimensions.VoiceOrbRadiusMeters * 2f
        }

    val videoContentLowerExtent: Float
        get() =
            max(
                bodyHeight(LearningPanel.Video) / 2f,
                -LearningStageLocalPose.Subtitle.y +
                    LearningStageDimensions.SubtitleHeight.value * metersPerDp / 2f,
            )

    val videoLowerExtent: Float
        get() = videoContentLowerExtent + footerHeight

    private val footerHeight: Float
        get() =
            (LearningStageDimensions.DragBarGap + LearningStageDimensions.DragBarHeight).value *
                metersPerDp *
                LearningStageDimensions.PanelScale

    fun lowerExtent(panel: LearningPanel): Float =
        when (panel) {
            LearningPanel.Video -> videoLowerExtent
            LearningPanel.VoiceControl -> LearningStageDimensions.VoiceOrbRadiusMeters
            else -> bodyHeight(panel) / 2f + footerHeight
        }

    fun blackboard(): LearningBlackboardGeometry =
        LearningBlackboardGeometry(
            innerWidth = VideoEntityConfig.PANEL_WIDTH.value * metersPerDp + 0.14f,
            top = bodyHeight(LearningPanel.Video) / 2f + 0.08f,
            bottom = -videoLowerExtent - 0.025f,
        )

    fun alignPanels(layout: LearningSpatialLayout): LearningSpatialLayout =
        layout.copy(
            outline = layout.outline.copy(y = layout.video.y),
            aiCoach = layout.aiCoach.copy(y = layout.video.y),
            toolbar =
                layout.toolbar.copy(
                    x = layout.video.x,
                    y =
                        minOf(
                            layout.toolbar.y,
                            layout.video.y -
                                videoLowerExtent -
                                bodyHeight(LearningPanel.Toolbar) / 2f -
                                0.16f
                        ),
                ),
        )

    fun rootHeight(eyeHeight: Float, layout: LearningSpatialLayout): Float =
        max(eyeHeight, LearningPanel.entries.maxOf { 0.12f - layout.pose(it).y + lowerExtent(it) })

    fun minimumCenterY(rootHeight: Float): Map<LearningPanel, Float> =
        LearningPanel.entries.associateWith { 0.12f - rootHeight + lowerExtent(it) }
}
