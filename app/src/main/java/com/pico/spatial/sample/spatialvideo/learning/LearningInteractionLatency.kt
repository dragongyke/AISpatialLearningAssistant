package com.pico.spatial.sample.spatialvideo.learning

data class LearningInteractionLatency(
    val asrFirstPartialMs: Long? = null,
    val asrFinalMs: Long? = null,
    val visionMs: Long? = null,
    val llmFirstTokenMs: Long? = null,
    val ttsFirstAudioMs: Long? = null,
    val totalMs: Long? = null,
    val responseFirstAudioMs: Long? = null,
) {
    fun compactLabel(): String =
        listOfNotNull(
                asrFirstPartialMs?.let { "ASR ${it}ms" },
                visionMs?.let { "Vision ${it}ms" },
                llmFirstTokenMs?.let { "AI ${it}ms" },
                ttsFirstAudioMs?.let { "Voice ${it}ms" },
                responseFirstAudioMs?.let { "Reply ${it}ms" },
            )
            .joinToString(" · ")
}

class LearningInteractionLatencyTracker(private val clockMs: () -> Long) {
    private var startedAtMs = 0L
    private var visionStartedAtMs: Long? = null
    private var llmStartedAtMs: Long? = null
    private var ttsStartedAtMs: Long? = null
    private var responseStartedAtMs: Long? = null
    private var latency = LearningInteractionLatency()

    @Synchronized
    fun begin() {
        startedAtMs = clockMs()
        visionStartedAtMs = null
        llmStartedAtMs = null
        ttsStartedAtMs = null
        responseStartedAtMs = null
        latency = LearningInteractionLatency()
    }

    @Synchronized
    fun markAsrPartial() {
        if (latency.asrFirstPartialMs == null) {
            latency = latency.copy(asrFirstPartialMs = elapsedSince(startedAtMs))
        }
    }

    @Synchronized
    fun markAsrFinal() {
        latency = latency.copy(asrFinalMs = elapsedSince(startedAtMs))
    }

    /** Called after ASR final (or typed submit); does not include the learner's speaking time. */
    @Synchronized
    fun beginResponse() {
        responseStartedAtMs = clockMs()
    }

    @Synchronized
    fun beginVision() {
        visionStartedAtMs = clockMs()
    }

    @Synchronized
    fun finishVision() {
        visionStartedAtMs?.let { latency = latency.copy(visionMs = elapsedSince(it)) }
        visionStartedAtMs = null
    }

    @Synchronized
    fun beginLlm() {
        llmStartedAtMs = clockMs()
    }

    @Synchronized
    fun markLlmFirstToken() {
        if (latency.llmFirstTokenMs == null) {
            llmStartedAtMs?.let { latency = latency.copy(llmFirstTokenMs = elapsedSince(it)) }
        }
    }

    @Synchronized
    fun beginTts() {
        if (ttsStartedAtMs == null) ttsStartedAtMs = clockMs()
    }

    @Synchronized
    fun markTtsFirstAudio() {
        if (latency.responseFirstAudioMs == null) {
            responseStartedAtMs?.let {
                latency = latency.copy(responseFirstAudioMs = elapsedSince(it))
            }
        }
        if (latency.ttsFirstAudioMs == null) {
            ttsStartedAtMs?.let { latency = latency.copy(ttsFirstAudioMs = elapsedSince(it)) }
        }
    }

    @Synchronized
    fun finish() {
        latency = latency.copy(totalMs = elapsedSince(startedAtMs))
    }

    @Synchronized fun snapshot(): LearningInteractionLatency = latency

    private fun elapsedSince(startMs: Long): Long = (clockMs() - startMs).coerceAtLeast(0L)
}
