package com.pico.spatial.sample.spatialvideo.learning

import kotlin.math.abs

/** Small playback-head-indexed meter for the 24 kHz mono PCM stream, not a recording. */
internal class PcmPlaybackEnvelope {
    private val tags = LongArray(128) { -1 }
    private val peaks = IntArray(tags.size)

    @Synchronized
    fun append(pcm: ByteArray, offset: Int, count: Int, firstFrame: Long) {
        var i = offset
        var frame = firstFrame
        while (i + 1 < offset + count) {
            val window = frame / 480
            val index = (window % tags.size).toInt()
            if (tags[index] != window) {
                tags[index] = window
                peaks[index] = 0
            }
            val sample = (pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)
            peaks[index] = maxOf(peaks[index], abs(sample))
            i += 2
            frame++
        }
    }

    @Synchronized
    fun levelAt(frame: Long): Float {
        val window = frame / 480
        val index = (window % tags.size).toInt()
        return if (tags[index] == window) (peaks[index] / 16384f).coerceAtMost(1f) else 0f
    }
}
