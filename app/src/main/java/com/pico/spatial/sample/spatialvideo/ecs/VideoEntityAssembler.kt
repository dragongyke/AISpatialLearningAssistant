/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.ecs

import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.VideoPlayerComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.VideoMaterial
import com.pico.spatial.core.ecs.video.CypressMediaPlayer
import com.pico.spatial.core.ecs.video.VideoDimensionMode
import com.pico.spatial.core.ecs.video.VideoTextureSampleMode
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3

/** Configuration constants for video entities. */
object VideoEntityConfig {
    val PANEL_WIDTH = 3168.dp
    val PANEL_HEIGHT = 1782.dp
    const val PANEL_CORNER_RADIUS = 0.02f
    const val PANEL_LOCAL_X = 0f
    const val PANEL_LOCAL_Y = 0f
    const val PANEL_LOCAL_Z = 0f
}

/** Assembler for configuring entities with video components and behaviors. */
object VideoEntityAssembler {
    private const val VIDEO_TEXTURE_SAMPLE_NAME = "ScreenLightVideo"

    /** Configures this [Entity] as a planar video panel. */
    suspend fun assembleVideoPanel(
        entity: Entity,
        player: CypressMediaPlayer,
        panelWidth: Float,
        panelHeight: Float,
    ) {
        val mesh =
            MeshResource.createVideoPanel(
                panelWidth,
                panelHeight,
                VideoEntityConfig.PANEL_CORNER_RADIUS
            )
        val material = createVideoMaterial()
        val videoPlayerComponent =
            VideoPlayerComponent(player, mesh, material).apply {
                setTextureSampleMode(VideoTextureSampleMode.RAW_AND_BLURRED)
                setTextureSampleName(VIDEO_TEXTURE_SAMPLE_NAME)
            }
        entity.components.set(videoPlayerComponent)
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(
                Vector3(
                    VideoEntityConfig.PANEL_LOCAL_X,
                    VideoEntityConfig.PANEL_LOCAL_Y,
                    VideoEntityConfig.PANEL_LOCAL_Z,
                )
            )
        }
    }

    private fun createVideoMaterial(): VideoMaterial =
        VideoMaterial(
            BlendingMode.OPAQUE,
            VideoDimensionMode.MONO,
            MaterialCullingMode.BACK,
            Color4.BLACK,
        )
}
