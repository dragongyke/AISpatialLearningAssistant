package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** SDK-independent, meter-sized geometry, uploaded once and shared by the native renderer. */
internal data class LearningOrbPoint(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: LearningOrbPoint) =
        LearningOrbPoint(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: LearningOrbPoint) =
        LearningOrbPoint(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float) = LearningOrbPoint(x * scale, y * scale, z * scale)

    fun dot(other: LearningOrbPoint) = x * other.x + y * other.y + z * other.z

    fun cross(other: LearningOrbPoint) =
        LearningOrbPoint(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )

    fun length() = sqrt(dot(this))

    fun normalized() = this * (1f / length())
}

internal data class LearningOrbMesh(
    val positions: List<LearningOrbPoint>,
    val normals: List<LearningOrbPoint>,
    val triangleIndices: List<Int>,
)

internal object LearningCrystalOrbMeshes {
    fun pedestalWood(): LearningOrbMesh =
        Builder()
            .apply {
                // A stepped, fluted ogee profile gives the plinth a turned-wood silhouette.
                lathe(
                    listOf(
                        0.070f to -0.153f,
                        0.076f to -0.150f,
                        0.076f to -0.146f,
                        0.070f to -0.143f,
                        0.070f to -0.140f,
                        0.060f to -0.137f,
                        0.044f to -0.133f,
                        0.033f to -0.129f,
                        0.029f to -0.123f,
                        0.030f to -0.117f,
                        0.042f to -0.111f,
                        0.053f to -0.107f,
                    )
                )
                repeat(4) { support ->
                    tube(supportPath(support), 10) { t -> 0.007f - t * 0.0035f }
                }
            }
            .build()

    fun pedestalBrass(): LearningOrbMesh =
        Builder()
            .apply {
                ring(0.074f, -0.145f, 0.0015f)
                ring(0.061f, -0.137f, 0.0014f)
                ring(0.064f, -0.104f, 0.0024f)
                repeat(4) { support ->
                    // Fine raised brass follows the carved support rather than forming a metal
                    // cylinder.
                    tube(
                        supportPath(support).map { p ->
                            val angle = support * PI / 2.0
                            p +
                                LearningOrbPoint(
                                    (cos(angle) * 0.005f).toFloat(),
                                    0.001f,
                                    (sin(angle) * 0.005f).toFloat()
                                )
                        },
                        6
                    ) {
                        0.00115f
                    }
                    val scroll =
                        (0..24).map { step ->
                            val t = step / 24f
                            val angle = -PI / 2.0 + t * PI * 1.7
                            val radius = 0.012f * (1f - 0.72f * t)
                            radial(
                                0.082f + cos(angle).toFloat() * radius,
                                -0.119f + sin(angle).toFloat() * radius,
                                support * PI / 2.0
                            )
                        }
                    tube(scroll, 6) { t -> 0.0018f - t * 0.0008f }
                }
            }
            .build()

    fun waitingSwirl(): LearningOrbMesh =
        Builder()
            .apply {
                repeat(2) { strand ->
                    val path =
                        (0..64).map { step ->
                            val t = step / 64f
                            val radius = 0.087f - t * 0.036f
                            radial(radius, -0.044f + t * 0.088f, strand * PI + t * PI * 2.3)
                        }
                    tube(path, 6) { t -> 0.0009f + sin(t * PI).toFloat() * 0.0012f }
                }
            }
            .build()

    fun waitingRunes(): LearningOrbMesh =
        Builder()
            .apply {
                repeat(12) { rune ->
                    val angle = rune * PI / 6.0
                    fun point(tangent: Float, radius: Float): LearningOrbPoint =
                        radial(radius, 0f, angle) +
                            LearningOrbPoint(
                                (-sin(angle) * tangent).toFloat(),
                                0f,
                                (cos(angle) * tangent).toFloat()
                            )
                    tube(listOf(point(0f, 0.128f), point(0f, 0.143f)), 6) { 0.001f }
                    val sign = if (rune % 2 == 0) 1f else -1f
                    tube(
                        listOf(point(0f, 0.141f), point(sign * 0.005f, 0.136f), point(0f, 0.132f)),
                        6
                    ) {
                        0.00085f
                    }
                    tube(listOf(point(-0.004f, 0.132f), point(0.004f, 0.132f)), 6) { 0.00085f }
                }
            }
            .build()

    private fun supportPath(index: Int): List<LearningOrbPoint> =
        (0..32).map { step ->
            val t = step / 32f
            val u = 1f - t
            val radius =
                u * u * u * 0.047f +
                    3f * u * u * t * 0.115f +
                    3f * u * t * t * 0.067f +
                    t * t * t * 0.101f
            val height =
                u * u * u * -0.137f +
                    3f * u * u * t * -0.137f +
                    3f * u * t * t * -0.110f +
                    t * t * t * -0.066f
            radial(radius, height, index * PI / 2.0)
        }

    private fun radial(radius: Float, y: Float, angle: Double) =
        LearningOrbPoint(cos(angle).toFloat() * radius, y, sin(angle).toFloat() * radius)

    private class Builder {
        private val positions = mutableListOf<LearningOrbPoint>()
        private val indices = mutableListOf<Int>()

        fun lathe(profile: List<Pair<Float, Float>>) {
            val sides = 64
            val rings =
                profile.map { (radius, y) ->
                    List(sides) { side ->
                        val angle = side * 2.0 * PI / sides
                        radial(radius * (1f + 0.016f * cos(angle * 12).toFloat()), y, angle)
                    }
                }
            surface(rings)
            cap(rings.first(), LearningOrbPoint(0f, profile.first().second, 0f), false)
            cap(rings.last(), LearningOrbPoint(0f, profile.last().second, 0f), true)
        }

        fun ring(radius: Float, y: Float, thickness: Float) {
            tube((0..64).map { radial(radius, y, it * PI / 32.0) }, 8, capped = false) { thickness }
        }

        fun tube(
            path: List<LearningOrbPoint>,
            sides: Int,
            capped: Boolean = true,
            radius: (Float) -> Float
        ) {
            val tangents =
                path.indices.map { index ->
                    (path[(index + 1).coerceAtMost(path.lastIndex)] -
                            path[(index - 1).coerceAtLeast(0)])
                        .normalized()
                }
            // One frame reference for the entire curve prevents cross-section flips near vertical.
            val reference =
                listOf(
                        LearningOrbPoint(1f, 0f, 0f),
                        LearningOrbPoint(0f, 1f, 0f),
                        LearningOrbPoint(0f, 0f, 1f)
                    )
                    .minBy { axis -> tangents.maxOf { abs(it.dot(axis)) } }
            val rings =
                path.mapIndexed { index, point ->
                    val tangent = tangents[index]
                    val side = tangent.cross(reference).normalized()
                    val up = side.cross(tangent)
                    val thickness = radius(index.toFloat() / path.lastIndex)
                    List(sides) { spoke ->
                        val angle = spoke * 2.0 * PI / sides
                        point +
                            (side * cos(angle).toFloat() + up * sin(angle).toFloat()) * thickness
                    }
                }
            surface(rings)
            if (capped) {
                cap(rings.first(), path.first(), false)
                cap(rings.last(), path.last(), true)
            }
        }

        private fun surface(rings: List<List<LearningOrbPoint>>) {
            val start = positions.size
            val sides = rings.first().size
            rings.forEach(positions::addAll)
            repeat(rings.size - 1) { row ->
                repeat(sides) { side ->
                    val a = start + row * sides + side
                    val b = start + row * sides + (side + 1) % sides
                    indices.addAll(listOf(a, a + sides, b, b, a + sides, b + sides))
                }
            }
        }

        private fun cap(ring: List<LearningOrbPoint>, center: LearningOrbPoint, top: Boolean) {
            val start = positions.size
            positions += ring
            val middle = positions.size
            positions += center
            repeat(ring.size) { side ->
                val a = start + side
                val b = start + (side + 1) % ring.size
                indices.addAll(if (top) listOf(middle, b, a) else listOf(middle, a, b))
            }
        }

        fun build(): LearningOrbMesh {
            val normals = MutableList(positions.size) { LearningOrbPoint(0f, 0f, 0f) }
            indices.chunked(3).forEach { (a, b, c) ->
                val normal = (positions[b] - positions[a]).cross(positions[c] - positions[a])
                normals[a] = normals[a] + normal
                normals[b] = normals[b] + normal
                normals[c] = normals[c] + normal
            }
            return LearningOrbMesh(
                positions.toList(),
                normals.map { it.normalized() },
                indices.toList()
            )
        }
    }
}
