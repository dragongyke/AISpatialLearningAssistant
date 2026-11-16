package com.pico.spatial.sample.spatialvideo.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.BuildConfig
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.learning.LearningAiConfigurationStatus
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningAiSetupResourcesTest {
    @Test
    fun noticeFollowsHeadsetLanguageAndKeepsBuildInstructions() {
        for ((locale, title, button) in
            listOf(
                Triple(Locale.US, "Some AI features are unavailable", "Continue"),
                Triple(Locale.SIMPLIFIED_CHINESE, "部分 AI 功能暂不可用", "继续"),
                Triple(Locale.forLanguageTag("zh-SG"), "部分 AI 功能暂不可用", "继续"),
            )) {
            val resources = resourcesFor(locale)
            assertEquals(title, resources.getString(R.string.catalog_ai_setup_title))
            assertEquals(button, resources.getString(R.string.catalog_ai_setup_continue))
            val instructions = resources.getString(R.string.catalog_ai_setup_instructions)
            assertTrue(instructions.contains("ai.local.properties.example"))
            assertTrue(instructions.contains("ai.local.properties"))
            assertTrue(instructions.contains("Debug APK"))
            assertTrue(instructions.contains("Release"))
            for (missing in
                listOf(
                    R.string.catalog_ai_setup_missing_both,
                    R.string.catalog_ai_setup_missing_model,
                    R.string.catalog_ai_setup_missing_speech,
                )) {
                val description = resources.getString(missing)
                val message =
                    resources.getString(R.string.catalog_ai_setup_limitations, description)
                assertTrue(message.contains(description))
                assertFalse(message.contains("%1\$s"))
            }
        }
    }

    @Test
    fun installedKeylessBuildCannotBypassNotice() {
        val state = LearningAiConfigurationStatus.from()
        if (BuildConfig.ARK_API_KEY.isBlank()) {
            assertFalse(state.modelConfigured)
            assertTrue(state.requiresNotice)
        }
        if (
            BuildConfig.VOLCENGINE_SPEECH_APP_ID.isBlank() ||
                BuildConfig.VOLCENGINE_SPEECH_ACCESS_TOKEN.isBlank()
        ) {
            assertFalse(state.speechConfigured)
            assertTrue(state.requiresNotice)
        }
    }

    private fun resourcesFor(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            context.createConfigurationContext(config).resources
        }
}
