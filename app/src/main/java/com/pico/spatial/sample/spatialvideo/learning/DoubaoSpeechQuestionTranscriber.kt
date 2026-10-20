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

import android.app.Application
import android.content.Context
import com.bytedance.speech.speechengine.SpeechEngine
import com.bytedance.speech.speechengine.SpeechEngineDefines
import com.bytedance.speech.speechengine.SpeechEngineGenerator
import com.pico.spatial.sample.spatialvideo.BuildConfig
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class DoubaoSpeechConfiguration(
    val appId: String = BuildConfig.VOLCENGINE_SPEECH_APP_ID,
    val accessToken: String = BuildConfig.VOLCENGINE_SPEECH_ACCESS_TOKEN,
    val resourceId: String = BuildConfig.VOLCENGINE_ASR_RESOURCE_ID,
) {
    val isConfigured: Boolean
        get() = appId.isNotBlank() && accessToken.isNotBlank() && resourceId.isNotBlank()
}

internal val DoubaoSpeechEngineMutex = Mutex()

internal class PreferredSpeechQuestionTranscriber(
    private val online: DoubaoSpeechQuestionTranscriber,
    private val offline: VoskSpeechQuestionTranscriber,
) : SpeechQuestionTranscriber {
    override val isReady: Boolean
        get() = online.isReady || offline.isReady

    override val readinessMessage: String
        get() =
            if (online.isReady) {
                "Doubao online bilingual speech recognition is ready."
            } else {
                offline.readinessMessage
            }

    override suspend fun prepare(): Result<Unit> =
        if (online.isReady) Result.success(Unit) else offline.prepare()

    override suspend fun transcribeOnce(
        maxListenMs: Long,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult =
        if (online.isReady) online.transcribeOnce(maxListenMs, onPartialResult)
        else offline.transcribeOnce(maxListenMs, onPartialResult)
}

@Suppress("DEPRECATION")
internal class DoubaoSpeechQuestionTranscriber(
    context: Context,
    private val configuration: DoubaoSpeechConfiguration = DoubaoSpeechConfiguration(),
) : SpeechQuestionTranscriber {
    private val application = context.applicationContext as Application

    override val isReady: Boolean
        get() = configuration.isConfigured

    override val readinessMessage: String
        get() =
            if (isReady) {
                "Doubao online bilingual speech recognition is ready."
            } else {
                "Doubao ASR is not configured; the bundled offline model will be used."
            }

    override suspend fun prepare(): Result<Unit> =
        if (isReady) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Doubao ASR credentials are not configured."))
        }

    override suspend fun transcribeOnce(
        maxListenMs: Long,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult {
        if (!isReady) return SpeechTranscriptionResult("", readinessMessage)
        return DoubaoSpeechEngineMutex.withLock {
            withContext(Dispatchers.IO) { transcribeWithEngine(maxListenMs, onPartialResult) }
        }
    }

    private suspend fun transcribeWithEngine(
        maxListenMs: Long,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult {
        check(SpeechEngineGenerator.PrepareEnvironment(application, application)) {
            "Doubao SpeechEngine environment setup failed."
        }
        val engine = SpeechEngineGenerator.getInstance()
        engine.setContext(application)
        val handle = engine.createEngine()
        check(handle != 0L) { "Doubao ASR engine creation failed." }
        val latestText = AtomicReference("")
        return try {
            configureEngine(engine, handle)
            val initCode = engine.initEngine(handle)
            check(initCode == SpeechEngineDefines.ERR_NO_ERROR) {
                "Doubao ASR initialization failed (${initCode})."
            }
            withTimeoutOrNull(maxListenMs) {
                awaitRecognition(engine, handle, latestText, onPartialResult)
            }
                ?: SpeechTranscriptionResult(
                    text = latestText.get(),
                    statusMessage =
                        if (latestText.get().isBlank()) {
                            "Doubao ASR timed out without recognizing speech."
                        } else {
                            "Doubao ASR transcribed the latest speech."
                        },
                )
        } catch (error: Exception) {
            SpeechTranscriptionResult(
                text = "",
                statusMessage = "Doubao ASR failed: ${error.safeSpeechMessage()}",
            )
        } finally {
            runCatching {
                engine.sendDirective(handle, SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "")
            }
            runCatching { engine.destroyEngine(handle) }
        }
    }

    private fun configureEngine(engine: SpeechEngine, handle: Long) {
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING,
            SpeechEngineDefines.ASR_ENGINE,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_RECORDER_TYPE_STRING,
            SpeechEngineDefines.RECORDER_TYPE_RECORDER,
        )
        engine.setOptionInt(
            handle,
            SpeechEngineDefines.PARAMS_KEY_RECORDER_PRESET_INT,
            SpeechEngineDefines.RECORDER_PRESET_VOICE_COMMUNICATION,
        )
        engine.setOptionInt(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_WORK_MODE_INT,
            SpeechEngineDefines.ASR_WORK_MODE_ONLINE,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_ADDRESS_STRING,
            ASR_ADDRESS,
        )
        engine.setOptionString(handle, SpeechEngineDefines.PARAMS_KEY_ASR_URI_STRING, ASR_URI)
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING,
            configuration.appId,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING,
            configuration.accessToken,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_RESOURCE_ID_STRING,
            configuration.resourceId,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_UID_STRING,
            UUID.randomUUID().toString(),
        )
        engine.setOptionInt(
            handle,
            SpeechEngineDefines.PARAMS_KEY_PROTOCOL_TYPE_INT,
            SpeechEngineDefines.PROTOCOL_TYPE_SEED,
        )
        engine.setOptionInt(handle, SpeechEngineDefines.PARAMS_KEY_SAMPLE_RATE_INT, SAMPLE_RATE)
        engine.setOptionInt(handle, SpeechEngineDefines.PARAMS_KEY_CHANNEL_NUM_INT, CHANNEL_COUNT)
        engine.setOptionBoolean(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ENABLE_CHECK_RECORD_PERMISSION_BOOL,
            true,
        )
        engine.setOptionBoolean(handle, SpeechEngineDefines.PARAMS_KEY_ASR_AUTO_STOP_BOOL, true)
        engine.setOptionBoolean(handle, SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_ITN_BOOL, true)
        engine.setOptionBoolean(handle, SpeechEngineDefines.PARAMS_KEY_ASR_SHOW_PUNC_BOOL, true)
        engine.setOptionBoolean(handle, SpeechEngineDefines.PARAMS_KEY_ASR_SHOW_UTTER_BOOL, true)
        engine.setOptionInt(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_VAD_START_SILENCE_TIME_INT,
            START_SILENCE_TIMEOUT_MS,
        )
        engine.setOptionInt(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_VAD_END_SILENCE_TIME_INT,
            END_SILENCE_TIMEOUT_MS,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_RESULT_TYPE_STRING,
            SpeechEngineDefines.ASR_RESULT_TYPE_FULL,
        )
        engine.setOptionString(
            handle,
            SpeechEngineDefines.PARAMS_KEY_ASR_REQ_PARAMS_STRING,
            requestParameters(),
        )
    }

    private suspend fun awaitRecognition(
        engine: SpeechEngine,
        handle: Long,
        latestText: AtomicReference<String>,
        onPartialResult: (String) -> Unit,
    ): SpeechTranscriptionResult = suspendCancellableCoroutine { continuation ->
        val partialEmitter = DistinctSpeechPartialEmitter(onPartialResult)
        fun complete(text: String, status: String) {
            if (continuation.isActive) {
                continuation.resume(SpeechTranscriptionResult(text.trim(), status))
            }
        }

        engine.setListener { messageType, data, size ->
            val payload =
                data
                    ?.let { bytes ->
                        val safeSize = size.coerceIn(0, bytes.size)
                        bytes.copyOf(safeSize)
                    }
                    ?.toString(Charsets.UTF_8)
                    .orEmpty()
            when (messageType) {
                SpeechEngineDefines.MESSAGE_TYPE_PARTIAL_RESULT,
                SpeechEngineDefines.MESSAGE_TYPE_ALL_PARTIAL_RESULT ->
                    DoubaoSpeechResultParser.extractText(payload)?.let { partial ->
                        latestText.set(partial)
                        partialEmitter.emit(partial)
                    }
                SpeechEngineDefines.MESSAGE_TYPE_FINAL_RESULT -> {
                    val text =
                        DoubaoSpeechResultParser.extractText(payload).orEmpty().ifBlank {
                            latestText.get()
                        }
                    complete(
                        text,
                        if (text.isBlank()) {
                            "Doubao ASR did not recognize speech."
                        } else {
                            "Doubao online bilingual ASR transcribed the question."
                        },
                    )
                }
                SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR ->
                    complete("", "Doubao ASR engine error: ${payload.take(160)}")
                SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP ->
                    complete(
                        latestText.get(),
                        if (latestText.get().isBlank()) {
                            "Doubao ASR stopped without recognizing speech."
                        } else {
                            "Doubao online bilingual ASR transcribed the question."
                        },
                    )
            }
        }
        val startCode = engine.sendDirective(handle, SpeechEngineDefines.DIRECTIVE_START_ENGINE, "")
        if (startCode != SpeechEngineDefines.ERR_NO_ERROR) {
            complete("", "Doubao ASR failed to start (${startCode}).")
        }
    }

    private fun requestParameters(): String {
        val correction =
            JSONObject()
                .put(
                    "correct_words",
                    JSONObject()
                        .put("deep seek", "DeepSeek")
                        .put("deep sick", "DeepSeek")
                        .put("pico", "PICO"),
                )
                .toString()
        return JSONObject()
            .put("force_to_speech_time", 0)
            .put("end_window_size", END_SILENCE_TIMEOUT_MS)
            .put("context", correction)
            .toString()
    }

    private companion object {
        const val ASR_ADDRESS = "wss://openspeech.bytedance.com"
        const val ASR_URI = "/api/v3/sauc/bigmodel"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
        const val START_SILENCE_TIMEOUT_MS = 5_000
        const val END_SILENCE_TIMEOUT_MS = 600
    }
}

internal object DoubaoSpeechResultParser {
    fun extractText(payload: String): String? =
        runCatching {
                val root = JSONObject(payload)
                findText(root.opt("result")) ?: findText(root)
            }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun findText(value: Any?): String? =
        when (value) {
            is JSONObject -> {
                value.optString("text").takeIf(String::isNotBlank)
                    ?: value
                        .keys()
                        .asSequence()
                        .mapNotNull { key ->
                            when (val child = value.opt(key)) {
                                is JSONObject,
                                is JSONArray -> findText(child)
                                else -> null
                            }
                        }
                        .firstOrNull()
            }
            is JSONArray ->
                (0 until value.length())
                    .asSequence()
                    .mapNotNull { index -> findText(value.opt(index)) }
                    .firstOrNull()
            is String -> value.takeIf(String::isNotBlank)
            else -> null
        }
}

private fun Throwable.safeSpeechMessage(): String =
    message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120).ifBlank { "unknown error" }
