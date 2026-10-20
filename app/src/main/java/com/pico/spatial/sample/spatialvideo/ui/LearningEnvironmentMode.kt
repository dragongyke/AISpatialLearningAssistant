package com.pico.spatial.sample.spatialvideo.ui

enum class LearningEnvironmentMode {
    VirtualHall,
    Reality,
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
