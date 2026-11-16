package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.PI
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
    val uv: List<Pair<Float, Float>> = emptyList(),
)

internal data class LearningOrbSweep(val filaments: LearningOrbMesh, val veils: LearningOrbMesh)

internal object LearningCrystalOrbMeshes {
    // The same sphere resource is instanced for the polished shell and the smaller smoked glass.
    fun shell(): LearningOrbMesh {
        val sides = 48
        val rows = 20
        val normals = mutableListOf(LearningOrbPoint(0f, 1f, 0f))
        val indices = mutableListOf<Int>()
        for (row in 1 until rows) {
            val latitude = PI * row / rows
            repeat(sides) { side ->
                val theta = side * 2 * PI / sides
                normals +=
                    LearningOrbPoint(
                        (sin(latitude) * cos(theta)).toFloat(),
                        cos(latitude).toFloat(),
                        (sin(latitude) * sin(theta)).toFloat()
                    )
            }
        }
        val south = normals.size
        normals += LearningOrbPoint(0f, -1f, 0f)
        for (side in 0 until sides) {
            val a = 1 + side
            val b = 1 + (side + 1) % sides
            indices.addAll(listOf(0, b, a))
            for (row in 0 until rows - 2) {
                val left = a + row * sides
                val right = b + row * sides
                indices.addAll(
                    listOf(left, right, left + sides, right, right + sides, left + sides)
                )
            }
            indices.addAll(listOf(a + (rows - 2) * sides, b + (rows - 2) * sides, south))
        }
        return LearningOrbMesh(
            normals.map { it * LearningCrystalOrbGeometry.shellRadiusMeters },
            normals,
            indices
        )
    }

    fun sweep(stream: LearningCrystalOrbStream): LearningOrbSweep {
        val threads = Builder(textured = false)
        val silk = Builder(textured = true)
        repeat(3) { branch ->
            val phase = stream.ordinal * 2.16 + branch * 2.64
            val tilt =
                when (stream) {
                    LearningCrystalOrbStream.Cyan -> -0.53
                    LearningCrystalOrbStream.Magenta -> 0.92
                    LearningCrystalOrbStream.Gold -> -1.14
                } + 0.65 * branch
            val rim = branch == 2
            val bank = if (rim) 1.14 else 0.31 * (stream.ordinal - 1) - 0.73 * branch
            val steps = if (rim) 54 else 76
            val path =
                (0..steps).map { sample ->
                    val t = sample.toDouble() / steps
                    val angle =
                        phase +
                            (if (branch == 1) -1 else 1) *
                                ((if (rim) 1.57 else 2.56) * PI * t + 0.33 * sin(2 * PI * t))
                    val latitude =
                        (t - 0.5) * (if (rim) 0.5 else 1.78) +
                            0.45 * sin(angle + phase) +
                            0.13 * sin(2 * angle)
                    // Each current leaves the rim, dives toward the center and resurfaces.
                    val dip = (1 + cos(angle - phase + 1.0)) * 0.5
                    // Reserve room for the banked ribbon edges and the hovered detail transform.
                    val radius =
                        if (rim) 0.106 - 0.006 * dip
                        else 0.107 - 0.002 * stream.ordinal - 0.004 * branch - 0.055 * dip * dip
                    val x = radius * cos(latitude) * cos(angle)
                    val y = radius * sin(latitude)
                    val z = radius * cos(latitude) * sin(angle)
                    val by = y * cos(bank) - z * sin(bank)
                    LearningOrbPoint(
                        (x * cos(tilt) - by * sin(tilt)).toFloat(),
                        (x * sin(tilt) + by * cos(tilt)).toFloat(),
                        (y * sin(bank) + z * cos(bank)).toFloat()
                    )
                }
            // Use the radial direction to transport the ribbon across the sphere, not a
            // camera-facing quad. The tapered edges and narrow hot strands have real thickness.
            val sections =
                path.mapIndexed { i, center ->
                    val t = i.toDouble() / steps
                    val tangent =
                        (path[(i + 1).coerceAtMost(steps)] - path[(i - 1).coerceAtLeast(0)])
                            .normalized()
                    val side = tangent.cross(center).normalized()
                    val normal = side.cross(tangent)
                    val twist = 0.62 * sin(phase + t * 2 * PI)
                    val widthAxis = side * cos(twist).toFloat() + normal * sin(twist).toFloat()
                    val envelope = sin(t * PI).toFloat()
                    Section(
                        center,
                        widthAxis,
                        widthAxis.cross(tangent),
                        0.00036f +
                            envelope * (0.0103f + 0.0048f * sin(t * 3 * PI + phase).toFloat()),
                        0.00028f + 0.00064f * envelope
                    )
                }
            silk.tube(sections, 4)
            threads.tube(
                sections.mapIndexed { i, sec ->
                    val t = i.toDouble() / steps
                    val crest = (0.18 + 0.64 * sin(phase + t * 3 * PI)).toFloat()
                    val width =
                        (0.000055 + sin(PI * t) * 0.00110 * (0.63 + 0.50 * sin(phase + 3 * PI * t)))
                            .toFloat()
                    sec.copy(
                        center = sec.center + sec.side * (sec.width * crest) + sec.up * sec.depth,
                        width = width,
                        depth = width * 0.72f
                    )
                },
                4
            )
        }
        return LearningOrbSweep(threads.build(), silk.build())
    }

    private data class Section(
        val center: LearningOrbPoint,
        val side: LearningOrbPoint,
        val up: LearningOrbPoint,
        val width: Float,
        val depth: Float
    )

    private class Builder(private val textured: Boolean) {
        private val positions = mutableListOf<LearningOrbPoint>()
        private val indices = mutableListOf<Int>()
        private val normals = mutableListOf<LearningOrbPoint>()
        private val uv = mutableListOf<Pair<Float, Float>>()

        fun tube(sections: List<Section>, sides: Int) {
            val start = positions.size
            for ((row, section) in sections.withIndex()) {
                for (spoke in 0 until sides) {
                    val angle = spoke * 2 * PI / sides
                    val c = cos(angle).toFloat()
                    val s = sin(angle).toFloat()
                    positions +=
                        section.center +
                            section.side * (section.width * c) +
                            section.up * (section.depth * s)
                    normals +=
                        (section.side * (c / section.width) + section.up * (s / section.depth))
                            .normalized()
                    if (textured) uv += row.toFloat() / sections.lastIndex to 0.5f + 0.5f * c
                }
            }
            for (row in 0 until sections.lastIndex) {
                for (side in 0 until sides) {
                    val a = start + row * sides + side
                    val b = start + row * sides + (side + 1) % sides
                    indices.addAll(listOf(a, a + sides, b, b, a + sides, b + sides))
                }
            }
            for (end in listOf(false, true)) {
                val ring = if (end) sections.lastIndex else 0
                val section = sections[ring]
                val normal = section.up.cross(section.side) * (if (end) 1f else -1f)
                val cap = positions.size
                positions += section.center
                normals += normal
                if (textured) uv += (if (end) 1f else 0f) to 0.5f
                for (side in 0 until sides) {
                    positions += positions[start + ring * sides + side]
                    normals += normal
                    if (textured) uv += uv[start + ring * sides + side]
                }
                for (side in 0 until sides) {
                    val a = cap + 1 + side
                    val b = cap + 1 + (side + 1) % sides
                    indices.addAll(if (end) listOf(cap, b, a) else listOf(cap, a, b))
                }
            }
        }

        fun build() = LearningOrbMesh(positions, normals, indices, uv)
    }
}
