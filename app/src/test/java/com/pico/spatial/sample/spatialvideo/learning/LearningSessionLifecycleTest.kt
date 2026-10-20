package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSessionLifecycleTest {
    @Test
    fun repeatedStageExitAndDisposalFinishTheSessionOnlyOnce() {
        val lifecycle = LearningSessionLifecycle()
        lifecycle.start()
        val version = lifecycle.version
        assertTrue(lifecycle.exit(version))
        assertFalse(lifecycle.exit(version))
        assertFalse(lifecycle.exit(version))
    }

    @Test
    fun anOldStageCannotEndANewerSession() {
        val lifecycle = LearningSessionLifecycle()
        lifecycle.start()
        val old = lifecycle.version
        lifecycle.start()
        assertFalse(lifecycle.exit(old))
        assertTrue(lifecycle.isActive)
        assertTrue(lifecycle.exit(lifecycle.version))
    }

    @Test
    fun completionIsHandledOnceUntilExplicitReplay() {
        val lifecycle = LearningSessionLifecycle()
        assertFalse(lifecycle.complete())
        lifecycle.start()
        assertTrue(lifecycle.complete())
        assertFalse(lifecycle.complete())
        lifecycle.resume()
        assertTrue(lifecycle.complete())
    }
}
