package com.pico.spatial.sample.spatialvideo.ui

class StageVisibilityTracker {
    var ownerSessionId: Long? = null
        private set

    private var hasEnteredStage = false
    private var hasEndedCurrentVisit = false

    fun shouldEndSession(isOnstage: Boolean, currentSessionId: Long? = null): Boolean {
        if (isOnstage) {
            if (!hasEnteredStage || hasEndedCurrentVisit) ownerSessionId = currentSessionId
            hasEnteredStage = true
            hasEndedCurrentVisit = false
            return false
        }
        if (!hasEnteredStage || hasEndedCurrentVisit) return false
        hasEndedCurrentVisit = true
        return true
    }
}
