package com.pico.spatial.sample.spatialvideo.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/** V3 framing, as specified by Doubao's TTS Websocket Bidirection protocols reference. */
internal object DoubaoTtsProtocol {
    data class Frame(
        val event: Int,
        val sessionId: String?,
        val payload: ByteArray,
        val isAudio: Boolean,
        val errorCode: Int? = null,
    )

    fun encode(event: Int, sessionId: String? = null, json: String = "{}"): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { writer ->
            writer.write(byteArrayOf(0x11, 0x14, 0x10, 0))
            writer.writeInt(event)
            if (event >= 100) {
                val id = requireNotNull(sessionId).toByteArray(Charsets.UTF_8)
                writer.writeInt(id.size)
                writer.write(id)
            }
            val bytes = json.toByteArray(Charsets.UTF_8)
            writer.writeInt(bytes.size)
            writer.write(bytes)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): Frame {
        require(bytes.size >= 4) { "Truncated TTS header." }
        val header = bytes[0].toInt() and 255
        require(header shr 4 == 1 && header and 15 >= 1) { "Unsupported TTS header." }
        val size = (header and 15) * 4
        require(size <= bytes.size) { "Truncated TTS extended header." }
        val type = (bytes[1].toInt() and 255) shr 4
        val flags = bytes[1].toInt() and 15
        val compression = bytes[2].toInt() and 15
        require(compression in 0..1) { "Unsupported TTS compression." }
        val input = ByteBuffer.wrap(bytes).apply { position(size) }
        fun number(): Int {
            require(input.remaining() >= 4) { "Truncated TTS field." }
            return input.int
        }
        fun field(): ByteArray {
            val length = number()
            require(length in 0..input.remaining()) { "Invalid TTS field length." }
            return ByteArray(length).also(input::get)
        }
        val error = if (type == 15) number() else null
        if (type != 15 && flags in listOf(1, 3)) number()
        val event = if (flags and 4 != 0) number() else 0
        val id = if (event >= 100) field().toString(Charsets.UTF_8) else null
        if (event in 50..52) field() // Connection IDs precede the JSON body, not session IDs.
        val encoded = field()
        require(!input.hasRemaining()) { "Unexpected trailing TTS bytes." }
        val payload =
            if (compression == 1) {
                GZIPInputStream(encoded.inputStream()).use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 2 * 1024 * 1024) {
                            "TTS payload too large."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } else encoded
        return Frame(event, id, payload, type == 11, error)
    }
}
