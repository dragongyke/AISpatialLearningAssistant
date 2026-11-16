package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmPlaybackEnvelopeTest {
    @Test
    fun queuedLoudAudioDoesNotLightUpAnEarlierPause() {
        val envelope = PcmPlaybackEnvelope()
        envelope.append(ByteArray(960), 0, 960, 0)
        // Negative little-endian PCM16, at the start of the second 20 ms window.
        envelope.append(byteArrayOf(123, 123, 0, -64), 2, 2, 480)
        assertEquals(0f, envelope.levelAt(479), 0f)
        assertEquals(1f, envelope.levelAt(480), 0f)
        assertEquals(0f, envelope.levelAt(960), 0f)
    }

    @Test
    fun overwritingAnOldWindowCannotLightUpTheWrongPlaybackPosition() {
        val envelope = PcmPlaybackEnvelope()
        envelope.append(byteArrayOf(0, 32), 0, 2, 0)
        assertEquals(0.5f, envelope.levelAt(0), 0f)
        envelope.append(byteArrayOf(0, 0), 0, 2, 480L * 128)
        assertEquals(0f, envelope.levelAt(0), 0f)
    }
}
