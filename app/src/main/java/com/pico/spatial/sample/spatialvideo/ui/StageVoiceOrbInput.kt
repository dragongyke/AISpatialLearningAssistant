package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo

internal class StageVoiceOrbInput(private val layout: LearningPanelDragState) {
    private val gesture = LearningCrystalOrbGesturePolicy()
    private val smoothing = SpatialDragSmoothingFilter(0.55f)
    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var target: Entity? = null
    private var startPose = LearningStageLocalPose.VoiceControl
    private var applyPose: ((SpatialPanelPose) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    var isPressed = false
        private set

    fun bind(
        content: SpatialViewContent,
        root: Entity,
        target: Entity,
        applyPose: (SpatialPanelPose) -> Unit,
        onFinished: () -> Unit
    ) {
        reset()
        this.content = content
        stageRoot = root
        this.target = target
        this.applyPose = applyPose
        this.onFinished = onFinished
    }

    fun isTarget(entity: Entity): Boolean = entity == target

    fun onEvents(owner: Any, events: List<SpatialPointerInfo>, onTap: () -> Unit): Boolean {
        var consumed = false
        for (event in events) {
            val pointer = event.pointerId.value
            if (event.isDownEvent()) {
                if (event.targetedEntity != target || target == null) continue
                val point = stagePoint(event) ?: continue
                if (gesture.begin(owner, pointer, point, event.uptimeMillis)) {
                    startPose = layout.pose(LearningPanel.VoiceControl)
                    smoothing.reset()
                    isPressed = true
                    consumed = true
                }
            } else if (gesture.owns(owner, pointer)) {
                consumed = true
                val point = stagePoint(event)
                if (point == null) {
                    reset()
                    continue
                }
                if (event.pressed) {
                    gesture.move(owner, pointer, point, event.uptimeMillis)?.let {
                        move(smoothing.apply(it))
                    }
                    if (!gesture.owns(owner, pointer)) reset()
                } else {
                    val release = gesture.end(owner, pointer, point, event.uptimeMillis)
                    isPressed = false
                    smoothing.reset()
                    when (release) {
                        LearningCrystalOrbRelease.Tap -> onTap()
                        is LearningCrystalOrbRelease.Drag -> {
                            move(release.delta)
                            applyPose?.invoke(layout.finish(LearningPanel.VoiceControl))
                            onFinished?.invoke()
                        }
                        else -> Unit
                    }
                }
            }
        }
        return consumed
    }

    private fun move(delta: SpatialDragDelta) {
        applyPose?.invoke(layout.moveFrom(LearningPanel.VoiceControl, startPose, delta))
    }

    private fun stagePoint(event: SpatialPointerInfo): SpatialDragPoint? {
        val content = content ?: return null
        val root = stageRoot ?: return null
        val point = event.position3D
        val world =
            content.convertPosition(
                Vector3(point.x, point.y, point.z),
                ViewCoordinateSpace.Local,
                content.localSpatialCoordinateSpace,
            )
        val local = root.convertPositionFrom(world, null)
        return SpatialDragPoint(local.x, local.y, local.z)
    }

    fun reset() {
        gesture.reset()
        smoothing.reset()
        isPressed = false
    }

    fun clear() {
        reset()
        content = null
        stageRoot = null
        target = null
        applyPose = null
        onFinished = null
    }
}
