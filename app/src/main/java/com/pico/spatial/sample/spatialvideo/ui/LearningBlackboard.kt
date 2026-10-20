package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3

data class LearningBlackboardGeometry(val innerWidth: Float, val top: Float, val bottom: Float) {
    val frameWidth = 0.055f
    val surfaceZ = -0.035f
    val backZ = -0.085f
    val height
        get() = top - bottom

    val centerY
        get() = (top + bottom) / 2f
}

/** Fixed to the learning wall's anchor, never parented to the draggable video. */
internal class LearningBlackboard(
    private val content: SpatialViewContent,
    parent: Entity,
    geometry: LearningBlackboardGeometry,
    private val woodTexture: TextureResource,
) {
    val root = Entity()
    private val meshes = mutableListOf<MeshResource>()
    private val materials = mutableListOf<PhysicallyBasedMaterial>()
    private val children = mutableListOf<Entity>()
    private var closed = false

    init {
        root.setName("LearningBlackboard")
        content.addEntity(root)
        parent.addChild(root)
        // The library is baked/unlit; give the runtime frame its own shadow-free fill.
        Entity().also { light ->
            children.add(light)
            light.setName("BlackboardFill")
            light.components.set(
                TransformComponent().setPosition(Vector3(-0.4f, geometry.centerY + 0.6f, 0.9f))
            )
            light.components.set(PointLightComponent(Color4(1f, 0.9f, 0.76f, 1f), 350f, 2.8f))
            root.addChild(light)
        }
        val oak =
            material(Color4(0.32f, 0.20f, 0.10f, 1f), 0.53f).apply {
                setBaseColorTexture(woodTexture)
                setEmissiveTexture(woodTexture)
                setEmissiveColor(Color4(0.12f, 0.07f, 0.03f, 1f))
            }
        val brass = material(Color4(0.53f, 0.36f, 0.13f, 1f), 0.32f, 0.65f)
        val slate = material(Color4(0.026f, 0.052f, 0.040f, 1f), 0.95f)
        val chalk = material(Color4(0.78f, 0.76f, 0.65f, 1f), 0.9f)
        fun box(
            name: String,
            center: Vector3,
            size: Vector3,
            material: PhysicallyBasedMaterial,
            bevel: Float = 0.006f
        ) {
            val mesh = MeshResource.createBox(size, bevel).also(meshes::add)
            val entity =
                Entity().apply {
                    setName(name)
                    components.set(TransformComponent().setPosition(center))
                    components.set(ModelComponent(mesh, material))
                }
            children.add(entity)
            root.addChild(entity)
        }
        with(geometry) {
            box(
                "Slate",
                Vector3(0f, centerY, surfaceZ - 0.012f),
                Vector3(innerWidth, height, 0.024f),
                slate
            )
            for (side in listOf(-1f, 1f)) {
                box(
                    "SideFrame${if (side < 0) "Left" else "Right"}",
                    Vector3(side * (innerWidth + frameWidth) / 2f, centerY, -0.045f),
                    Vector3(frameWidth, height + 2f * frameWidth, 0.08f),
                    oak
                )
                box(
                    "SideInlay${if (side < 0) "Left" else "Right"}",
                    Vector3(side * (innerWidth / 2f + 0.012f), centerY, -0.002f),
                    Vector3(0.004f, height, 0.004f),
                    brass,
                    0.001f
                )
            }
            for ((name, y) in
                listOf("Top" to top + frameWidth / 2f, "Bottom" to bottom - frameWidth / 2f)) {
                box(
                    "Frame$name",
                    Vector3(0f, y, -0.045f),
                    Vector3(innerWidth, frameWidth, 0.08f),
                    oak
                )
                box(
                    "Inlay$name",
                    Vector3(0f, y, -0.002f),
                    Vector3(innerWidth - 0.02f, 0.004f, 0.004f),
                    brass,
                    0.001f
                )
            }
            box(
                "ChalkTray",
                Vector3(0f, bottom - 0.033f, 0.012f),
                Vector3(innerWidth + 0.13f, 0.026f, 0.11f),
                oak
            )
            for (i in 0..2) {
                box(
                    "Chalk$i",
                    Vector3(innerWidth / 2f - 0.22f - i * 0.08f, bottom - 0.013f, 0.026f),
                    Vector3(0.06f, 0.012f, 0.013f),
                    chalk,
                    0.004f
                )
            }
        }
    }

    private fun material(color: Color4, roughness: Float, metallic: Float = 0f) =
        PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(color)
            setRoughness(roughness)
            setMetallic(metallic)
            setEmissiveColor(Color4(color.red * 0.18f, color.green * 0.18f, color.blue * 0.18f, 1f))
            materials.add(this)
        }

    fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        fun release(action: () -> Unit) {
            try {
                action()
            } catch (next: Throwable) {
                if (failure == null) failure = next else failure!!.addSuppressed(next)
            }
        }
        release { if (root.valid) root.enabled = false }
        release { if (root.valid) content.removeEntity(root) }
        release { if (root.valid) root.removeFromParent() }
        children.asReversed().forEach { release { if (it.valid) it.destroy(recursively = false) } }
        release { if (root.valid) root.destroy(recursively = false) }
        meshes.forEach { release { if (it.valid) it.close() } }
        materials.forEach { release { if (it.valid) it.close() } }
        release { if (woodTexture.valid) woodTexture.close() }
        failure?.let { throw it }
    }
}
