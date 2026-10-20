package com.pico.spatial.sample.spatialvideo.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningWindowCoordinatorTest {
    @Test
    fun canReopenHistoryAfterEachUserCloseWithoutOpeningStage() {
        var historyOpen = false
        var openRequests = 0
        val coordinator =
            LearningWindowCoordinator(
                openStage = { error("History must not enter Stage") },
                minimizeCatalog = {},
                minimizeHistory = {},
                restoreCatalog = {},
                restoreHistory = {},
                openHistory = {
                    historyOpen = true
                    openRequests++
                },
            )
        repeat(3) {
            historyOpen = false
            coordinator.showHistory()
            assertTrue(historyOpen)
        }
        assertEquals(3, openRequests)
    }

    @Test
    fun minimizesSharedWindowsOnlyAfterStageOpens() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator =
            LearningWindowCoordinator(
                openStage = {
                    calls += "open-stage"
                    true
                },
                minimizeCatalog = { calls += "minimize-catalog" },
                minimizeHistory = { calls += "minimize-history" },
                closeNote = { calls += "close-note" },
                restoreCatalog = {},
                restoreHistory = {},
            )

        assertTrue(coordinator.enterStage())
        assertEquals(
            listOf("open-stage", "minimize-catalog", "minimize-history", "close-note"),
            calls,
        )
    }

    @Test
    fun keepsSharedWindowsVisibleWhenStageOpeningIsDenied() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator =
            LearningWindowCoordinator(
                openStage = {
                    calls += "open-stage"
                    false
                },
                minimizeCatalog = { calls += "minimize-catalog" },
                minimizeHistory = { calls += "minimize-history" },
                closeNote = { calls += "close-note" },
                restoreCatalog = {},
                restoreHistory = {},
            )

        assertFalse(coordinator.enterStage())
        assertEquals(listOf("open-stage"), calls)
    }

    @Test
    fun restoresCatalogAndHistoryTogether() {
        val calls = mutableListOf<String>()
        val coordinator =
            LearningWindowCoordinator(
                openStage = { true },
                minimizeCatalog = {},
                minimizeHistory = {},
                closeNote = {},
                restoreCatalog = { calls += "restore-catalog" },
                restoreHistory = { calls += "restore-history" },
            )

        coordinator.restoreSharedWindows()

        assertEquals(listOf("restore-catalog", "restore-history"), calls)
    }
}
