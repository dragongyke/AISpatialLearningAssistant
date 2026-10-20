package com.pico.spatial.sample.spatialvideo.learning

import com.pico.spatial.sample.spatialvideo.BuildConfig
import kotlinx.coroutines.channels.ReceiveChannel

internal class PreferredSpeechAnswerSpeaker(
    private val streaming: SpeechAnswerSpeaker,
    private val fallback: SpeechAnswerSpeaker,
) : SpeechAnswerSpeaker {
    override val isConfigured
        get() = streaming.isConfigured || fallback.isConfigured

    override val readinessMessage
        get() =
            if (streaming.isConfigured) streaming.readinessMessage else fallback.readinessMessage

    override suspend fun withSession(block: suspend () -> Unit) {
        if (streaming.isConfigured) streaming.withSession(block) else fallback.withSession(block)
    }

    override suspend fun speak(text: String, onPlaybackStarted: () -> Unit): SpeechPlaybackResult {
        if (streaming.isConfigured) {
            val result = streaming.speak(text, onPlaybackStarted)
            if (result.wasPlayed) return result
        }
        return fallback.speak(text, onPlaybackStarted)
    }

    override suspend fun speakStream(
        text: ReceiveChannel<String>,
        isCurrent: () -> Boolean,
        onFirstText: () -> Unit,
        onPlaybackStarted: () -> Unit,
        onResult: (SpeechPlaybackResult) -> Unit,
    ) {
        // A partially consumed stream cannot be replayed safely through another provider.
        val preferred = if (streaming.isConfigured) streaming else fallback
        preferred.speakStream(text, isCurrent, onFirstText, onPlaybackStarted, onResult)
    }

    override fun stop() {
        streaming.stop()
        fallback.stop()
    }
}

internal data class DoubaoStreamingTtsConfiguration(
    val appId: String = BuildConfig.VOLCENGINE_SPEECH_APP_ID,
    val accessToken: String = BuildConfig.VOLCENGINE_SPEECH_ACCESS_TOKEN,
    val resourceId: String = BuildConfig.VOLCENGINE_TTS_RESOURCE_ID,
    val voiceType: String = BuildConfig.VOLCENGINE_TTS_VOICE_TYPE,
) {
    val isConfigured
        get() =
            appId.isNotBlank() &&
                accessToken.isNotBlank() &&
                resourceId.isNotBlank() &&
                voiceType.isNotBlank()

    val lowLatencyModel: String?
        get() =
            if (
                resourceId in
                    setOf(
                        "seed-tts-1.0",
                        "seed-tts-1.0-concurr",
                        "volc.service_type.10029",
                        "volc.service_type.10048"
                    )
            )
                "seed-tts-1.1"
            else null
}
