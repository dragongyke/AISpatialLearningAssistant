package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSentenceChunkerTest {
    @Test
    fun lowLatencyModeReleasesAnOpeningClauseWithoutWaitingForTheWholeSentence() {
        val chunker = StreamingSentenceChunker(lowLatency = true)
        assertEquals(listOf("导数表示函数在某一点的变化快慢，"), chunker.append("导数表示函数在某一点的变化快慢，我们可以"))
        assertEquals("我们可以", chunker.finish())
    }

    @Test
    fun lowLatencyModeKeepsShortGreetingsAndNumbersTogether() {
        val chunker = StreamingSentenceChunker(lowLatency = true)
        assertEquals(emptyList<String>(), chunker.append("Yes, consider 1,000 units"))
        assertEquals(listOf("Yes, consider 1,000 units, ".trim()), chunker.append(", "))
    }

    @Test
    fun splitsLongPunctuatedBurstWithoutLosingText() {
        val chunker = StreamingSentenceChunker()
        val text = "知识点".repeat(200) + "。"
        val chunks = chunker.append(text) + listOfNotNull(chunker.finish())
        assertEquals(text, chunks.joinToString(""))
        org.junit.Assert.assertTrue(chunks.all { it.length <= 240 })
    }

    @Test
    fun englishSentencesStartSpeakingBeforeTheWholeAnswerFinishes() {
        val chunker = StreamingSentenceChunker()
        assertEquals(
            listOf("A derivative measures change."),
            chunker.append("A derivative measures change. ")
        )
        assertEquals(listOf("Speed is one example."), chunker.append("Speed is one example. "))
    }

    @Test
    fun doesNotSplitDecimalNumbersOrAbbreviationsAcrossDeltas() {
        val chunker = StreamingSentenceChunker()
        assertEquals(emptyList<String>(), chunker.append("Dr. Smith uses 3."))
        assertEquals(listOf("Dr. Smith uses 3.14 as pi."), chunker.append("14 as pi. Next"))
        assertEquals("Next", chunker.finish())
    }

    @Test
    fun emitsCompleteSentencesAndKeepsTheRemainder() {
        val chunker = StreamingSentenceChunker()

        assertEquals(emptyList<String>(), chunker.append("我们先看"))
        assertEquals(listOf("我们先看核心。"), chunker.append("核心。然后再"))
        assertEquals(listOf("然后再举个例子。"), chunker.append("举个例子。"))
        assertEquals(null, chunker.finish())
    }

    @Test
    fun finishReturnsTheLastIncompleteSentence() {
        val chunker = StreamingSentenceChunker()
        chunker.append("最后补充一点")

        assertEquals("最后补充一点", chunker.finish())
    }
}
