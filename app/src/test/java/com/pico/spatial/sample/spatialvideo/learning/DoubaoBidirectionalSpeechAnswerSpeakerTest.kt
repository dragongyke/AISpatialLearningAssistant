package com.pico.spatial.sample.spatialvideo.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DoubaoBidirectionalSpeechAnswerSpeakerTest {
    @Test
    fun lowLatencyModelOnlyAppliesToCompatibleTtsOneResources() {
        assertEquals(
            "seed-tts-1.1",
            DoubaoStreamingTtsConfiguration(resourceId = "volc.service_type.10029").lowLatencyModel
        )
        assertEquals(
            "seed-tts-1.1",
            DoubaoStreamingTtsConfiguration(resourceId = "seed-tts-1.0").lowLatencyModel
        )
        assertNull(DoubaoStreamingTtsConfiguration(resourceId = "seed-tts-2.0").lowLatencyModel)
    }

    @Test
    fun cancellingInputAloneCancelsTheActiveAudioSession() = runBlocking {
        val server = MockWebServer()
        val entered = CompletableDeferred<Unit>()
        val output = HeldOutput(entered, CompletableDeferred())
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(replyingServer(CopyOnWriteArrayList(), CompletableDeferred()))
        )
        try {
            val input = Channel<String>(Channel.UNLIMITED)
            input.send("Pending answer.")
            val playback = launch {
                speaker(server, output).speakStream(input, { true }, {}, {}, {})
            }
            withTimeout(5_000) { entered.await() }
            input.cancel()
            withTimeout(1_000) { playback.join() }
            assertTrue(playback.isCancelled)
            assertTrue(output.closed)
            assertFalse(output.finished)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun sendsLaterTextWhileFirstAudioIsStillPlaying() = runBlocking {
        val server = MockWebServer()
        val received = CopyOnWriteArrayList<String>()
        val allTextSent = CompletableDeferred<Unit>()
        val audioStarted = CompletableDeferred<Unit>()
        val releaseAudio = CompletableDeferred<Unit>()
        val output = HeldOutput(audioStarted, releaseAudio)
        server.enqueue(MockResponse().withWebSocketUpgrade(replyingServer(received, allTextSent)))
        try {
            val speaker = speaker(server, output)
            val input = Channel<String>(Channel.UNLIMITED)
            input.send("First sentence.")
            input.send("Second sentence.")
            input.close()
            var firstText = 0
            val playback = launch { speaker.speakStream(input, { true }, { firstText++ }, {}, {}) }
            withTimeout(5_000) {
                audioStarted.await()
                allTextSent.await()
            }
            assertFalse(
                "Playback should still be held while both texts reach synthesis",
                playback.isCompleted
            )
            assertEquals(listOf("First sentence. ", "Second sentence. "), received)
            releaseAudio.complete(Unit)
            withTimeout(5_000) { playback.join() }
            assertEquals(1, firstText)
            assertTrue(output.finished)
            assertTrue(output.closed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun cancellingDuringPlaybackClosesOutputAndDoesNotFinishStaleAudio() = runBlocking {
        val server = MockWebServer()
        val audioStarted = CompletableDeferred<Unit>()
        val output = HeldOutput(audioStarted, CompletableDeferred())
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(replyingServer(CopyOnWriteArrayList(), CompletableDeferred()))
        )
        try {
            val speaker = speaker(server, output)
            val input = Channel<String>(Channel.UNLIMITED)
            input.send("Interrupt me.")
            val playback = launch { speaker.speakStream(input, { true }, {}, {}, {}) }
            withTimeout(5_000) { audioStarted.await() }
            speaker.stop()
            withTimeout(5_000) { playback.cancelAndJoin() }
            assertTrue(output.stopped)
            assertTrue(output.closed)
            assertFalse(output.finished)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectedSessionReportsTextOnlyFailureAndReleasesOutput() = runBlocking {
        val server = MockWebServer()
        val output = HeldOutput(CompletableDeferred(), CompletableDeferred())
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            val result = withTimeout(5_000) { speaker(server, output).speak("Hello.") }
            assertFalse(result.wasPlayed)
            assertTrue(result.statusMessage.contains("text"))
            assertTrue(output.closed)
        } finally {
            server.shutdown()
        }
    }

    private fun speaker(server: MockWebServer, output: PcmSpeechOutput) =
        DoubaoBidirectionalSpeechAnswerSpeaker(
            configuration =
                DoubaoStreamingTtsConfiguration(
                    "test-app",
                    "test-token",
                    "test-resource",
                    "test-voice"
                ),
            endpoint = server.url("/tts").toString(),
            outputFactory = { output },
        )

    private fun replyingServer(
        received: MutableList<String>,
        allTextSent: CompletableDeferred<Unit>
    ) =
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = DoubaoTtsProtocol.decode(bytes.toByteArray())
                when (frame.event) {
                    1 -> webSocket.send(serverFrame(50, "connection", "{}".toByteArray()))
                    100 -> {
                        val params =
                            JSONObject(frame.payload.toString(Charsets.UTF_8))
                                .getJSONObject("req_params")
                        assertEquals(
                            "pcm",
                            params.getJSONObject("audio_params").getString("format")
                        )
                        webSocket.send(serverFrame(150, frame.sessionId!!, "{}".toByteArray()))
                    }
                    200 -> {
                        received +=
                            JSONObject(frame.payload.toString(Charsets.UTF_8))
                                .getJSONObject("req_params")
                                .getString("text")
                        webSocket.send(
                            serverFrame(
                                352,
                                frame.sessionId!!,
                                byteArrayOf(1, 0, 2, 0),
                                audio = true
                            )
                        )
                    }
                    102 -> {
                        allTextSent.complete(Unit)
                        webSocket.send(serverFrame(152, frame.sessionId!!, "{}".toByteArray()))
                    }
                }
            }
        }

    private fun serverFrame(
        event: Int,
        id: String,
        payload: ByteArray,
        audio: Boolean = false
    ): ByteString {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.write(
                byteArrayOf(
                    0x11,
                    if (audio) 0xb4.toByte() else 0x94.toByte(),
                    if (audio) 0 else 0x10,
                    0
                )
            )
            it.writeInt(event)
            it.writeInt(id.length)
            it.write(id.toByteArray())
            it.writeInt(payload.size)
            it.write(payload)
        }
        return out.toByteArray().toByteString()
    }

    private class HeldOutput(
        val entered: CompletableDeferred<Unit>,
        val release: CompletableDeferred<Unit>
    ) : PcmSpeechOutput {
        var stopped = false
        var closed = false
        var finished = false

        override suspend fun write(pcm: ByteArray) {
            entered.complete(Unit)
            release.await()
        }

        override suspend fun finish() {
            finished = true
        }

        override fun stop() {
            stopped = true
        }

        override fun close() {
            closed = true
        }
    }
}
