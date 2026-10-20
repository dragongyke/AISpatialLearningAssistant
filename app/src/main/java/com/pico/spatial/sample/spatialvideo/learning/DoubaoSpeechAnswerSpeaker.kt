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
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.pico.spatial.sample.spatialvideo.BuildConfig
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal data class SpeechPlaybackResult(
    val statusMessage: String,
    val wasPlayed: Boolean,
)

internal interface SpeechAnswerSpeaker {
    val isConfigured: Boolean
    val readinessMessage: String

    suspend fun withSession(block: suspend () -> Unit) {
        block()
    }

    suspend fun speak(
        text: String,
        onPlaybackStarted: () -> Unit = {},
    ): SpeechPlaybackResult

    suspend fun speakStream(
        text: ReceiveChannel<String>,
        isCurrent: () -> Boolean,
        onFirstText: () -> Unit,
        onPlaybackStarted: () -> Unit,
        onResult: (SpeechPlaybackResult) -> Unit,
    ) {
        withSession {
            var first = true
            for (sentence in text) {
                if (!isCurrent()) break
                if (first) {
                    first = false
                    onFirstText()
                }
                val result = speak(sentence, onPlaybackStarted)
                if (isCurrent()) onResult(result)
            }
        }
    }

    fun stop()
}

internal class ActiveSpeechPlayback {
    private var stopAction: (() -> Unit)? = null

    fun register(action: () -> Unit) {
        val previous =
            synchronized(this) {
                val existing = stopAction
                stopAction = action
                existing
            }
        previous?.invoke()
    }

    fun clear(action: () -> Unit) {
        synchronized(this) {
            if (stopAction === action) {
                stopAction = null
            }
        }
    }

    fun stop() {
        val action = synchronized(this) { stopAction.also { stopAction = null } }
        action?.invoke()
    }
}

internal data class DoubaoTtsConfiguration(
    val appId: String = BuildConfig.VOLCENGINE_SPEECH_APP_ID,
    val accessToken: String = BuildConfig.VOLCENGINE_SPEECH_ACCESS_TOKEN,
    val voiceType: String = BuildConfig.VOLCENGINE_TTS_VOICE_TYPE,
) {
    val isConfigured: Boolean
        get() = appId.isNotBlank() && accessToken.isNotBlank() && voiceType.isNotBlank()
}

internal object SpeechTextSanitizer {
    private const val MAX_UTF8_BYTES = 900

    fun forPlayback(markdown: String): String {
        val plain =
            markdown
                .replace(Regex("[#*_>~]"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (plain.toByteArray(Charsets.UTF_8).size <= MAX_UTF8_BYTES) return plain
        val suffix = "."
        val byteBudget = MAX_UTF8_BYTES - suffix.toByteArray(Charsets.UTF_8).size
        val result = StringBuilder()
        var size = 0
        for (character in plain) {
            val bytes = character.toString().toByteArray(Charsets.UTF_8).size
            if (size + bytes > byteBudget) break
            result.append(character)
            size += bytes
        }
        return result.toString().trimEnd() + suffix
    }
}

internal class DoubaoSpeechAnswerSpeaker(
    context: Context,
    private val configuration: DoubaoTtsConfiguration = DoubaoTtsConfiguration(),
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build(),
) : SpeechAnswerSpeaker {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val activePlayback = ActiveSpeechPlayback()

    override val isConfigured: Boolean
        get() = configuration.isConfigured

    override val readinessMessage: String
        get() =
            if (isConfigured) {
                "Doubao voice reply is ready."
            } else {
                "Doubao TTS is not configured; AI replies will remain text-only."
            }

    override suspend fun speak(
        text: String,
        onPlaybackStarted: () -> Unit,
    ): SpeechPlaybackResult {
        if (!isConfigured) return SpeechPlaybackResult(readinessMessage, wasPlayed = false)
        val spokenText = SpeechTextSanitizer.forPlayback(text)
        if (spokenText.isBlank()) {
            return SpeechPlaybackResult("The AI answer contained no speakable text.", false)
        }
        return try {
            val audio = requestAudio(spokenText)
            val audioFile =
                withContext(Dispatchers.IO) {
                    File.createTempFile("doubao-answer-", ".mp3", appContext.cacheDir).apply {
                        writeBytes(audio)
                    }
                }
            try {
                playFile(audioFile, onPlaybackStarted)
                SpeechPlaybackResult("Doubao finished speaking the AI answer.", true)
            } finally {
                withContext(Dispatchers.IO) { audioFile.delete() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SpeechPlaybackResult(
                "Doubao TTS failed: ${error.safeTtsMessage()}",
                wasPlayed = false,
            )
        }
    }

    override fun stop() {
        mainHandler.post { activePlayback.stop() }
    }

    private suspend fun requestAudio(text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put(
                        "app",
                        JSONObject()
                            .put("appid", configuration.appId)
                            .put("token", configuration.accessToken)
                            .put("cluster", "volcano_tts"),
                    )
                    .put("user", JSONObject().put("uid", UUID.randomUUID().toString()))
                    .put(
                        "audio",
                        JSONObject()
                            .put("voice_type", configuration.voiceType)
                            .put("encoding", "mp3")
                            .put("speed_ratio", 1.0),
                    )
                    .put(
                        "request",
                        JSONObject()
                            .put("reqid", UUID.randomUUID().toString())
                            .put("text", text)
                            .put("operation", "query"),
                    )
            val request =
                Request.Builder()
                    .url(TTS_URL)
                    .header("Authorization", "Bearer;${configuration.accessToken}")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            executeRequest(request)
        }

    private suspend fun executeRequest(request: Request): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                val responseBody = it.body?.string().orEmpty()
                                check(it.isSuccessful) {
                                    "TTS HTTP ${it.code}: ${responseBody.take(200)}"
                                }
                                val json = JSONObject(responseBody)
                                check(json.optInt("code") == SUCCESS_CODE) {
                                    "TTS ${json.optInt("code")}: ${json.optString("message")}"
                                }
                                val encoded =
                                    json.optString("data").takeIf(String::isNotBlank)
                                        ?: error("TTS returned no audio data.")
                                Base64.decode(encoded, Base64.DEFAULT)
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resumeWith(result)
                        }
                    }
                }
            )
        }

    private suspend fun playFile(audioFile: File, onPlaybackStarted: () -> Unit) =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                val player = MediaPlayer()
                lateinit var stopAction: () -> Unit

                fun finish(error: Throwable? = null) {
                    if (!completed.compareAndSet(false, true)) return
                    activePlayback.clear(stopAction)
                    runCatching { player.stop() }
                    runCatching { player.release() }
                    if (continuation.isActive) {
                        when (error) {
                            null -> continuation.resume(Unit)
                            is CancellationException -> continuation.cancel(error)
                            else -> continuation.resumeWith(Result.failure(error))
                        }
                    }
                }

                stopAction = { finish(CancellationException("Speech playback was stopped.")) }
                activePlayback.register(stopAction)
                player.setOnCompletionListener { finish() }
                player.setOnErrorListener { _, what, extra ->
                    finish(IllegalStateException("Audio playback failed (${what}/${extra})."))
                    true
                }
                continuation.invokeOnCancellation { finish() }
                runCatching {
                        player.setDataSource(audioFile.absolutePath)
                        player.prepare()
                        player.start()
                        onPlaybackStarted()
                    }
                    .onFailure(::finish)
            }
        }

    private companion object {
        const val TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
        const val SUCCESS_CODE = 3000
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun Throwable.safeTtsMessage(): String =
    message.orEmpty().lineSequence().firstOrNull().orEmpty().take(120).ifBlank { "unknown error" }
