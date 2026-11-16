package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbMeshesTest {
    private val shell = LearningCrystalOrbMeshes.shell()
    private val streams = LearningCrystalOrbStream.entries.map(LearningCrystalOrbMeshes::sweep)
    private val meshes = listOf(shell) + streams.flatMap { listOf(it.filaments, it.veils) }

    @Test
    fun meshesHaveFiniteUnitNormalsAndNondegenerateConsistentlyWoundTriangles() {
        for (mesh in meshes) {
            assertTrue(mesh.positions.isNotEmpty())
            assertEquals(mesh.positions.size, mesh.normals.size)
            assertEquals(0, mesh.triangleIndices.size % 3)
            mesh.positions.forEach {
                assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite())
            }
            mesh.normals.forEach { assertEquals(1f, it.length(), 0.002f) }
            mesh.triangleIndices.chunked(3).forEach { triangle ->
                assertTrue(triangle.all { it in mesh.positions.indices })
                val (a, b, c) = triangle.map { mesh.positions[it] }
                val cross = (b - a).cross(c - a)
                assertTrue("Degenerate triangle", cross.length() > 1e-10f)
                val normal = triangle.map { mesh.normals[it] }.reduce { x, y -> x + y }
                assertTrue("Inverted normal", cross.dot(normal) > 0f)
            }
        }
    }

    @Test
    fun sweepsOccupyDifferentDepthsInsideTheGlassRatherThanAFlatBand() {
        for (stream in streams) {
            for (mesh in listOf(stream.filaments, stream.veils)) {
                val p = mesh.positions
                val radius = p.maxOf { it.length() }
                assertTrue("Sweep exceeds 12 cm glass: $radius m", radius <= 0.12f)
                assertTrue(p.maxOf { it.length() } - p.minOf { it.length() } > 0.025f)
                assertTrue(p.maxOf { it.x } - p.minOf { it.x } > 0.10f)
                assertTrue(p.maxOf { it.y } - p.minOf { it.y } > 0.10f)
                assertTrue(p.maxOf { it.z } - p.minOf { it.z } > 0.10f)
            }
        }
        assertTrue(shell.positions.all { kotlin.math.abs(it.length() - 0.12f) < 1e-5f })
        assertTrue(
            shell.positions.zip(shell.normals).all { (point, normal) -> point.dot(normal) > 0f }
        )
    }

    @Test
    fun veilsHaveCompleteUvsAndBoundedNonuniformLightMaps() {
        val colors = mutableSetOf<Int>()
        for ((index, stream) in LearningCrystalOrbStream.entries.withIndex()) {
            val mesh = streams[index].veils
            assertEquals(mesh.positions.size, mesh.uv.size)
            assertTrue(mesh.uv.all { (u, v) -> u in 0f..1f && v in 0f..1f })
            val pixels = LearningCrystalOrbCaustics.pixels(stream)
            assertEquals(
                LearningCrystalOrbCaustics.width * LearningCrystalOrbCaustics.height,
                pixels.size
            )
            fun brightest(p: Int) = maxOf(p ushr 16 and 255, p ushr 8 and 255, p and 255)
            assertTrue(
                "Light gaps must be nearly transparent under additive blending",
                pixels.minOf(::brightest) < 8
            )
            assertTrue("Crest must remain bright", pixels.maxOf(::brightest) > 230)
            colors += pixels.contentHashCode()
        }
        assertEquals(3, colors.size)
        assertTrue(
            3 * 4 * LearningCrystalOrbCaustics.width * LearningCrystalOrbCaustics.height <=
                192 * 1024
        )
    }

    @Test
    fun allRenderedMeshesStayWithinTheExistingGeometryAndHitBudgets() {
        // The inner glass uses a second instance of the same shell mesh.
        val instances = meshes + shell
        assertTrue(instances.size <= 8)
        assertTrue(instances.sumOf { it.positions.size } < 9000)
        assertTrue(instances.sumOf { it.triangleIndices.size / 3 } < 14000)
        val maxRadius =
            streams
                .flatMap { listOf(it.veils, it.filaments) }
                .maxOf { mesh -> mesh.positions.maxOf { it.length() } }
        for (state in LearningVoiceControlState.entries) {
            repeat(60) { tick ->
                val frame = learningCrystalOrbFrame(state, true, false, tick / 10.0, 1f)
                val radius = maxRadius * frame.sweepScale * frame.visualScale
                assertTrue("Animated sweep outside glass: $radius m", radius <= 0.12f)
                assertTrue(frame.innerScale * frame.visualScale < 1f)
                assertTrue(radius < LearningCrystalOrbGeometry.colliderRadiusMeters)
            }
        }
        val padding =
            LearningCrystalOrbGeometry.colliderRadiusMeters -
                LearningCrystalOrbGeometry.shellRadiusMeters
        assertTrue("Gaze target should hug the floating glass", padding in 0f..0.015f)
    }
}
