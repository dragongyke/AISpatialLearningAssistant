package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningCourseAvailabilityTest {
    @Test
    fun `successful and partial responses are available`() {
        assertEquals(
            CourseAvailability.Available,
            LearningCourseAvailabilityPolicy.fromHttpCode(200)
        )
        assertEquals(
            CourseAvailability.Available,
            LearningCourseAvailabilityPolicy.fromHttpCode(206)
        )
    }

    @Test
    fun `client and server failures are unavailable`() {
        assertEquals(
            CourseAvailability.Unavailable,
            LearningCourseAvailabilityPolicy.fromHttpCode(404)
        )
        assertEquals(
            CourseAvailability.Unavailable,
            LearningCourseAvailabilityPolicy.fromHttpCode(503)
        )
    }
}
