package com.pico.spatial.sample.spatialvideo.ecs

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEntityConfigTest {
    @Test
    fun videoPanelUsesEnlargedLocalOriginLayout() {
        assertEquals(3168.dp, VideoEntityConfig.PANEL_WIDTH)
        assertEquals(1782.dp, VideoEntityConfig.PANEL_HEIGHT)
        assertEquals(0f, VideoEntityConfig.PANEL_LOCAL_X)
        assertEquals(0f, VideoEntityConfig.PANEL_LOCAL_Y)
        assertEquals(0f, VideoEntityConfig.PANEL_LOCAL_Z)
    }
}
