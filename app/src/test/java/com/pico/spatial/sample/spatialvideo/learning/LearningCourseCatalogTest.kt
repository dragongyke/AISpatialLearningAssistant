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
package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCourseCatalogTest {
    @Test
    fun catalogContainsFiveDirectPlayableStanfordVideos() {
        assertEquals(
            listOf(
                "stanford-cs106a-lecture01",
                "stanford-cs229-lecture01",
                "stanford-ee364a-lecture01",
                "stanford-cs107-lecture01",
                "stanford-ee263-lecture01",
            ),
            LearningCourseCatalog.videos.map { it.id },
        )
        LearningCourseCatalog.videos.forEach { video ->
            assertTrue(video.sourceUri.startsWith("https://see.stanford.edu/videos/courses/see/"))
            assertTrue(video.sourceUri.endsWith(".mp4"))
            assertTrue(video.sourcePageUri.startsWith("https://see.stanford.edu/Course/"))
        }
    }

    @Test
    fun catalogUsesSimplifiedV1CategoriesWithoutRecentUpdates() {
        assertEquals(
            listOf("All", "Programming", "AI / Machine Learning", "Math / Optimization"),
            LearningCourseCatalog.categories,
        )
    }

    @Test
    fun filtersCoursesByCategoryAndSearchText() {
        val result =
            LearningCourseFilter.filter(
                videos = LearningCourseCatalog.videos,
                category = "Programming",
                query = "paradigms",
            )

        assertEquals(listOf("stanford-cs107-lecture01"), result.map { it.id })
    }
}
