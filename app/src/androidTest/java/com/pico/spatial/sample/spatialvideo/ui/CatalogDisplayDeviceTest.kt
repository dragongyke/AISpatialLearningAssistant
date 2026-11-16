package com.pico.spatial.sample.spatialvideo.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.SampleLearningContent
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDisplayDeviceTest {
    private fun display(locale: Locale): CatalogDisplay {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return CatalogDisplay(context.createConfigurationContext(config).resources)
    }

    @Test
    fun allBuiltInTitlesHaveEnglishAndChineseDisplayForms() {
        val english = display(Locale.ENGLISH)
        val chinese = display(Locale.SIMPLIFIED_CHINESE)
        for (course in LearningCourseCatalog.videos) {
            assertEquals(course.courseTitle, english.courseTitle(course))
            assertEquals(course.lectureTitle, english.lectureTitle(course))
            assertEquals(course.description, english.description(course))
            assertEquals(course.licenseNote, english.licenseNote(course))
            assertEquals(
                course.toLearningVideo().title,
                english.videoTitle(course.toLearningVideo())
            )
            assertNotEquals(course.courseTitle, chinese.courseTitle(course))
            assertNotEquals(course.lectureTitle, chinese.lectureTitle(course))
            assertNotEquals(course.description, chinese.description(course))
            assertNotEquals(course.licenseNote, chinese.licenseNote(course))
            for (chapter in course.chapters) {
                assertEquals(chapter.title, english.chapterTitle(chapter))
                assertNotEquals(chapter.title, chinese.chapterTitle(chapter))
                assertEquals(chinese.chapterTitle(chapter), chinese.chapterSummary(chapter))
            }
        }
        for (category in LearningCourseCatalog.categories) {
            assertEquals(category, english.categoryTitle(category))
            assertNotEquals(category, chinese.categoryTitle(category))
        }
        for (chapter in SampleLearningContent.chapters) {
            assertNotEquals(chapter.title, chinese.chapterTitle(chapter))
            assertNotEquals(chapter.summary, chinese.chapterSummary(chapter))
        }
        assertNotEquals(
            SampleLearningContent.video.title,
            chinese.videoTitle(SampleLearningContent.video)
        )
    }

    @Test
    fun translatedSearchUsesStableCategoryKeysAndStillFindsOriginalTitles() {
        val display = display(Locale.forLanguageTag("zh-SG"))
        val course = LearningCourseCatalog.videos.single { it.id == "stanford-cs229-lecture01" }
        assertEquals(listOf(course), display.filterCourses(course.category, "机器学习"))
        assertEquals(listOf(course), display.filterCourses(course.category, "MACHINE LEARNING"))
        assertEquals(emptyList<Any>(), display.filterCourses("Programming", "CS229"))
        assertEquals("CS229 机器学习 · 第 1 讲：学习动机与应用", display.videoTitle(course.toLearningVideo()))
    }

    @Test
    fun historyDatesFollowWindowConfigurationNotTheProcessDefault() {
        val instant = 1_781_234_567_000L
        assertNotEquals(
            display(Locale.US).historyDate(instant),
            display(Locale.CHINA).historyDate(instant)
        )
    }

    @Test
    fun unrecognizedMetadataAndCustomTitlesArePreserved() {
        val display = display(Locale.SIMPLIFIED_CHINESE)
        val personal = "My notes: 我自己的标题"
        assertEquals(personal, display.videoTitle("legacy-id", personal))
        assertEquals(personal, display.videoTitle(LearningCourseCatalog.defaultVideo.id, personal))
        assertEquals(personal, display.categoryTitle(personal))
        val chapter = SampleLearningContent.chapters.first().copy(id = "custom", title = personal)
        assertEquals(personal, display.chapterTitle(chapter))
    }
}
