/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

data class SpeechTranscriptionResult(
    val text: String,
    val statusMessage: String,
)

interface SpeechQuestionTranscriber {
    val isReady: Boolean
    val readinessMessage: String

    suspend fun prepare(): Result<Unit>

    suspend fun transcribeOnce(
        maxListenMs: Long = 6_500L,
        onPartialResult: (String) -> Unit = {},
    ): SpeechTranscriptionResult
}

internal class DistinctSpeechPartialEmitter(private val onPartialResult: (String) -> Unit) {
    private var lastText = ""

    fun emit(text: String) {
        val normalized = text.trim()
        if (normalized.isNotBlank() && normalized != lastText) {
            lastText = normalized
            onPartialResult(normalized)
        }
    }
}

class VoskSpeechQuestionTranscriber(private val context: Context) : SpeechQuestionTranscriber {
    private val modelInstaller = VoskModelInstaller(context)

    val modelDirectory: File?
        get() = modelInstaller.modelDirectory.takeIf(VoskModelLayout::isComplete)

    val modelHint: String
        get() = "The bundled Chinese speech model is being prepared. Try voice input again shortly."

    override val isReady: Boolean
        get() = modelDirectory != null

    override val readinessMessage: String
        get() = if (isReady) "Bundled offline ASR is ready." else modelHint

    suspend fun ensureModelInstalled(): Result<File> = modelInstaller.ensureInstalled()

    override suspend fun prepare(): Result<Unit> = ensureModelInstalled().map {}

    override suspend fun transcribeOnce(
        maxListenMs: Long,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult {
        val directory =
            modelDirectory
                ?: modelInstaller.ensureInstalled().getOrNull()
                ?: return SpeechTranscriptionResult(
                    text = "",
                    statusMessage = "The bundled ASR model could not be installed.",
                )
        return runCatching {
                withContext(Dispatchers.IO) {
                    val model = Model(directory.absolutePath)
                    val recognizer =
                        try {
                            Recognizer(model, SAMPLE_RATE)
                        } catch (error: Exception) {
                            model.close()
                            throw error
                        }
                    withContext(Dispatchers.Main.immediate) {
                        listenWithSpeechService(
                            model,
                            recognizer,
                            maxListenMs,
                            onPartialResult,
                        )
                    }
                }
            }
            .getOrElse { error ->
                SpeechTranscriptionResult(
                    text = "",
                    statusMessage =
                        "ASR failed: ${error.message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120)}",
                )
            }
    }

    private suspend fun listenWithSpeechService(
        model: Model,
        recognizer: Recognizer,
        maxListenMs: Long,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        var latestText = ""
        val speechService = SpeechService(recognizer, SAMPLE_RATE)
        val partialEmitter = DistinctSpeechPartialEmitter(onPartialResult)
        var timeoutRunnable: Runnable? = null

        fun finish(text: String, status: String) {
            if (!completed.compareAndSet(false, true)) return
            timeoutRunnable?.let(handler::removeCallbacks)
            runCatching { speechService.stop() }
            speechService.shutdown()
            recognizer.close()
            model.close()
            if (continuation.isActive) {
                continuation.resume(SpeechTranscriptionResult(text.trim(), status))
            }
        }

        val listener =
            object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    parseSpeechText(hypothesis, "partial").takeIf(String::isNotBlank)?.let {
                        latestText = it
                        partialEmitter.emit(it)
                    }
                }

                override fun onResult(hypothesis: String?) {
                    latestText = parseSpeechText(hypothesis, "text").ifBlank { latestText }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val finalText = parseSpeechText(hypothesis, "text").ifBlank { latestText }
                    finish(
                        finalText,
                        if (finalText.isBlank()) "No speech recognized." else "Transcribed."
                    )
                }

                override fun onError(exception: Exception?) {
                    finish("", "ASR failed: ${exception?.message.orEmpty()}")
                }

                override fun onTimeout() {
                    finish(
                        latestText,
                        if (latestText.isBlank()) "No speech recognized." else "Transcribed."
                    )
                }
            }

        continuation.invokeOnCancellation { finish(latestText, "Speech recognition cancelled.") }
        speechService.startListening(listener)
        timeoutRunnable = Runnable {
            if (!completed.get()) {
                speechService.stop()
                finish(
                    latestText,
                    if (latestText.isBlank()) "No speech recognized." else "Transcribed."
                )
            }
        }
        handler.postDelayed(requireNotNull(timeoutRunnable), maxListenMs)
    }

    private companion object {
        const val DEFAULT_MAX_LISTEN_MS = 6_500L
        const val SAMPLE_RATE = 16_000.0f
    }
}

private fun parseSpeechText(json: String?, key: String): String =
    runCatching { JSONObject(json.orEmpty()).optString(key) }.getOrDefault("")
