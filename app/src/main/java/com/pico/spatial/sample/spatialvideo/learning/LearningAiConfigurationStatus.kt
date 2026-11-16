package com.pico.spatial.sample.spatialvideo.learning

internal data class LearningAiConfigurationStatus(
    val modelConfigured: Boolean,
    val speechConfigured: Boolean,
) {
    val requiresNotice: Boolean
        get() = !modelConfigured || !speechConfigured

    companion object {
        // Use the same configuration checks as the active providers, without network requests.
        fun from(
            assistant: DoubaoAssistantConfiguration = DoubaoAssistantConfiguration(),
            vision: ArkVisionConfiguration = ArkVisionConfiguration(),
            speech: DoubaoSpeechConfiguration = DoubaoSpeechConfiguration(),
            streamingTts: DoubaoStreamingTtsConfiguration = DoubaoStreamingTtsConfiguration(),
            fallbackTts: DoubaoTtsConfiguration = DoubaoTtsConfiguration(),
        ) =
            LearningAiConfigurationStatus(
                modelConfigured = assistant.isConfigured && vision.isConfigured,
                speechConfigured =
                    speech.isConfigured && (streamingTts.isConfigured || fallbackTts.isConfigured),
            )
    }
}
