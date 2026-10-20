package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSummaryStoragePolicyTest {
    @Test
    fun usesPublicDocumentsOnScopedStorageDevices() {
        assertTrue(LearningSummaryStoragePolicy.canWritePublicDocuments(29))
        assertFalse(LearningSummaryStoragePolicy.canWritePublicDocuments(28))
        assertEquals(
            "Documents/AI Spatial Learning",
            LearningSummaryStoragePolicy.relativePath,
        )
    }
}
