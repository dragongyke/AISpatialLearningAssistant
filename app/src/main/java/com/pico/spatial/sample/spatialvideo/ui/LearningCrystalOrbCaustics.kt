package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Static sRGB emission maps. Black adds no light; glass remains visible between wisps. */
internal object LearningCrystalOrbCaustics {
    const val width = 256
    const val height = 64

    fun pixels(stream: LearningCrystalOrbStream): IntArray {
        val result = IntArray(width * height)
        val phase = stream.ordinal * 2.09
        for (x in 0 until width) {
            val angle = 2 * PI * (x + 0.5) / width
            val fan = 0.12 + 0.52 * sin(angle + phase).pow(2)
            val center = 0.22 * sin(2 * angle + phase)
            val flare = (0.5 + 0.5 * sin(2 * angle + phase)).pow(5)
            val tail = sin(angle / 2).pow(0.6)
            val paths = DoubleArray(5) { (it - 2) * fan * 0.42 + 0.043 * sin(3 * angle + it - 2) }
            for (y in 0 until height) {
                val v = (y + 0.5) / height * 2 - 1
                val d = v - center
                var light = 0.52 * exp(-3 * d * d) * (0.53 + flare)
                var hot = 0.0
                for (path in paths) {
                    val offset = d - path
                    val peak = exp(-offset * offset * 420) * (0.42 + 0.84 * flare)
                    light += peak
                    hot += peak * flare
                }
                val fade = tail * (1 - v * v)
                val white = (hot * hot * 0.6).coerceAtMost(1.0)
                fun channel(color: Float): Int {
                    val value = ((color * light + white) * fade).coerceIn(0.0, 1.0)
                    val srgb =
                        if (value < 0.0031308) 12.92 * value else 1.055 * value.pow(1 / 2.4) - 0.055
                    return (255 * srgb).roundToInt()
                }
                val rgb = stream.tint
                result[y * width + x] =
                    (255 shl 24) or
                        (channel(rgb.red) shl 16) or
                        (channel(rgb.green) shl 8) or
                        channel(rgb.blue)
            }
        }
        return result
    }
}
