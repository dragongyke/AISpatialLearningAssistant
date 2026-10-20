package com.pico.spatial.sample.spatialvideo.ui

internal class LearningDragGestureState(private val layout: LearningPanelDragState) {
    private data class Capture(
        val owner: Any,
        val pointer: Long,
        val session: SpatialPanelDragSession,
        var moved: Boolean = false,
    )

    private var capture: Capture? = null
    private val smoothing = SpatialDragSmoothingFilter()

    fun begin(owner: Any, pointer: Long, panel: LearningPanel, point: SpatialDragPoint): Boolean {
        if (capture != null || !point.isFinite()) return false
        smoothing.reset()
        capture =
            Capture(
                owner,
                pointer,
                SpatialPanelDragSession(panel, point, point, layout.pose(panel))
            )
        return true
    }

    fun owns(owner: Any, pointer: Long): Boolean =
        capture?.let { it.owner === owner && it.pointer == pointer } == true

    fun move(
        owner: Any,
        pointer: Long,
        point: SpatialDragPoint
    ): Pair<LearningPanel, SpatialPanelPose>? {
        if (!owns(owner, pointer) || !point.isFinite()) return null
        val active = capture ?: return null
        val session = active.session
        val before = layout.pose(session.panel)
        val pose =
            layout.moveFrom(
                session.panel,
                session.startPanelPose,
                smoothing.apply(session.stageDelta(point))
            )
        if (pose == before) return null
        active.moved = true
        return session.panel to pose
    }

    fun end(owner: Any, pointer: Long): Pair<LearningPanel, SpatialPanelPose>? {
        if (!owns(owner, pointer)) return null
        val active = capture ?: return null
        reset()
        return if (active.moved) active.session.panel.let { it to layout.finish(it) } else null
    }

    fun cancel(owner: Any) {
        if (capture?.owner === owner) reset()
    }

    fun reset() {
        capture = null
        smoothing.reset()
    }

    private fun SpatialDragPoint.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
}
