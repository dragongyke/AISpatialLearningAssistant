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

data class LearningCourseVideo(
    val id: String,
    val provider: String,
    val category: String,
    val courseTitle: String,
    val lectureTitle: String,
    val description: String,
    val sourceUri: String,
    val sourcePageUri: String,
    val licenseNote: String,
    val durationMs: Long,
    val chapters: List<LearningChapter>,
    val transcriptSegments: List<TranscriptSegment>,
) {
    fun toLearningVideo() =
        LearningVideo(
            id = id,
            title = "$courseTitle: $lectureTitle",
            sourceUri = sourceUri,
            durationMs = durationMs,
            transcriptStatus = ProcessingStatus.Ready,
            summaryStatus = ProcessingStatus.Ready,
            provider = provider,
            category = category,
            sourcePageUri = sourcePageUri,
            licenseNote = licenseNote,
        )
}

object LearningCourseCatalog {
    val videos =
        listOf(
            LearningCourseVideo(
                id = "stanford-cs106a-lecture01",
                provider = "Stanford Engineering Everywhere",
                category = "Programming",
                courseTitle = "CS106A Programming Methodology",
                lectureTitle = "Lecture 1 - Introduction",
                description =
                    "Introduces programming methodology, course expectations, and the way computational thinking is taught through small programs.",
                sourceUri =
                    "https://see.stanford.edu/videos/courses/see/CS106A/CS106A-lecture01.mp4",
                sourcePageUri = "https://see.stanford.edu/Course/CS106A/194",
                licenseNote =
                    "Stanford SEE content is generally CC BY-NC-SA 4.0 unless otherwise indicated.",
                durationMs = 2_986_867L,
                chapters =
                    listOf(
                        chapter("cs106a-l1-welcome", "Welcome to CS106A", 0L, 257_000L),
                        chapter("cs106a-l1-staff", "Course staff", 257_000L, 348_000L),
                        chapter(
                            "cs106a-l1-methodology",
                            "Why Programming Methodology?",
                            348_000L,
                            633_000L
                        ),
                        chapter(
                            "cs106a-l1-placement",
                            "Choosing the right course",
                            633_000L,
                            919_000L
                        ),
                        chapter("cs106a-l1-logistics", "Class logistics", 919_000L, 1_246_000L),
                        chapter(
                            "cs106a-l1-grading",
                            "Assignments and grading",
                            1_246_000L,
                            1_689_000L
                        ),
                        chapter("cs106a-l1-extensions", "Extensions", 1_689_000L, 1_853_000L),
                        chapter("cs106a-l1-exams", "Midterm and final", 1_853_000L, 1_920_000L),
                        chapter(
                            "cs106a-l1-grade-breakdown",
                            "Grade breakdown",
                            1_920_000L,
                            2_072_000L
                        ),
                        chapter("cs106a-l1-honor-code", "The Honor Code", 2_072_000L, 2_661_000L),
                        chapter("cs106a-l1-karel", "Why Karel?", 2_661_000L, 2_986_867L),
                    ),
                transcriptSegments = emptyList(),
            ),
            LearningCourseVideo(
                id = "stanford-cs229-lecture01",
                provider = "Stanford Engineering Everywhere",
                category = "AI / Machine Learning",
                courseTitle = "CS229 Machine Learning",
                lectureTitle = "Lecture 1 - Motivation and Applications",
                description =
                    "Introduces machine learning, course logistics, supervised learning, learning theory, unsupervised learning, and reinforcement learning.",
                sourceUri = "https://see.stanford.edu/videos/courses/see/CS229/CS229-lecture01.mp4",
                sourcePageUri = "https://see.stanford.edu/Course/CS229",
                licenseNote =
                    "Stanford SEE content is generally CC BY-NC-SA 4.0 unless otherwise indicated.",
                durationMs = 4_119_533L,
                chapters =
                    listOf(
                        chapter("cs229-l1-intro", "Introductory remarks", 28_000L, 310_000L),
                        chapter(
                            "cs229-l1-motivation",
                            "Motivation and applications",
                            310_000L,
                            634_000L
                        ),
                        chapter("cs229-l1-logistics", "Class logistics", 634_000L, 1_957_000L),
                        chapter(
                            "cs229-l1-definition",
                            "Definition of machine learning",
                            1_957_000L,
                            2_177_000L
                        ),
                        chapter(
                            "cs229-l1-supervised",
                            "Overview of supervised learning",
                            2_177_000L,
                            2_718_000L
                        ),
                        chapter(
                            "cs229-l1-theory",
                            "Overview of learning theory",
                            2_718_000L,
                            3_013_000L
                        ),
                        chapter(
                            "cs229-l1-unsupervised",
                            "Overview of unsupervised learning",
                            3_013_000L,
                            3_743_000L
                        ),
                        chapter(
                            "cs229-l1-rl",
                            "Overview of reinforcement learning",
                            3_743_000L,
                            4_119_533L
                        ),
                    ),
                transcriptSegments = emptyList(),
            ),
            LearningCourseVideo(
                id = "stanford-ee364a-lecture01",
                provider = "Stanford Engineering Everywhere",
                category = "Math / Optimization",
                courseTitle = "EE364A Convex Optimization I",
                lectureTitle = "Lecture 1 - Introduction",
                description =
                    "Introduces convex optimization, problem classes, modeling goals, and why convexity gives tractable structure.",
                sourceUri =
                    "https://see.stanford.edu/videos/courses/see/EE364A/EE364A-lecture01.mp4",
                sourcePageUri = "https://see.stanford.edu/Course/EE364A",
                licenseNote =
                    "Stanford SEE content is generally CC BY-NC-SA 4.0 unless otherwise indicated.",
                durationMs = 4_832_200L,
                chapters =
                    listOf(
                        chapter("ee364a-l1-intro", "Optimization problem framing", 0L, 900_000L),
                        chapter(
                            "ee364a-l1-convexity",
                            "Why convexity matters",
                            900_000L,
                            1_980_000L
                        ),
                        chapter("ee364a-l1-modeling", "Modeling examples", 1_980_000L, 3_360_000L),
                        chapter(
                            "ee364a-l1-applications",
                            "Applications and course map",
                            3_360_000L,
                            4_832_200L
                        ),
                    ),
                transcriptSegments = emptyList(),
            ),
            LearningCourseVideo(
                id = "stanford-cs107-lecture01",
                provider = "Stanford Engineering Everywhere",
                category = "Programming",
                courseTitle = "CS107 Programming Paradigms",
                lectureTitle = "Lecture 1 - Course and Paradigm Overview",
                description =
                    "Introduces the course, systems programming foundations, and the imperative, object-oriented, concurrent, functional, and scripting paradigms.",
                sourceUri = "https://see.stanford.edu/videos/courses/see/CS107/CS107-lecture01.mp4",
                sourcePageUri = "https://see.stanford.edu/Course/CS107",
                licenseNote =
                    "Stanford SEE content is generally CC BY-NC-SA 4.0 unless otherwise indicated.",
                durationMs = 1_045_067L,
                chapters =
                    listOf(
                        chapter(
                            "cs107-l1-overview",
                            "Course and programming paradigm overview",
                            0L,
                            1_045_067L,
                        )
                    ),
                transcriptSegments = emptyList(),
            ),
            LearningCourseVideo(
                id = "stanford-ee263-lecture01",
                provider = "Stanford Engineering Everywhere",
                category = "Math / Optimization",
                courseTitle = "EE263 Linear Dynamical Systems",
                lectureTitle = "Lecture 1 - Overview",
                description =
                    "Introduces applied linear algebra, linear dynamical systems, estimation, filtering, and real engineering applications.",
                sourceUri = "https://see.stanford.edu/videos/courses/see/EE263/EE263-lecture01.mp4",
                sourcePageUri = "https://see.stanford.edu/Course/EE263/72",
                licenseNote =
                    "Stanford SEE content is generally CC BY-NC-SA 4.0 unless otherwise indicated.",
                durationMs = 4_605_767L,
                chapters =
                    listOf(
                        chapter("ee263-l1-intro", "Course overview", 0L, 1_265_000L),
                        chapter(
                            "ee263-l1-systems",
                            "Overview of linear dynamical systems",
                            1_265_000L,
                            1_755_000L,
                        ),
                        chapter(
                            "ee263-l1-motivation",
                            "Why study linear systems?",
                            1_755_000L,
                            2_529_000L,
                        ),
                        chapter(
                            "ee263-l1-examples",
                            "Engineering examples",
                            2_529_000L,
                            3_457_000L,
                        ),
                        chapter(
                            "ee263-l1-estimation",
                            "Estimation and filtering",
                            3_457_000L,
                            4_058_000L,
                        ),
                        chapter(
                            "ee263-l1-linear-functions",
                            "Linear functions",
                            4_058_000L,
                            4_605_767L,
                        ),
                    ),
                transcriptSegments = emptyList(),
            ),
        )

    val categories = listOf("All", "Programming", "AI / Machine Learning", "Math / Optimization")
    val defaultVideo = videos.first()

    private fun chapter(id: String, title: String, startMs: Long, endMs: Long) =
        LearningChapter(
            id = id,
            title = title,
            startMs = startMs,
            endMs = endMs,
            summary = title,
        )
}

object LearningCourseFilter {
    fun filter(
        videos: List<LearningCourseVideo>,
        category: String,
        query: String,
    ): List<LearningCourseVideo> {
        val normalizedQuery = query.trim().lowercase()
        return videos.filter { video ->
            val matchesCategory = category == "All" || video.category == category
            val matchesQuery =
                normalizedQuery.isBlank() ||
                    listOf(
                            video.courseTitle,
                            video.lectureTitle,
                            video.description,
                            video.provider,
                            video.category,
                        )
                        .any { it.lowercase().contains(normalizedQuery) }
            matchesCategory && matchesQuery
        }
    }
}
