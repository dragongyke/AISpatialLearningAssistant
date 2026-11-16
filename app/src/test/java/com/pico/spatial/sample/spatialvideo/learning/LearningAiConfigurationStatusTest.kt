package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAiConfigurationStatusTest {
    private val assistant = DoubaoAssistantConfiguration("key", "text-model", "https://example.com")
    private val vision = ArkVisionConfiguration("key", "vision-model", "https://example.com")
    private val speech = DoubaoSpeechConfiguration("app", "token", "asr-resource")
    private val streamingTts =
        DoubaoStreamingTtsConfiguration("app", "token", "tts-resource", "voice")
    private val fallbackTts = DoubaoTtsConfiguration("app", "token", "voice")

    private fun status(
        text: DoubaoAssistantConfiguration = assistant,
        image: ArkVisionConfiguration = vision,
        asr: DoubaoSpeechConfiguration = speech,
        tts: DoubaoStreamingTtsConfiguration = streamingTts,
        fallback: DoubaoTtsConfiguration = fallbackTts,
    ) = LearningAiConfigurationStatus.from(text, image, asr, tts, fallback)

    @Test
    fun completeCloudConfigurationDoesNotNeedNotice() {
        assertFalse(status().requiresNotice)
    }

    @Test
    fun keylessReleaseNeedsBothKindsOfConfiguration() {
        val result =
            status(
                text = assistant.copy(apiKey = ""),
                image = vision.copy(apiKey = ""),
                asr = speech.copy(appId = "", accessToken = ""),
                tts = streamingTts.copy(appId = "", accessToken = ""),
                fallback = fallbackTts.copy(appId = "", accessToken = ""),
            )
        assertTrue(result.requiresNotice)
        assertFalse(result.modelConfigured)
        assertFalse(result.speechConfigured)
    }

    @Test
    fun modelOnlyBuildReportsSpeechMissing() {
        val result = status(asr = speech.copy(accessToken = " "))
        assertTrue(result.modelConfigured)
        assertFalse(result.speechConfigured)
        assertTrue(result.requiresNotice)
    }

    @Test
    fun speechOnlyBuildReportsModelMissing() {
        val result = status(text = assistant.copy(apiKey = " "))
        assertFalse(result.modelConfigured)
        assertTrue(result.speechConfigured)
        assertTrue(result.requiresNotice)
    }

    @Test
    fun keyWithoutTextOrVisionModelStillNeedsNotice() {
        assertTrue(status(text = assistant.copy(model = "")).requiresNotice)
        assertTrue(status(image = vision.copy(model = "")).requiresNotice)
    }

    @Test
    fun speechTokenWithoutAppIdStillNeedsNotice() {
        assertFalse(status(asr = speech.copy(appId = "")).speechConfigured)
    }

    @Test
    fun asrWithoutEitherTtsProviderStillNeedsNotice() {
        val result =
            status(
                tts = streamingTts.copy(accessToken = ""),
                fallback = fallbackTts.copy(accessToken = ""),
            )
        assertFalse(result.speechConfigured)
        assertTrue(result.requiresNotice)
    }

    @Test
    fun configuredFallbackTtsRemainsUsable() {
        assertFalse(status(tts = streamingTts.copy(resourceId = "")).requiresNotice)
    }
}
