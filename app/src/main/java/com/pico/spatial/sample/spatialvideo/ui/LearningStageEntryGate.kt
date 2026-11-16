package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.learning.LearningAiConfigurationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class LearningStageEntryGate(
    val configuration: LearningAiConfigurationStatus,
    private val enterStage: suspend () -> Boolean,
) {
    private val notice = MutableStateFlow(false)
    val showNotice = notice.asStateFlow()
    private var pendingPreparation: (() -> Boolean)? = null
    private var entering = false

    suspend fun request(prepareSession: () -> Boolean) {
        if (entering || pendingPreparation != null) return
        if (configuration.requiresNotice) {
            pendingPreparation = prepareSession
            notice.value = true
        } else {
            enter(prepareSession)
        }
    }

    suspend fun continueEntry() {
        val prepare = pendingPreparation ?: return
        dismiss()
        enter(prepare)
    }

    fun dismiss() {
        pendingPreparation = null
        notice.value = false
    }

    private suspend fun enter(prepareSession: () -> Boolean) {
        if (entering) return
        entering = true
        try {
            if (prepareSession()) enterStage()
        } finally {
            entering = false
        }
    }
}
