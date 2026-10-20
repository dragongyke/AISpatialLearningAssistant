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
package com.pico.spatial.sample.spatialvideo

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.sample.spatialvideo.ui.CourseCatalogScreen
import com.pico.spatial.sample.spatialvideo.ui.ImmersiveScene
import com.pico.spatial.sample.spatialvideo.ui.LearningHistoryWindow
import com.pico.spatial.sample.spatialvideo.ui.LearningNoteWindow
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Immersion
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.platform.resize.CaptionBarType

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme {
                CourseCatalogScreen(
                    Modifier.windowConstraints(
                        width = COURSE_CATALOG_WIDTH,
                        height = COURSE_CATALOG_HEIGHT,
                    )
                )
            }
        }

        WindowContainer(
            id = HISTORY_WINDOW_CONTAINER_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(HISTORY_WINDOW_WIDTH, COURSE_CATALOG_HEIGHT),
            enableMaterialBackground = false,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            placement = {
                containers
                    .firstOrNull { it.id == CATALOG_WINDOW_CONTAINER_ID }
                    ?.let { Placement.right(it, HISTORY_WINDOW_GAP) } ?: Placement.none()
            },
        ) {
            PicoTheme {
                LearningHistoryWindow(
                    Modifier.windowConstraints(
                        width = HISTORY_WINDOW_WIDTH,
                        height = COURSE_CATALOG_HEIGHT,
                    )
                )
            }
        }

        WindowContainer(
            id = NOTE_WINDOW_CONTAINER_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(NOTE_WINDOW_WIDTH, COURSE_CATALOG_HEIGHT),
            enableMaterialBackground = false,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            placement = {
                containers
                    .firstOrNull { it.id == HISTORY_WINDOW_CONTAINER_ID }
                    ?.let { Placement.right(it, HISTORY_WINDOW_GAP) } ?: Placement.none()
            },
        ) {
            PicoTheme {
                LearningNoteWindow(
                    Modifier.windowConstraints(
                        width = NOTE_WINDOW_WIDTH,
                        height = COURSE_CATALOG_HEIGHT,
                    )
                )
            }
        }

        Stage(
            id = STAGE_ID,
            immersion = Immersion(default = 100, min = 0, max = 100),
        ) {
            PicoTheme { ImmersiveScene() }
        }
    }

const val STAGE_ID = "spatial_video_immersive"

const val CATALOG_WINDOW_CONTAINER_ID = "AILearningCatalog"
const val CATALOG_WINDOW_TAG = "ai-learning-catalog"
const val HISTORY_WINDOW_CONTAINER_ID = "AILearningHistory"
const val HISTORY_WINDOW_TAG = "ai-learning-history"
const val NOTE_WINDOW_CONTAINER_ID = "AILearningNote"
const val NOTE_WINDOW_TAG = "ai-learning-note"

private val COURSE_CATALOG_WIDTH = 1280.dp
private val COURSE_CATALOG_HEIGHT = 760.dp
private val HISTORY_WINDOW_WIDTH = 500.dp
private val NOTE_WINDOW_WIDTH = 720.dp
private val HISTORY_WINDOW_GAP = 24.dp
