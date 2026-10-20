package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStoragePolicyTest {
    @Test
    fun summaryFileNameIsStablePerVideo() {
        assertEquals(
            "stanford-cs106a-lecture01-learning-summary.md",
            LearningSummaryNaming.fileName("stanford-cs106a-lecture01"),
        )
        assertEquals(
            LearningSummaryNaming.fileName("course/a"),
            LearningSummaryNaming.fileName("course/a"),
        )
    }
}
