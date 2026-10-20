package com.pico.spatial.sample.spatialvideo.learning

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoAnswerRequestTest {
    @Test
    fun streamedEnglishWordsAndParagraphsKeepWhitespaceTokens() = runBlocking {
        val deltas = listOf("Hello", " ", "world.", "\n\n", "Next paragraph.")
        val response =
            deltas.joinToString("\n\n") {
                "data: " +
                    JSONObject()
                        .put(
                            "choices",
                            org.json
                                .JSONArray()
                                .put(JSONObject().put("delta", JSONObject().put("content", it)))
                        )
            } + "\n\ndata: [DONE]\n\n"
        withEngine(responseOverride = response) { engine, _ ->
            val partials = mutableListOf<String>()
            val answer =
                engine.answer("Explain", 0, emptyList(), LearningUiState(), null) { partials += it }
            assertEquals("Hello world.\n\nNext paragraph.", answer.questionAnswer.answerText)
            assertEquals(
                listOf(
                    "Hello",
                    "Hello ",
                    "Hello world.",
                    "Hello world.\n\n",
                    "Hello world.\n\nNext paragraph."
                ),
                partials
            )
        }
    }

    @Test
    fun whitespaceOnlyStreamDoesNotRecordMeaningfulFirstContent() = runBlocking {
        val observations = mutableListOf<ArkAnswerObservation>()
        val response =
            "data: {\"choices\":[{\"delta\":{\"content\":\" \\n\"}}]}\n\ndata: [DONE]\n\n"
        withEngine(responseOverride = response, observations = observations) { engine, _ ->
            val partials = mutableListOf<String>()
            engine.answer("Explain", 0, emptyList(), LearningUiState(), null) { partials += it }
            assertEquals(" \n", partials.first())
            assertEquals(null, observations.single().firstContentMs)
        }
    }

    @Test
    fun interactiveAnswerDisablesThinkingAndRequestsFastService() = runBlocking {
        withEngine { engine, requests ->
            val answer = engine.answer("Explain this", 10_000, emptyList(), LearningUiState(), null)

            assertEquals(AssistantResponseMode.Live, answer.mode)
            assertEquals(1, requests.size)
            val body = requests.single()
            assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
            assertEquals("fast", body.getString("service_tier"))
            assertTrue(body.getBoolean("stream"))
            assertEquals(480, body.getInt("max_tokens"))
        }
    }

    @Test
    fun notesKeepDefaultThinkingAndServiceTier() = runBlocking {
        withEngine { engine, requests ->
            engine.generateSummary(LearningUiState())

            val body = requests.single()
            assertFalse(body.has("thinking"))
            assertFalse(body.has("service_tier"))
            assertFalse(body.getBoolean("stream"))
            assertEquals(1800, body.getInt("max_tokens"))
        }
    }

    @Test
    fun noFramesUsesTextOnlyAndDoesNotClaimVisualEvidence() = runBlocking {
        withEngine { engine, requests ->
            engine.answer("What is on the board?", 10_000, emptyList(), LearningUiState(), null)

            val content = requests.single().getJSONArray("messages").getJSONObject(1).get("content")
            assertTrue(content is String)
            assertTrue(content.toString().contains("Visual evidence is unavailable"))
            assertTrue(content.toString().contains("Do not claim to see"))
            assertFalse(requests.single().toString().contains("image_url"))
        }
    }

    @Test
    fun framesAndSubtitleHistoryAreSentInOneAnswerRequest() = runBlocking {
        withEngine { engine, requests ->
            val previous =
                LearningQuestionAnswer(
                    "old",
                    2_000,
                    "Earlier question",
                    "Earlier answer",
                    0,
                    4_000,
                    1
                )
            val state = LearningUiState(subtitleOffsetMs = 2_000)
            engine.answer(
                "What is on the board?",
                10_000,
                listOf(previous),
                state,
                null,
                visualFrames = listOf(CapturedVideoFrame(8_000, "data:image/jpeg;base64,/9j/2Q==")),
            )

            assertEquals(1, requests.size)
            val content =
                requests.single().getJSONArray("messages").getJSONObject(1).getJSONArray("content")
            assertEquals(2, content.length())
            assertEquals("text", content.getJSONObject(0).getString("type"))
            val text = content.getJSONObject(0).getString("text")
            assertTrue(text.contains("Earlier question"))
            assertTrue(text.contains("Earlier answer"))
            assertTrue(text.contains("English"))
            assertTrue(text.contains("00:08"))
            assertTrue(text.contains(state.transcriptSegments.first().text))
            assertFalse(text.contains("Visual evidence is unavailable"))
            assertEquals("image_url", content.getJSONObject(1).getString("type"))
            assertEquals(
                "data:image/jpeg;base64,/9j/2Q==",
                content.getJSONObject(1).getJSONObject("image_url").getString("url")
            )
        }
    }

    @Test
    fun rejectedFramesDoNotImplyImagesWereSent() = runBlocking {
        withEngine { engine, requests ->
            engine.answer(
                "Read the board",
                10_000,
                emptyList(),
                LearningUiState(),
                null,
                visualFrames =
                    listOf(CapturedVideoFrame(8_000, "https://untrusted.invalid/frame.jpg")),
            )

            val content =
                requests.single().getJSONArray("messages").getJSONObject(1).getString("content")
            assertTrue(content.contains("Visual evidence is unavailable"))
            assertFalse(requests.single().toString().contains("image_url"))
        }
    }

    @Test
    fun recordsActualDowngradedTierAndTimingsWithoutContent() = runBlocking {
        val observations = mutableListOf<ArkAnswerObservation>()
        withEngine(observations = observations) { engine, _ ->
            engine.answer("private question", 10_000, emptyList(), LearningUiState(), null)
            val observation = observations.single()
            assertEquals("fast", observation.requestedServiceTier)
            assertEquals("default", observation.actualServiceTier)
            assertEquals("live", observation.outcome)
            assertEquals(0, observation.frameCount)
            assertTrue(observation.firstContentMs != null)
            assertTrue(observation.elapsedMs >= observation.firstContentMs!!)
            assertFalse(observation.toString().contains("private question"))
            assertFalse(observation.toString().contains("test-key"))
        }
    }

    @Test
    fun serviceErrorsDoNotExposeResponseBodyInFallbackStatus() = runBlocking {
        withEngine(responseCode = 500, responseOverride = "private response body test-key") {
            engine,
            _ ->
            val answer =
                engine.answer("Read the board", 10_000, emptyList(), LearningUiState(), null)
            assertEquals(AssistantResponseMode.Error, answer.mode)
            assertFalse(answer.statusMessage.contains("private response body"))
            assertFalse(answer.statusMessage.contains("test-key"))
        }
    }

    @Test
    fun fastNotActivatedRetriesOnceWithAutoAndRemembersForNextAnswer() = runBlocking {
        val error =
            """{"error":{"code":"ModelNotOpen","message":"Your account has not activated the fast mode for this model. private test-key"}}"""
        val observations = mutableListOf<ArkAnswerObservation>()
        withEngine(scriptedResponses = listOf(404 to error), observations = observations) {
            engine,
            requests ->
            val partials = mutableListOf<String>()
            val answer =
                engine.answer("Explain", 0, emptyList(), LearningUiState(), null) { partials += it }
            assertEquals(AssistantResponseMode.Live, answer.mode)
            assertEquals("Answer", answer.questionAnswer.answerText)
            assertEquals(listOf("Answer"), partials)
            assertEquals(listOf("fast", "auto"), requests.map { it.getString("service_tier") })
            requests.forEach {
                assertEquals("disabled", it.getJSONObject("thinking").getString("type"))
            }
            assertFalse(answer.statusMessage.contains("private"))
            assertEquals(2, observations.single().requestAttempts)
            assertTrue(observations.single().fastTierFallback)
            assertEquals("auto", observations.single().requestedServiceTier)
            assertEquals(200, observations.single().httpStatus)
            engine.answer("Next question", 0, emptyList(), LearningUiState(), null)
            assertEquals(
                listOf("fast", "auto", "auto"),
                requests.map { it.getString("service_tier") }
            )
        }
    }

    @Test
    fun modelNotOpenWithoutFastActivationFailureDoesNotRetry() = runBlocking {
        val error =
            """{"error":{"code":"ModelNotOpen","message":"Your account has not activated this model."}}"""
        val observations = mutableListOf<ArkAnswerObservation>()
        withEngine(scriptedResponses = listOf(404 to error), observations = observations) {
            engine,
            requests ->
            val answer = engine.answer("Explain", 0, emptyList(), LearningUiState(), null)
            assertEquals(AssistantResponseMode.Error, answer.mode)
            assertEquals(1, requests.size)
            assertEquals(404, observations.single().httpStatus)
            assertEquals("ModelNotOpen", observations.single().errorCode)
            assertTrue(answer.statusMessage.contains("HTTP 404 ModelNotOpen"))
        }
    }

    @Test
    fun failedAutoRetryStopsWithoutLooping() = runBlocking {
        val error =
            """{"error":{"code":"ModelNotOpen","message":"The fast mode has not been activated for your account."}}"""
        withEngine(scriptedResponses = listOf(404 to error, 404 to error)) { engine, requests ->
            val partials = mutableListOf<String>()
            val answer =
                engine.answer("Explain", 0, emptyList(), LearningUiState(), null) { partials += it }
            assertEquals(AssistantResponseMode.Error, answer.mode)
            assertEquals(2, requests.size)
            assertEquals(1, partials.size)
        }
    }

    @Test
    fun otherErrorCodesMentioningFastDoNotRetryOrExposeMessages() = runBlocking {
        val error =
            """{"error":{"code":"InvalidParameter","type":"BadRequest","param":"service_tier","message":"fast mode is not activated. secret test-key"}}"""
        val observations = mutableListOf<ArkAnswerObservation>()
        withEngine(scriptedResponses = listOf(400 to error), observations = observations) {
            engine,
            requests ->
            val answer = engine.answer("Explain", 0, emptyList(), LearningUiState(), null)
            assertEquals(1, requests.size)
            assertEquals("InvalidParameter", observations.single().errorCode)
            assertEquals("BadRequest", observations.single().errorType)
            assertEquals("service_tier", observations.single().errorParameter)
            assertFalse(observations.toString().contains("secret"))
            assertFalse(answer.statusMessage.contains("test-key"))
        }
    }

    @Test
    fun genericModelActivationErrorWithUnrelatedFastMentionDoesNotRetry() = runBlocking {
        val error =
            """{"error":{"code":"ModelNotOpen","message":"This model has not been activated. Fast mode is available after activation."}}"""
        withEngine(scriptedResponses = listOf(404 to error)) { engine, requests ->
            assertEquals(
                AssistantResponseMode.Error,
                engine.answer("Explain", 0, emptyList(), LearningUiState(), null).mode
            )
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun arbitraryErrorFieldsCannotReachDiagnostics() = runBlocking {
        val error =
            """{"error":{"code":"secret-test-key","type":"private content","param":"private prompt","message":"private body"}}"""
        val observations = mutableListOf<ArkAnswerObservation>()
        withEngine(scriptedResponses = listOf(400 to error), observations = observations) {
            engine,
            _ ->
            val answer = engine.answer("Explain", 0, emptyList(), LearningUiState(), null)
            assertEquals(null, observations.single().errorCode)
            assertEquals(null, observations.single().errorType)
            assertEquals(null, observations.single().errorParameter)
            assertTrue(answer.statusMessage.contains("HTTP 400"))
            assertFalse(answer.statusMessage.contains("private"))
            assertFalse(answer.statusMessage.contains("test-key"))
        }
    }

    @Test
    fun callerCancellationClosesAnInFlightStreamWithoutFallback() = runBlocking {
        val firstContent = CompletableDeferred<Unit>()
        val firstEvent = "data: {\"choices\":[{\"delta\":{\"content\":\"First\"}}]}\n\n"
        val server = MockWebServer()
        // Enqueued throttles also slow request uploads; dispatch only after the prompt is received.
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(firstEvent + "data: [DONE]\n\n")
                        .throttleBody(firstEvent.toByteArray().size.toLong(), 2, TimeUnit.SECONDS)
            }
        server.start()
        val client = OkHttpClient()
        val observations = mutableListOf<ArkAnswerObservation>()
        val partials = mutableListOf<String>()
        val engine =
            DoubaoLearningAssistantEngine(
                DoubaoAssistantConfiguration("test-key", "test-model", server.url("/").toString()),
                client,
                observations::add,
            )
        try {
            val job = launch {
                engine.answer("Explain", 0, emptyList(), LearningUiState(), null) { text ->
                    partials += text
                    firstContent.complete(Unit)
                }
            }
            withTimeout(3_000) { firstContent.await() }
            withTimeout(1_000) { job.cancelAndJoin() }
            withTimeout(1_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
            assertEquals(listOf("First"), partials)
            assertEquals("cancelled", observations.single().outcome)
        } finally {
            server.shutdown()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun interactiveDeadlineDoesNotExtendAShorterClientDeadline() = runBlocking {
        val deadlines = mutableListOf<Long>()
        val client =
            OkHttpClient.Builder()
                .callTimeout(1, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    deadlines += chain.call().timeout().timeoutNanos()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            "data: {\"choices\":[{\"delta\":{\"content\":\"Answer\"}}]}\n\ndata: [DONE]\n\n"
                                .toResponseBody()
                        )
                        .build()
                }
                .build()
        try {
            val engine =
                DoubaoLearningAssistantEngine(
                    DoubaoAssistantConfiguration("test-key", "test-model", "https://ark.invalid"),
                    client,
                    {},
                )
            engine.answer("Explain", 0, emptyList(), LearningUiState(), null)
            assertEquals(TimeUnit.SECONDS.toNanos(1), deadlines.single())
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private suspend fun withEngine(
        responseCode: Int = 200,
        responseOverride: String? = null,
        scriptedResponses: List<Pair<Int, String>> = emptyList(),
        observations: MutableList<ArkAnswerObservation> = mutableListOf(),
        block: suspend (LearningAssistantEngine, MutableList<JSONObject>) -> Unit,
    ) {
        val requests = mutableListOf<JSONObject>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    val body = JSONObject(buffer.readUtf8())
                    requests += body
                    val scripted = scriptedResponses.getOrNull(requests.lastIndex)
                    val response =
                        scripted?.second
                            ?: responseOverride
                            ?: if (body.getBoolean("stream")) {
                                "data: {\"service_tier\":\"default\",\"choices\":[{\"delta\":{\"content\":\"Answer\"}}]}\n\ndata: [DONE]\n\n"
                            } else {
                                "{\"choices\":[{\"message\":{\"content\":\"Notes\"}}]}"
                            }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(scripted?.first ?: responseCode)
                        .message("test response")
                        .body(response.toResponseBody())
                        .build()
                }
                .build()
        try {
            block(
                DoubaoLearningAssistantEngine(
                    DoubaoAssistantConfiguration(
                        "test-key",
                        "doubao-seed-2-1-turbo-260628",
                        "https://ark.invalid/api/v3"
                    ),
                    client,
                    observations::add,
                ),
                requests,
            )
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
