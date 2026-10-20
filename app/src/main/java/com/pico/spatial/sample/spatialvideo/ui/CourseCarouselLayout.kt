package com.pico.spatial.sample.spatialvideo.ui

object CourseCarouselLayout {
    fun cardWidth(
        viewportWidth: Float,
        itemSpacing: Float,
        visibleCardCount: Float = 3.5f,
    ): Float = ((viewportWidth - itemSpacing * 3f) / visibleCardCount).coerceAtLeast(1f)
}
