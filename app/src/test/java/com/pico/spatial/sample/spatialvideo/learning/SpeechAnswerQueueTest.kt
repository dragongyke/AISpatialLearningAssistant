package com.pico.spatial.sample.spatialvideo.learning

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAnswerQueueTest {
    @Test
    fun allSentencesInAnAnswerShareOneSession() = runBlocking {
        val speaker = FakeSpeaker()
        val input = Channel<String>(Channel.UNLIMITED)
        input.send("First sentence.")
        input.send("Second sentence.")
        input.close()
        var starts = 0
        SpeechAnswerQueue(speaker).play(input, { true }, { starts++ }, {}, {})
        assertEquals(listOf("First sentence.", "Second sentence."), speaker.spoken)
        assertEquals(1, speaker.opened)
        assertEquals(1, speaker.closed)
        assertEquals(1, starts)
    }

    @Test
    fun bargeInClosesTheSessionAndDiscardsQueuedSentences() = runBlocking {
        val speaking = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val speaker = FakeSpeaker {
            speaking.complete(Unit)
            hold.await()
        }
        val input = Channel<String>(Channel.UNLIMITED)
        input.send("First.")
        input.send("Must not play.")
        val job = launch { SpeechAnswerQueue(speaker).play(input, { true }, {}, {}, {}) }
        speaking.await()
        job.cancelAndJoin()
        assertEquals(listOf("First."), speaker.spoken)
        assertEquals(1, speaker.closed)
        assertTrue(input.trySend("Late sentence.").isFailure)
    }

    private class FakeSpeaker(private val duringSpeech: suspend () -> Unit = {}) :
        SpeechAnswerSpeaker {
        override val isConfigured = true
        override val readinessMessage = ""
        var opened = 0
        var closed = 0
        val spoken = mutableListOf<String>()

        override suspend fun withSession(block: suspend () -> Unit) {
            opened++
            try {
                block()
            } finally {
                closed++
            }
        }

        override suspend fun speak(
            text: String,
            onPlaybackStarted: () -> Unit
        ): SpeechPlaybackResult {
            check(opened == 1 && closed == 0)
            spoken += text
            onPlaybackStarted()
            duringSpeech()
            return SpeechPlaybackResult("", true)
        }

        override fun stop() {}
    }
}
