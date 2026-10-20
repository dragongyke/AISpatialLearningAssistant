package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.CATALOG_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.HISTORY_WINDOW_CONTAINER_ID
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CourseCatalogSpatialLayoutTest {
    @Test
    fun historyUsesASeparateWindowContainer() {
        assertNotEquals(CATALOG_WINDOW_CONTAINER_ID, HISTORY_WINDOW_CONTAINER_ID)
    }
}
