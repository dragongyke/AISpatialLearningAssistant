package com.pico.spatial.sample.spatialvideo.learning

class LearningSessionLifecycle {
    var version = 0L
        private set

    var isActive = false
        private set

    private var completed = false

    fun start() {
        version++
        isActive = true
        completed = false
    }

    fun canExit(expectedVersion: Long): Boolean = isActive && version == expectedVersion

    fun exit(expectedVersion: Long): Boolean {
        if (!canExit(expectedVersion)) return false
        isActive = false
        return true
    }

    fun complete(): Boolean {
        if (!isActive || completed) return false
        completed = true
        return true
    }

    fun resume() {
        completed = false
    }
}
