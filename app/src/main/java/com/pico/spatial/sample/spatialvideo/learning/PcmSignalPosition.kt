package com.pico.spatial.sample.spatialvideo.learning

/** First PCM16 mono signal above near-zero quantization noise, relative to this packet. */
internal fun firstPcmSignalFrame(pcm: ByteArray): Int? {
    require(pcm.size % 2 == 0)
    for (index in pcm.indices step 2) {
        val value =
            ((pcm[index].toInt() and 255) or (pcm[index + 1].toInt() shl 8)).toShort().toInt()
        if (kotlin.math.abs(value) > 8) return index / 2
    }
    return null
}
