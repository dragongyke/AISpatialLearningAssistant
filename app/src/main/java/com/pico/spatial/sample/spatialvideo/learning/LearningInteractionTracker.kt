package com.pico.spatial.sample.spatialvideo.learning

internal class LearningInteractionTracker {
    private var currentId = 0L

    fun begin(): Long {
        currentId += 1L
        return currentId
    }

    fun invalidate() {
        currentId += 1L
    }

    fun isCurrent(id: Long): Boolean = id == currentId
}
