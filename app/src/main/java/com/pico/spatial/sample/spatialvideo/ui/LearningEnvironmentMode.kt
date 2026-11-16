package com.pico.spatial.sample.spatialvideo.ui

import androidx.annotation.StringRes
import com.pico.spatial.sample.spatialvideo.R

enum class LearningEnvironmentMode(@StringRes val toggleLabelRes: Int) {
    VirtualHall(R.string.learning_view_reality),
    Reality(R.string.learning_return_lecture_hall),
}

object LearningEnvironmentModePolicy {
    val initial = LearningEnvironmentMode.VirtualHall

    fun toggle(mode: LearningEnvironmentMode): LearningEnvironmentMode =
        when (mode) {
            LearningEnvironmentMode.VirtualHall -> LearningEnvironmentMode.Reality
            LearningEnvironmentMode.Reality -> LearningEnvironmentMode.VirtualHall
        }

    fun showsVirtualHall(mode: LearningEnvironmentMode): Boolean =
        mode == LearningEnvironmentMode.VirtualHall
}
