package com.pico.spatial.sample.spatialvideo.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.learning.LearningChapter
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseFilter
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseVideo
import com.pico.spatial.sample.spatialvideo.learning.LearningVideo
import com.pico.spatial.sample.spatialvideo.learning.SampleLearningContent
import java.text.DateFormat
import java.util.Date

@Composable
fun rememberCatalogDisplay(): CatalogDisplay {
    val resources = LocalContext.current.resources
    return remember(resources, LocalConfiguration.current) { CatalogDisplay(resources) }
}

/** Display-only metadata. Navigation, history and Markdown continue to use canonical values. */
class CatalogDisplay(private val resources: Resources) {
    fun categoryTitle(category: String): String =
        when (category) {
            "All" -> resources.getString(R.string.catalog_category_all)
            "Programming" -> resources.getString(R.string.catalog_category_programming)
            "AI / Machine Learning" -> resources.getString(R.string.catalog_category_ai)
            "Math / Optimization" -> resources.getString(R.string.catalog_category_math)
            else -> category
        }

    fun courseTitle(course: LearningCourseVideo): String =
        courseStrings[course.id]?.let { resources.getString(it.title) } ?: course.courseTitle

    fun lectureTitle(course: LearningCourseVideo): String =
        courseStrings[course.id]?.let { resources.getString(it.lecture) } ?: course.lectureTitle

    fun description(course: LearningCourseVideo): String =
        courseStrings[course.id]?.let { resources.getString(it.description) } ?: course.description

    fun licenseNote(course: LearningCourseVideo): String =
        if (
            course.id in courseStrings &&
                course.licenseNote == LearningCourseCatalog.defaultVideo.licenseNote
        )
            resources.getString(R.string.catalog_license_see)
        else course.licenseNote

    fun videoTitle(video: LearningVideo): String = videoTitle(video.id, video.title)

    fun videoTitle(videoId: String?, fallback: String): String {
        if (
            videoId == SampleLearningContent.video.id &&
                fallback == SampleLearningContent.video.title
        ) {
            return resources.getString(R.string.catalog_sample_title)
        }
        val course =
            LearningCourseCatalog.videos.firstOrNull { it.id == videoId } ?: return fallback
        if (fallback != course.toLearningVideo().title) return fallback
        return resources.getString(
            R.string.catalog_full_title,
            courseTitle(course),
            lectureTitle(course)
        )
    }

    fun chapterTitle(chapter: LearningChapter): String =
        chapterTitles[chapter.id]?.let { resources.getString(it) } ?: chapter.title

    fun chapterSummary(chapter: LearningChapter): String =
        when {
            chapter.summary == chapter.title -> chapterTitle(chapter)
            chapter.id == "chapter-context" ->
                resources.getString(R.string.catalog_sample_context_summary)
            chapter.id == "chapter-workflow" ->
                resources.getString(R.string.catalog_sample_workflow_summary)
            chapter.id == "chapter-review" ->
                resources.getString(R.string.catalog_sample_review_summary)
            else -> chapter.summary
        }

    fun filterCourses(category: String, query: String): List<LearningCourseVideo> =
        LearningCourseFilter.filter(LearningCourseCatalog.videos, category, query) { course ->
            listOf(
                courseTitle(course),
                lectureTitle(course),
                description(course),
                categoryTitle(course.category)
            )
        }

    fun historyDate(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                resources.configuration.locales[0]
            )
            .format(Date(timestampMs))
}

private data class CourseStrings(val title: Int, val lecture: Int, val description: Int)

private val courseStrings =
    mapOf(
        "stanford-cs106a-lecture01" to
            CourseStrings(
                R.string.catalog_cs106a_title,
                R.string.catalog_cs106a_lecture,
                R.string.catalog_cs106a_description,
            ),
        "stanford-cs229-lecture01" to
            CourseStrings(
                R.string.catalog_cs229_title,
                R.string.catalog_cs229_lecture,
                R.string.catalog_cs229_description,
            ),
        "stanford-ee364a-lecture01" to
            CourseStrings(
                R.string.catalog_ee364a_title,
                R.string.catalog_ee364a_lecture,
                R.string.catalog_ee364a_description,
            ),
        "stanford-cs107-lecture01" to
            CourseStrings(
                R.string.catalog_cs107_title,
                R.string.catalog_cs107_lecture,
                R.string.catalog_cs107_description,
            ),
        "stanford-ee263-lecture01" to
            CourseStrings(
                R.string.catalog_ee263_title,
                R.string.catalog_ee263_lecture,
                R.string.catalog_ee263_description,
            ),
    )

private val chapterTitles =
    mapOf(
        "cs106a-l1-welcome" to R.string.catalog_chapter_cs106a_l1_welcome,
        "cs106a-l1-staff" to R.string.catalog_chapter_cs106a_l1_staff,
        "cs106a-l1-methodology" to R.string.catalog_chapter_cs106a_l1_methodology,
        "cs106a-l1-placement" to R.string.catalog_chapter_cs106a_l1_placement,
        "cs106a-l1-logistics" to R.string.catalog_chapter_cs106a_l1_logistics,
        "cs106a-l1-grading" to R.string.catalog_chapter_cs106a_l1_grading,
        "cs106a-l1-extensions" to R.string.catalog_chapter_cs106a_l1_extensions,
        "cs106a-l1-exams" to R.string.catalog_chapter_cs106a_l1_exams,
        "cs106a-l1-grade-breakdown" to R.string.catalog_chapter_cs106a_l1_grade_breakdown,
        "cs106a-l1-honor-code" to R.string.catalog_chapter_cs106a_l1_honor_code,
        "cs106a-l1-karel" to R.string.catalog_chapter_cs106a_l1_karel,
        "cs229-l1-intro" to R.string.catalog_chapter_cs229_l1_intro,
        "cs229-l1-motivation" to R.string.catalog_chapter_cs229_l1_motivation,
        "cs229-l1-logistics" to R.string.catalog_chapter_cs229_l1_logistics,
        "cs229-l1-definition" to R.string.catalog_chapter_cs229_l1_definition,
        "cs229-l1-supervised" to R.string.catalog_chapter_cs229_l1_supervised,
        "cs229-l1-theory" to R.string.catalog_chapter_cs229_l1_theory,
        "cs229-l1-unsupervised" to R.string.catalog_chapter_cs229_l1_unsupervised,
        "cs229-l1-rl" to R.string.catalog_chapter_cs229_l1_rl,
        "ee364a-l1-intro" to R.string.catalog_chapter_ee364a_l1_intro,
        "ee364a-l1-convexity" to R.string.catalog_chapter_ee364a_l1_convexity,
        "ee364a-l1-modeling" to R.string.catalog_chapter_ee364a_l1_modeling,
        "ee364a-l1-applications" to R.string.catalog_chapter_ee364a_l1_applications,
        "cs107-l1-overview" to R.string.catalog_chapter_cs107_l1_overview,
        "ee263-l1-intro" to R.string.catalog_chapter_ee263_l1_intro,
        "ee263-l1-systems" to R.string.catalog_chapter_ee263_l1_systems,
        "ee263-l1-motivation" to R.string.catalog_chapter_ee263_l1_motivation,
        "ee263-l1-examples" to R.string.catalog_chapter_ee263_l1_examples,
        "ee263-l1-estimation" to R.string.catalog_chapter_ee263_l1_estimation,
        "ee263-l1-linear-functions" to R.string.catalog_chapter_ee263_l1_linear_functions,
        "chapter-context" to R.string.catalog_chapter_sample_context,
        "chapter-workflow" to R.string.catalog_chapter_sample_workflow,
        "chapter-review" to R.string.catalog_chapter_sample_review,
    )
