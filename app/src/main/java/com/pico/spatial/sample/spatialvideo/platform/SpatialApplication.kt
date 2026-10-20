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
package com.pico.spatial.sample.spatialvideo.platform

import android.app.Application
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.di.videoModule
import com.pico.spatial.sample.spatialvideo.learning.VoskModelInstaller
import com.pico.spatial.sample.spatialvideo.mainApp
import com.pico.spatial.ui.foundation.dsl.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch as coroutineLaunch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class SpatialApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.coroutineLaunch {
            VoskModelInstaller(this@SpatialApplication).ensureInstalled()
        }
        startKoin {
            androidContext(this@SpatialApplication)
            modules(videoModule)
        }
        GlobalContext.get().createScope(VIDEO_SESSION_SCOPE_ID, named(VIDEO_SESSION_SCOPE_ID))
        launch(::mainApp)
    }
}
