package com.pico.spatial.sample.spatialvideo.learning

import android.util.Log
import com.pico.spatial.sample.spatialvideo.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

internal data class DoubaoAssistantConfiguration(
    val apiKey: String = BuildConfig.ARK_API_KEY,
    val model: String = BuildConfig.ARK_TEXT_MODEL,
    val baseUrl: String = BuildConfig.ARK_BASE_URL,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

internal class DoubaoLearningAssistantEngine(
    private val configuration: DoubaoAssistantConfiguration = DoubaoAssistantConfiguration(),
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build(),
    private val onObservation: (ArkAnswerObservation) -> Unit = {
        Log.i("ArkAnswer", it.toString())
    },
) : LearningAssistantEngine {
    @Volatile private var interactiveServiceTier = "fast"

    override val isConfigured: Boolean
        get() = configuration.isConfigured

    override suspend fun answer(
        question: String,
        timestampMs: Long,
        previousAnswers: List<LearningQuestionAnswer>,
        state: LearningUiState,
        visualContext: String?,
        visualFrames: List<CapturedVideoFrame>,
        onPartialAnswer: (String) -> Unit,
    ): LearningAssistantAnswer {
        currentCoroutineContext().ensureActive()
        val frames = ArkAnswerRequestPolicy.boundedFrames(visualFrames)
        val context = LearningAssistantText.findContext(timestampMs, state)
        val contextStartMs = context.minOfOrNull { it.startMs } ?: max(0L, timestampMs - 60_000L)
        val contextEndMs =
            context.maxOfOrNull { it.endMs } ?: min(timestampMs + 30_000L, state.video.durationMs)
        val fallback = {
            LearningAssistantText.localAnswerFallback(
                question = question,
                timestampMs = timestampMs,
                context = context,
                previousAnswers = previousAnswers,
                state = state,
            )
        }
        val result =
            if (isConfigured) {
                requestStreamingCompletion(
                    systemPrompt = LearningAssistantText.answerSystemPrompt(state.teachingMode),
                    userPrompt =
                        LearningAssistantText.answerUserPrompt(
                            question = question,
                            timestampMs = timestampMs,
                            context = context,
                            previousAnswers = previousAnswers,
                            state = state,
                            visualContext =
                                ArkAnswerRequestPolicy.visualEvidence(visualContext, frames),
                        ),
                    visualFrames = frames,
                    maxTokens = ANSWER_MAX_TOKENS,
                    fallback = fallback,
                    onPartialAnswer = onPartialAnswer,
                )
            } else {
                val text = fallback()
                onPartialAnswer(text)
                ArkTextResult(
                    text = text,
                    mode = AssistantResponseMode.Fallback,
                    statusMessage = "豆包模型尚未配置，已使用本地课程内容回答。",
                )
            }
        val questionAnswer =
            LearningQuestionAnswer(
                id = "qa-${System.currentTimeMillis()}-${previousAnswers.size}",
                timestampMs = timestampMs,
                questionText = question,
                answerText = result.text,
                contextStartMs = contextStartMs,
                contextEndMs = contextEndMs,
                createdAtMs = System.currentTimeMillis(),
            )
        return LearningAssistantAnswer(questionAnswer, result.mode, result.statusMessage)
    }

    override suspend fun generateSummary(
        state: LearningUiState,
        existingMarkdown: String,
        progressMs: Long
    ): LearningAssistantSummary {
        val fallback = { LearningAssistantText.localSummaryFallback(state) }
        val result =
            if (isConfigured) {
                requestCompletion(
                    systemPrompt = LearningAssistantText.summarySystemPrompt(),
                    userPrompt =
                        LearningAssistantText.summaryUserPrompt(
                            state,
                            existingMarkdown,
                            progressMs
                        ),
                    maxTokens = SUMMARY_MAX_TOKENS,
                    fallback = fallback,
                )
            } else {
                ArkTextResult(
                    text = fallback(),
                    mode = AssistantResponseMode.Fallback,
                    statusMessage = "豆包模型尚未配置，已整理本地学习笔记。",
                )
            }
        return LearningAssistantSummary(result.text, result.mode, result.statusMessage)
    }

    override fun findChapter(
        timestampMs: Long,
        chapters: List<LearningChapter>,
    ): LearningChapter =
        chapters.firstOrNull { timestampMs >= it.startMs && timestampMs < it.endMs }
            ?: chapters.filter { it.startMs <= timestampMs }.maxByOrNull { it.startMs }
            ?: chapters.minByOrNull { it.startMs }
            ?: SampleLearningContent.chapters.first()

    private suspend fun requestStreamingCompletion(
        systemPrompt: String,
        userPrompt: String,
        visualFrames: List<CapturedVideoFrame>,
        maxTokens: Int,
        fallback: () -> String,
        onPartialAnswer: (String) -> Unit,
    ): ArkTextResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val coroutineContext = currentCoroutineContext()
            var firstContentMs: Long? = null
            var serviceTier: String? = null
            var requestedTier = interactiveServiceTier
            var attempts = 0
            var fastTierFallback = false
            var httpStatus: Int? = null
            var httpFailure: ArkHttpFailure? = null
            var outcome = "error"
            fun elapsedMs() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            try {
                var result: ArkTextResult? = null
                while (result == null) {
                    attempts++
                    try {
                        result =
                            executeRequest(
                                systemPrompt,
                                userPrompt,
                                maxTokens,
                                stream = true,
                                visualFrames = visualFrames,
                                serviceTier = requestedTier,
                                timeoutNanos =
                                    TimeUnit.MILLISECONDS.toNanos(
                                        ArkAnswerRequestPolicy.ANSWER_TIMEOUT_MS
                                    ) - (System.nanoTime() - startedAt),
                            ) { response ->
                                httpStatus = response.code
                                val responseBody = response.body ?: error("豆包返回了空响应。")
                                if (!response.isSuccessful) throw ArkHttpFailure.from(response)
                                val answer = StringBuilder()
                                val source = responseBody.source()
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val line = source.readUtf8Line() ?: break
                                    if (line.trim() == "data: [DONE]") break
                                    ArkStreamingResponseParser.extractServiceTier(line)?.let {
                                        serviceTier = it
                                    }
                                    ArkStreamingResponseParser.extractDelta(line)?.let { delta ->
                                        coroutineContext.ensureActive()
                                        if (firstContentMs == null && delta.isNotBlank())
                                            firstContentMs = elapsedMs()
                                        answer.append(delta)
                                        onPartialAnswer(answer.toString())
                                    }
                                }
                                coroutineContext.ensureActive()
                                val text = answer.toString().trim()
                                check(text.isNotBlank()) { "豆包没有返回回答内容。" }
                                outcome = "live"
                                ArkTextResult(
                                    text = text,
                                    mode = AssistantResponseMode.Live,
                                    statusMessage = "豆包已结合当前课程内容完成回答。",
                                )
                            }
                    } catch (failure: ArkHttpFailure) {
                        if (
                            requestedTier == "fast" &&
                                attempts == 1 &&
                                firstContentMs == null &&
                                failure.fastUnavailable
                        ) {
                            interactiveServiceTier = "auto"
                            requestedTier = "auto"
                            fastTierFallback = true
                        } else {
                            throw failure
                        }
                    }
                }
                result
            } catch (cancelled: CancellationException) {
                outcome = "cancelled"
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                httpFailure = error as? ArkHttpFailure
                val text = fallback()
                onPartialAnswer(text)
                ArkTextResult(
                    text = text,
                    mode = AssistantResponseMode.Error,
                    statusMessage = "豆包暂时不可用（${error.safeArkMessage()}），已使用本地课程内容回答。",
                )
            } finally {
                runCatching {
                    onObservation(
                        ArkAnswerObservation(
                            elapsedMs(),
                            firstContentMs,
                            visualFrames.size,
                            serviceTier,
                            outcome = outcome,
                            requestedServiceTier = requestedTier,
                            httpStatus = httpStatus,
                            errorCode = httpFailure?.code,
                            errorType = httpFailure?.type,
                            errorParameter = httpFailure?.parameter,
                            requestAttempts = attempts,
                            fastTierFallback = fastTierFallback,
                        )
                    )
                }
            }
        }

    private suspend fun requestCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        fallback: () -> String,
    ): ArkTextResult =
        withContext(Dispatchers.IO) {
            try {
                executeRequest(systemPrompt, userPrompt, maxTokens, stream = false) { response ->
                    if (!response.isSuccessful) throw ArkHttpFailure.from(response)
                    val responseBody = response.body?.string().orEmpty()
                    val text =
                        JSONObject(responseBody)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                            ?.takeIf(String::isNotBlank) ?: error("豆包没有返回笔记内容。")
                    ArkTextResult(
                        text = text,
                        mode = AssistantResponseMode.Live,
                        statusMessage = "豆包已整理本次学习笔记。",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                ArkTextResult(
                    text = fallback(),
                    mode = AssistantResponseMode.Error,
                    statusMessage = "豆包暂时不可用（${error.safeArkMessage()}），已保留实时学习笔记。",
                )
            }
        }

    private suspend fun <T> executeRequest(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        stream: Boolean,
        visualFrames: List<CapturedVideoFrame> = emptyList(),
        serviceTier: String = "fast",
        timeoutNanos: Long =
            TimeUnit.MILLISECONDS.toNanos(ArkAnswerRequestPolicy.ANSWER_TIMEOUT_MS),
        readResponse: (Response) -> T,
    ): T {
        val request =
            Request.Builder()
                .url("${configuration.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${configuration.apiKey}")
                .post(
                    ArkAnswerRequestPolicy.requestBody(
                            configuration.model,
                            systemPrompt,
                            userPrompt,
                            maxTokens,
                            stream,
                            visualFrames,
                            serviceTier,
                        )
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            if (stream) {
                if (timeoutNanos <= 0) {
                    continuation.resumeWith(
                        Result.failure(java.io.InterruptedIOException("request timed out"))
                    )
                    return@suspendCancellableCoroutine
                }
                val boundNanos =
                    minOf(
                        timeoutNanos,
                        TimeUnit.MILLISECONDS.toNanos(ArkAnswerRequestPolicy.ANSWER_TIMEOUT_MS)
                    )
                val currentNanos = call.timeout().timeoutNanos()
                call
                    .timeout()
                    .timeout(
                        if (currentNanos == 0L) boundNanos else minOf(currentNanos, boundNanos),
                        TimeUnit.NANOSECONDS,
                    )
            }
            // Keep cancellation attached until the entire body has been consumed, not just headers.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (continuation.isActive) {
                                    val result = readResponse(it)
                                    if (continuation.isActive) continuation.resume(result)
                                }
                            }
                        } catch (error: Exception) {
                            if (continuation.isActive)
                                continuation.resumeWith(Result.failure(error))
                        }
                    }
                }
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ANSWER_MAX_TOKENS = 480
        const val SUMMARY_MAX_TOKENS = 1800
    }
}

internal object ArkStreamingResponseParser {
    fun extractServiceTier(line: String): String? =
        runCatching {
                JSONObject(line.trim().removePrefix("data:").trim())
                    .optString("service_tier")
                    .takeIf { it in setOf("fast", "default", "auto", "scale", "flex") }
            }
            .getOrNull()

    fun extractDelta(line: String): String? {
        val payload = line.trim().removePrefix("data:").trim()
        if (payload.isBlank() || payload == "[DONE]") return null
        return runCatching {
                JSONObject(payload)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    ?.takeIf(String::isNotEmpty)
            }
            .getOrNull()
    }
}

private data class ArkTextResult(
    val text: String,
    val mode: AssistantResponseMode,
    val statusMessage: String,
)

private fun Throwable.safeArkMessage(): String =
    when (this) {
        is ArkHttpFailure -> message.orEmpty()
        is java.io.InterruptedIOException -> "request timed out"
        is IOException -> "network error"
        else -> "request failed"
    }
