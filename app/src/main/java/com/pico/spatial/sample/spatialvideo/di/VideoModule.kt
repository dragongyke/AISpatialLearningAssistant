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
package com.pico.spatial.sample.spatialvideo.di

import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.sample.spatialvideo.ui.VideoViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val videoModule = module {
    scope(named(VIDEO_SESSION_SCOPE_ID)) {
        scoped { VideoViewModel() }
        scoped { LearningSessionViewModel(get(), androidContext()) }
    }
}

const val VIDEO_SESSION_SCOPE_ID = "video_session_scope"
