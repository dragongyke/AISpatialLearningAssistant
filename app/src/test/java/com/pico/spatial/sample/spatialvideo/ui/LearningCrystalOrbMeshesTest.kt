package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbMeshesTest {
    private val meshes =
        listOf(
            LearningCrystalOrbMeshes.pedestalWood(),
            LearningCrystalOrbMeshes.pedestalBrass(),
            LearningCrystalOrbMeshes.waitingSwirl(),
            LearningCrystalOrbMeshes.waitingRunes(),
        )

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
    fun allAnimatedGeometryFitsTheExistingColliderAndVisualEnvelope() {
        for ((index, mesh) in meshes.withIndex()) {
            val maxAnimationScale = if (index == 2) 1.1f else 1f
            for (point in mesh.positions) {
                val radius = point.length() * maxAnimationScale
                assertTrue(radius <= LearningCrystalOrbGeometry.maxVisualRadiusMeters)
                assertTrue(radius * 1.06f < LearningCrystalOrbGeometry.colliderRadiusMeters)
            }
        }
        assertEquals(0.19f, LearningCrystalOrbGeometry.colliderRadiusMeters, 0f)
        assertEquals(0.12f, LearningCrystalOrbGeometry.shellRadiusMeters, 0f)
    }

    @Test
    fun pedestalHasAFlaredFootAndRisingCarvedSupportsInsteadOfAStraightCylinder() {
        val wood = meshes[0].positions
        val foot = wood.filter { it.y < -0.14f }
        val neck = wood.filter { it.y in -0.126f..-0.12f }
        assertTrue(foot.maxOf { sqrt(it.x * it.x + it.z * it.z) } > 0.07f)
        assertTrue(neck.any { sqrt(it.x * it.x + it.z * it.z) < 0.04f })
        assertTrue(wood.any { it.y > -0.085f && abs(it.x) > 0.08f })
        assertTrue(wood.any { it.y > -0.085f && abs(it.z) > 0.08f })
    }

    @Test
    fun detailedMeshesRemainSmallAndDeterministic() {
        assertTrue(meshes.sumOf { it.positions.size } < 9000)
        assertTrue(meshes.sumOf { it.triangleIndices.size / 3 } < 14000)
        assertEquals(meshes[0], LearningCrystalOrbMeshes.pedestalWood())
        assertEquals(meshes[1], LearningCrystalOrbMeshes.pedestalBrass())
        assertEquals(meshes[2], LearningCrystalOrbMeshes.waitingSwirl())
        assertEquals(meshes[3], LearningCrystalOrbMeshes.waitingRunes())
    }

    @Test
    fun carvedSupportCrossSectionsDoNotFlipAlongTheirCurve() {
        // The turned profile precedes four 33-ring, 10-sided supports and their end caps.
        val firstSupport = 12 * 64 + 2 * 65
        val wood = meshes[0].positions
        repeat(4) { support ->
            val start = firstSupport + support * (33 * 10 + 2 * 11)
            repeat(31) { row ->
                val side = wood[start + row * 10] - wood[start + row * 10 + 5]
                val next = wood[start + (row + 1) * 10] - wood[start + (row + 1) * 10 + 5]
                assertTrue(
                    "Carved support twists abruptly",
                    side.normalized().dot(next.normalized()) > 0.9f
                )
            }
        }
    }
}
