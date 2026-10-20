package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.*
import org.junit.Test

class PcmSignalPositionTest {
    @Test
    fun silenceAndNearZeroNoiseDoNotCountAsSpeech() {
        assertEquals(null, firstPcmSignalFrame(byteArrayOf(0, 0, 8, 0, -8, -1)))
    }

    @Test
    fun detectsLittleEndianSignedSignalAfterSilentPrefix() {
        assertEquals(2, firstPcmSignalFrame(byteArrayOf(0, 0, 2, 0, 0, -128)))
        assertEquals(1, firstPcmSignalFrame(byteArrayOf(0, 0, -1, 127)))
    }
}
