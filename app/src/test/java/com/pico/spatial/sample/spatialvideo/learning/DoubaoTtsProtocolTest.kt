package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.*
import org.junit.Test

class DoubaoTtsProtocolTest {
    @Test
    fun connectionRequestHasNoSessionIdField() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x14, 0x10, 0, 0, 0, 0, 1, 0, 0, 0, 2, 123, 125),
            DoubaoTtsProtocol.encode(1),
        )
    }

    @Test
    fun textRequestUsesUtf8ByteLengths() {
        val packet = DoubaoTtsProtocol.encode(200, "s", "学习")
        assertArrayEquals(
            byteArrayOf(
                0x11,
                0x14,
                0x10,
                0,
                0,
                0,
                0,
                200.toByte(),
                0,
                0,
                0,
                1,
                115,
                0,
                0,
                0,
                6,
                0xe5.toByte(),
                0xad.toByte(),
                0xa6.toByte(),
                0xe4.toByte(),
                0xb9.toByte(),
                0xa0.toByte()
            ),
            packet,
        )
    }

    @Test
    fun audioEventPreservesRawPcmAndSessionIdentity() {
        val frame =
            DoubaoTtsProtocol.decode(
                byteArrayOf(
                    0x11,
                    0xb4.toByte(),
                    0,
                    0,
                    0,
                    0,
                    1,
                    96,
                    0,
                    0,
                    0,
                    1,
                    115,
                    0,
                    0,
                    0,
                    4,
                    0,
                    1,
                    -1,
                    127,
                )
            )
        assertEquals(352, frame.event)
        assertEquals("s", frame.sessionId)
        assertArrayEquals(byteArrayOf(0, 1, -1, 127), frame.payload)
        assertTrue(frame.isAudio)
    }

    @Test
    fun connectionResponseDoesNotMistakeConnectionIdForPayload() {
        val frame =
            DoubaoTtsProtocol.decode(
                byteArrayOf(
                    0x11,
                    0x94.toByte(),
                    0x10,
                    0,
                    0,
                    0,
                    0,
                    50,
                    0,
                    0,
                    0,
                    1,
                    99,
                    0,
                    0,
                    0,
                    2,
                    123,
                    125,
                )
            )
        assertEquals(50, frame.event)
        assertNull(frame.sessionId)
        assertEquals("{}", frame.payload.toString(Charsets.UTF_8))
    }

    @Test
    fun errorPacketRetainsNumericCodeWithoutLoggingPayload() {
        val frame =
            DoubaoTtsProtocol.decode(
                byteArrayOf(
                    0x11,
                    0xf0.toByte(),
                    0x10,
                    0,
                    0,
                    0,
                    0,
                    42,
                    0,
                    0,
                    0,
                    2,
                    123,
                    125,
                )
            )
        assertEquals(42, frame.errorCode)
    }

    @Test
    fun rejectsTruncatedOrOversizedPayloads() {
        for (packet in
            listOf(
                byteArrayOf(0x11),
                byteArrayOf(
                    0x11,
                    0x94.toByte(),
                    0x10,
                    0,
                    0,
                    0,
                    0,
                    50,
                    0,
                    0,
                    0,
                    0,
                    127,
                    127,
                    127,
                    127,
                )
            )) {
            assertThrows(IllegalArgumentException::class.java) { DoubaoTtsProtocol.decode(packet) }
        }
    }
}
