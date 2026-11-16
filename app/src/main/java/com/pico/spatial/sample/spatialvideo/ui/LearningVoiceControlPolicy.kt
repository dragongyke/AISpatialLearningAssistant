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
package com.pico.spatial.sample.spatialvideo.ui

import androidx.annotation.StringRes
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.learning.LearningUiState

internal enum class LearningVoiceControlState {
    Ready,
    Listening,
    Analyzing,
    Answering,
    Speaking,
    Unavailable,
}

@get:StringRes
internal val LearningVoiceControlState.statusLabelRes: Int
    get() =
        when (this) {
            LearningVoiceControlState.Ready -> R.string.learning_ready
            LearningVoiceControlState.Listening -> R.string.learning_listening
            LearningVoiceControlState.Analyzing -> R.string.learning_analyzing_video
            LearningVoiceControlState.Answering -> R.string.learning_answering
            LearningVoiceControlState.Speaking -> R.string.learning_speaking
            LearningVoiceControlState.Unavailable -> R.string.stage_voice_unavailable
        }

internal data class LearningVoiceControlPolicy(
    val isEnabled: Boolean,
    val state: LearningVoiceControlState,
)

internal fun learningVoiceControlPolicy(state: LearningUiState): LearningVoiceControlPolicy {
    val isEnabled = state.isAsrReady && !state.isAsrInstalling && !state.isListening
    val controlState =
        when {
            state.isListening -> LearningVoiceControlState.Listening
            state.isSpeaking -> LearningVoiceControlState.Speaking
            state.isAnalyzingVideo -> LearningVoiceControlState.Analyzing
            state.isAnswering || state.isPreparingSpeech -> LearningVoiceControlState.Answering
            isEnabled -> LearningVoiceControlState.Ready
            else -> LearningVoiceControlState.Unavailable
        }
    return LearningVoiceControlPolicy(isEnabled, controlState)
}
