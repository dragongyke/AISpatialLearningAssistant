package com.pico.spatial.sample.spatialvideo.ui

class LearningWindowCoordinator(
    private val openStage: suspend () -> Boolean,
    private val minimizeCatalog: () -> Unit,
    private val minimizeHistory: () -> Unit,
    private val closeNote: () -> Unit = {},
    private val restoreCatalog: () -> Unit,
    private val restoreHistory: () -> Unit,
    private val openHistory: () -> Unit = {},
) {
    fun showHistory() = openHistory()

    suspend fun enterStage(): Boolean {
        if (!openStage()) return false
        minimizeCatalog()
        minimizeHistory()
        closeNote()
        return true
    }

    fun restoreSharedWindows() {
        restoreCatalog()
        restoreHistory()
    }
}
