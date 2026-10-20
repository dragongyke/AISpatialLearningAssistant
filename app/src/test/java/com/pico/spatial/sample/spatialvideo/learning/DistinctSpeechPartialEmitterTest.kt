package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctSpeechPartialEmitterTest {
    @Test
    fun emitsOnlyMeaningfulChangedPartialTranscripts() {
        val emitted = mutableListOf<String>()
        val emitter = DistinctSpeechPartialEmitter(emitted::add)

        emitter.emit(" hello ")
        emitter.emit("hello")
        emitter.emit("")
        emitter.emit("hello world")

        assertEquals(listOf("hello", "hello world"), emitted)
    }
}
