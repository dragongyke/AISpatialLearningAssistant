package com.pico.spatial.sample.spatialvideo.learning

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.pico.spatial.sample.spatialvideo.R

fun interface LearningStatusStrings {
    operator fun invoke(@StringRes id: Int, vararg args: Any): String
}

internal fun Context.learningStatusStrings() = LearningStatusStrings { id, args ->
    getString(id, *args)
}

internal class LearningStatusLocaleObserver(
    initialLocales: LocaleList,
    private val onLocaleChange: () -> Unit,
) : ComponentCallbacks {
    @Volatile
    var locales: LocaleList = initialLocales
        private set

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (locales == newConfig.locales) return
        locales = newConfig.locales
        onLocaleChange()
    }

    @Deprecated("This locale observer does not manage memory.") override fun onLowMemory() = Unit

    // Do not republish a provider's old status after its asynchronous work completes.
    fun messageFor(startLocales: LocaleList, message: String, fallback: () -> String): String =
        if (locales == startLocales) message else fallback()

    internal fun recognitionMessage(
        startLocales: LocaleList,
        result: SpeechTranscriptionResult,
        strings: LearningStatusStrings
    ): String =
        messageFor(startLocales, result.statusMessage) {
            strings(
                if (result.text.isBlank()) R.string.learning_status_asr_failed
                else R.string.learning_status_recognized
            )
        }
}
