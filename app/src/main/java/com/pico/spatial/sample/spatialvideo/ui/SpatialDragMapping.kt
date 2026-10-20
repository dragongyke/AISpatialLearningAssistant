package com.pico.spatial.sample.spatialvideo.ui

data class SpatialDragDelta(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class SpatialDragPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

class SpatialDragSmoothingFilter(private val factor: Float = 0.35f) {
    private var filtered = SpatialDragDelta(0f, 0f, 0f)

    fun apply(delta: SpatialDragDelta): SpatialDragDelta {
        val safeFactor = factor.coerceIn(0f, 1f)
        filtered =
            SpatialDragDelta(
                x = filtered.x + (delta.x - filtered.x) * safeFactor,
                y = filtered.y + (delta.y - filtered.y) * safeFactor,
                z = filtered.z + (delta.z - filtered.z) * safeFactor,
            )
        return filtered
    }

    fun reset() {
        filtered = SpatialDragDelta(0f, 0f, 0f)
    }
}

class SpatialPanelDragSession(
    val panel: LearningPanel,
    private val startViewPoint: SpatialDragPoint,
    private val startStagePoint: SpatialDragPoint,
    val startPanelPose: SpatialPanelPose,
) {
    private var accumulatedViewDelta = SpatialDragDelta(0f, 0f, 0f)

    fun addViewDelta(delta: SpatialDragDelta): SpatialDragPoint {
        accumulatedViewDelta =
            SpatialDragDelta(
                x = accumulatedViewDelta.x + delta.x,
                y = accumulatedViewDelta.y + delta.y,
                z = accumulatedViewDelta.z + delta.z,
            )
        return SpatialDragPoint(
            x = startViewPoint.x + accumulatedViewDelta.x,
            y = startViewPoint.y + accumulatedViewDelta.y,
            z = startViewPoint.z + accumulatedViewDelta.z,
        )
    }

    fun stageDelta(currentStagePoint: SpatialDragPoint) =
        SpatialDragDelta(
            x = currentStagePoint.x - startStagePoint.x,
            y = currentStagePoint.y - startStagePoint.y,
            z = currentStagePoint.z - startStagePoint.z,
        )
}

object SpatialDragBarGeometry {
    fun centerY(
        panelHeightMeters: Float,
        gapMeters: Float,
        barHeightMeters: Float,
    ): Float = -(panelHeightMeters / 2f + gapMeters + barHeightMeters / 2f)

    fun localPose(
        panelHeightMeters: Float,
        gapMeters: Float,
        barHeightMeters: Float,
        forwardOffsetMeters: Float,
        handleScale: Float = 1f,
    ): SpatialDragPoint =
        SpatialDragPoint(
            x = 0f,
            y = centerY(panelHeightMeters, gapMeters * handleScale, barHeightMeters * handleScale),
            z = forwardOffsetMeters * handleScale,
        )
}

class LearningPanelDragState(initialLayout: LearningSpatialLayout) {
    private var currentLayout = initialLayout
    private var minimumY = emptyMap<LearningPanel, Float>()

    fun setFloorLimits(limits: Map<LearningPanel, Float>) {
        minimumY = limits
        replace(currentLayout)
    }

    private fun aboveFloor(panel: LearningPanel, pose: SpatialPanelPose): SpatialPanelPose =
        pose.copy(y = maxOf(pose.y, minimumY[panel] ?: Float.NEGATIVE_INFINITY))

    fun pose(panel: LearningPanel): SpatialPanelPose = currentLayout.pose(panel)

    fun moveFrom(
        panel: LearningPanel,
        startPose: SpatialPanelPose,
        delta: SpatialDragDelta,
    ): SpatialPanelPose {
        val currentPose = pose(panel)
        val targetPose =
            startPose.copy(
                x = startPose.x + delta.x,
                y = startPose.y + delta.y,
                z = startPose.z + delta.z,
            )
        currentLayout =
            currentLayout.move(
                panel = panel,
                deltaX = targetPose.x - currentPose.x,
                deltaY = targetPose.y - currentPose.y,
                deltaZ = targetPose.z - currentPose.z,
            )
        currentLayout = currentLayout.withPose(panel, aboveFloor(panel, pose(panel)))
        return pose(panel)
    }

    fun replace(layout: LearningSpatialLayout) {
        currentLayout = layout
        LearningPanel.entries.forEach { panel ->
            currentLayout = currentLayout.withPose(panel, aboveFloor(panel, pose(panel)))
        }
    }

    fun finish(panel: LearningPanel): SpatialPanelPose {
        val snapped = aboveFloor(panel, LearningSpatialLayoutPolicy.snap(panel, pose(panel)))
        currentLayout = currentLayout.withPose(panel, snapped)
        return snapped
    }

    fun snapshot(): LearningSpatialLayout = currentLayout
}
