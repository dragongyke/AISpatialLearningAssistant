package com.pico.spatial.sample.spatialvideo.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageLocaleResourcesTest {
    @Test
    fun transcriptBadgeFollowsTheUiLocale() {
        assertEquals(
            "Estimated timing",
            resourcesFor(Locale.US).getString(R.string.stage_transcript_estimated)
        )
        assertEquals(
            "时间为估算值",
            resourcesFor(Locale.SIMPLIFIED_CHINESE).getString(R.string.stage_transcript_estimated)
        )
        assertEquals(
            "时间为估算值",
            resourcesFor(Locale.forLanguageTag("zh-SG"))
                .getString(R.string.stage_transcript_estimated)
        )
    }

    @Test
    fun signedSubtitleOffsetKeepsTheDirectionInBothLanguages() {
        val en = resourcesFor(Locale.US)
        val zh = resourcesFor(Locale.SIMPLIFIED_CHINESE)
        assertEquals(
            "Subtitles: +0.5 s",
            en.getString(R.string.stage_subtitle_offset_positive, 0.5)
        )
        assertEquals("字幕偏移：+0.5 秒", zh.getString(R.string.stage_subtitle_offset_positive, 0.5))
        assertEquals("Subtitles: -0.5 s", en.getString(R.string.stage_subtitle_offset, -0.5))
        assertEquals("字幕偏移：-0.5 秒", zh.getString(R.string.stage_subtitle_offset, -0.5))
    }

    private fun resourcesFor(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            context.createConfigurationContext(config).resources
        }
}
