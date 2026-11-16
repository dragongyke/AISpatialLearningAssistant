package com.pico.spatial.sample.spatialvideo.learning

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sample.spatialvideo.R
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningStatusLocaleTest {
    @Test
    fun localeChangeRefreshesOnceAndDiscardsAnEarlierProviderStatus() {
        val english = LocaleList(Locale.US)
        val chinese = LocaleList(Locale.forLanguageTag("zh-SG"), Locale.US)
        var refreshes = 0
        val observer = LearningStatusLocaleObserver(english) { refreshes++ }

        fun configure(requestedLocales: LocaleList, requestedFontScale: Float) {
            observer.onConfigurationChanged(
                Configuration().apply {
                    setLocales(requestedLocales)
                    fontScale = requestedFontScale
                }
            )
        }

        configure(english, 1.5f)
        assertEquals(0, refreshes)
        configure(chinese, 1.5f)
        configure(chinese, 1.0f)
        assertEquals(1, refreshes)
        assertEquals("已就绪", observer.messageFor(english, "Ready") { "已就绪" })
        assertEquals("已完成", observer.messageFor(chinese, "已完成") { "已就绪" })
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val strings =
            base
                .createConfigurationContext(
                    Configuration(base.resources.configuration).apply { setLocales(chinese) }
                )
                .learningStatusStrings()
        val timedOut = SpeechTranscriptionResult("", "No speech was recognized.")
        assertEquals(
            strings(R.string.learning_status_asr_failed),
            observer.recognitionMessage(english, timedOut, strings)
        )
    }

    @Test
    fun fallbackStatusUsesResourceLocaleNotQuestionLanguage() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        suspend fun status(resourceLocale: Locale, question: String): String {
            val context =
                base.createConfigurationContext(
                    Configuration(base.resources.configuration).apply { setLocale(resourceLocale) }
                )
            val assistant =
                DoubaoLearningAssistantEngine(
                    context.learningStatusStrings(),
                    configuration = DoubaoAssistantConfiguration(apiKey = ""),
                )
            return assistant
                .answer(question, 0L, emptyList(), LearningUiState(), null)
                .statusMessage
        }

        assertEquals(
            "Doubao is not configured. Answered using local course content.",
            status(Locale.US, "请解释这节课。"),
        )
        assertEquals(
            "豆包尚未配置，已使用本地课程内容回答。",
            status(Locale.forLanguageTag("zh-SG"), "Explain this lesson."),
        )
    }
}
