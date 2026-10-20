package com.pico.spatial.sample.spatialvideo.ui

internal sealed interface LearningCrystalOrbRelease {
    data object Tap : LearningCrystalOrbRelease

    data class Drag(val delta: SpatialDragDelta) : LearningCrystalOrbRelease

    data object Cancelled : LearningCrystalOrbRelease
}

internal class LearningCrystalOrbGesturePolicy(
    private val dragThresholdMeters: Float = 0.012f,
    private val maxTapDurationMs: Long = 350,
) {
    private data class Capture(
        val owner: Any,
        val pointer: Long,
        val start: SpatialDragPoint,
        val startedMs: Long,
        var lastTimeMs: Long,
        var dragging: Boolean = false,
    )

    private var capture: Capture? = null

    init {
        require(dragThresholdMeters.isFinite() && dragThresholdMeters > 0f)
        require(maxTapDurationMs >= 0)
    }

    /** Points must share a stable, meter-based space; time must be monotonic milliseconds. */
    fun begin(owner: Any, pointer: Long, point: SpatialDragPoint, timeMs: Long): Boolean {
        if (capture != null || !point.isFinite() || timeMs < 0) return false
        capture = Capture(owner, pointer, point, timeMs, timeMs)
        return true
    }

    fun owns(owner: Any, pointer: Long): Boolean =
        capture?.let { it.owner === owner && it.pointer == pointer } == true

    /** Full displacement from pinch-down, never an incremental delta. No pose change below slop. */
    fun move(owner: Any, pointer: Long, point: SpatialDragPoint, timeMs: Long): SpatialDragDelta? {
        if (!owns(owner, pointer)) return null
        val active = capture ?: return null
        val delta = sample(active, point, timeMs) ?: return null
        return delta.takeIf { active.dragging }
    }

    /** Call for captured releases even when the pointer no longer targets the orb. */
    fun end(
        owner: Any,
        pointer: Long,
        point: SpatialDragPoint,
        timeMs: Long
    ): LearningCrystalOrbRelease? {
        if (!owns(owner, pointer)) return null
        val active = capture ?: return null
        val delta = sample(active, point, timeMs)
        reset()
        return when {
            delta == null -> LearningCrystalOrbRelease.Cancelled
            active.dragging -> LearningCrystalOrbRelease.Drag(delta)
            timeMs - active.startedMs <= maxTapDurationMs -> LearningCrystalOrbRelease.Tap
            else -> LearningCrystalOrbRelease.Cancelled
        }
    }

    fun cancel(owner: Any, pointer: Long) {
        if (owns(owner, pointer)) reset()
    }

    /** Call on stage hide, lost tracking, input-owner disposal, or scene teardown. */
    fun reset() {
        capture = null
    }

    private fun sample(active: Capture, point: SpatialDragPoint, timeMs: Long): SpatialDragDelta? {
        val delta =
            SpatialDragDelta(
                point.x - active.start.x,
                point.y - active.start.y,
                point.z - active.start.z
            )
        if (
            !point.isFinite() ||
                !delta.x.isFinite() ||
                !delta.y.isFinite() ||
                !delta.z.isFinite() ||
                timeMs < active.lastTimeMs
        ) {
            reset()
            return null
        }
        active.lastTimeMs = timeMs
        // Double arithmetic prevents squared finite float coordinates from overflowing.
        val distanceSquared =
            delta.x.toDouble() * delta.x +
                delta.y.toDouble() * delta.y +
                delta.z.toDouble() * delta.z
        if (distanceSquared >= dragThresholdMeters.toDouble() * dragThresholdMeters) {
            active.dragging = true
        }
        return delta
    }

    private fun SpatialDragPoint.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
}
