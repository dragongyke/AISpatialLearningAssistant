package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseCarouselLayoutTest {
    @Test
    fun cardWidthLeavesHalfOfTheFourthCardVisible() {
        val cardWidth =
            CourseCarouselLayout.cardWidth(
                viewportWidth = 1_224f,
                itemSpacing = 18f,
            )

        assertEquals(334.2857f, cardWidth, 0.001f)
    }
}
