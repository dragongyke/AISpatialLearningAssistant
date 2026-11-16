package com.pico.spatial.sample.spatialvideo.ui

import android.graphics.Bitmap
import androidx.annotation.MainThread
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import java.io.Closeable

/**
 * Native SDK6 orb. Construct, attach, update and close on the spatial UI/main thread. The caller
 * owns root pose, visibility, permission checks, input capture and frame scheduling. All dimensions
 * are local meters; keep the parent at unit scale for a 12 cm crystal radius.
 */
@MainThread
internal class LearningCrystalOrbRenderer() : Closeable {
    val root: Entity = Entity()

    private val entities = mutableListOf(root)
    private val resources = mutableListOf<Resource>()
    private var content: SpatialViewContent? = null
    private var attachedParent: Entity? = null
    private var closed = false
    private lateinit var shellMaterial: PhysicallyBasedMaterial
    private lateinit var innerMaterial: PhysicallyBasedMaterial
    private lateinit var innerTransform: TransformComponent
    private lateinit var detailTransform: TransformComponent

    private data class StreamResources(
        val stream: LearningCrystalOrbStream,
        val transform: TransformComponent,
        val threads: PhysicallyBasedMaterial,
        val veil: UnlitMaterial,
    )

    private val currents = mutableListOf<StreamResources>()
    private var previousTimeSeconds: Double? = null
    private var swirlDegrees = 0f

    init {
        try {
            build()
            update(LearningVoiceControlState.Ready, false, false, 0.0)
            previousTimeSeconds = null
        } catch (failure: Throwable) {
            runCatching { close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    constructor(content: SpatialViewContent, parent: Entity) : this() {
        try {
            attach(content, parent)
        } catch (failure: Throwable) {
            runCatching { close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    /** Idempotent for the same attachment. Close and recreate when replacing the scene. */
    fun attach(content: SpatialViewContent, parent: Entity) {
        check(!closed) { "The crystal orb has been closed" }
        if (this.content === content && attachedParent === parent) return
        check(this.content == null) { "The crystal orb is already attached" }
        require(parent !== root) { "The crystal orb cannot parent itself" }
        this.content = content
        attachedParent = parent
        // Register only the root: its existing native children follow it as one hierarchy.
        content.addEntity(root)
        check(parent.addChild(root)) { "Could not attach the crystal orb to its parent" }
    }

    /**
     * timeSeconds is monotonic elapsed time, not a wall clock. Missing audio uses timed breathing.
     * hovered is optional parent feedback; native HoverEffectComponent works independently of it.
     * This method never mutates root's transform or authorizes a question.
     */
    fun update(
        state: LearningVoiceControlState,
        hovered: Boolean,
        pressed: Boolean,
        timeSeconds: Double,
        audioLevel: Float? = null,
    ) {
        if (closed) return
        val frame = learningCrystalOrbFrame(state, hovered, pressed, timeSeconds, audioLevel)
        val validTime = timeSeconds.takeIf { it.isFinite() && it >= 0.0 }
        val dt =
            if (validTime != null) {
                previousTimeSeconds?.let { (validTime - it).coerceIn(0.0, 0.1) } ?: 0.0
            } else 0.0
        previousTimeSeconds = validTime
        swirlDegrees =
            (swirlDegrees + frame.swirlSpeedDegrees * dt.toFloat()) %
                LearningCrystalOrbMotion.swirlRepeatDegrees

        detailTransform.setScaleVector(Vector3.ONE * frame.visualScale)
        innerTransform.setScaleVector(Vector3.ONE * frame.innerScale)
        shellMaterial.setOpacity(frame.shellOpacity)
        shellMaterial.setBaseColor(frame.shellTint.color(1f))
        innerMaterial.setBaseColor(frame.innerTint.color(1f))
        innerMaterial.setEmissiveColor(frame.innerTint.color(frame.glow * 0.35f))
        for ((stream, transform, threads, veil) in currents) {
            transform.setEulerAngles(EulerAngles(0f, swirlDegrees * stream.speedRatio, 0f))
            transform.setScaleVector(Vector3.ONE * frame.sweepScale)
            threads.setEmissiveColor(stream.tint.color(frame.threadLight))
            veil.setOpacity(frame.veilOpacity)
        }
    }

    /** Detaches/destroys only this hierarchy and releases every owned native resource once. */
    override fun close() {
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
        // SpatialView may have already destroyed its hierarchy before Compose disposes this owner.
        release { if (root.valid) root.enabled = false }
        content?.let {
            release {
                if (root.valid) {
                    it.removeEntity(root)
                }
            }
        }
        release { if (root.valid) root.removeFromParent() }
        // Children first also handles partially-built or externally-detached owned entities.
        entities.asReversed().forEach { entity ->
            release { if (entity.valid) entity.destroy(recursively = false) }
        }
        resources.asReversed().forEach { resource ->
            release { if (resource.valid) resource.close() }
        }
        entities.clear()
        resources.clear()
        currents.clear()
        content = null
        attachedParent = null
        previousTimeSeconds = null
        failure?.let { throw it }
    }

    private fun build() {
        root.setName("LearningCrystalOrb")
        root.components.set(TransformComponent())
        shellMaterial =
            own(PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT)).apply {
                setRoughness(0.08f)
                setMetallic(0.08f)
                setEmissiveColor(Color4(0.028f, 0.046f, 0.085f, 1f))
                setCullingMode(MaterialCullingMode.BACK)
                setDepthWrite(false)
                setDepthTest(true)
            }
        val globe = mesh("GlassSurface", LearningCrystalOrbMeshes.shell())
        root.components.set(ModelComponent(globe, shellMaterial))
        root.components.set(
            CollisionComponent(
                collisionShape =
                    listOf(
                        own(
                            ShapeResource.createSphere(
                                LearningCrystalOrbGeometry.colliderRadiusMeters
                            )
                        )
                    ),
                physicsMaterial = own(PhysicsMaterialResource()),
            )
        )
        root.components.set(InteractableComponent())
        root.components.set(HoverEffectComponent())

        // The baked room has no IBL. SDK6 point lights do not cast shadows; keep the fill local.
        node("LocalFill", root, Vector3(0.08f, 0.055f, 0.135f))
            .entity
            .components
            .set(
                PointLightComponent(
                    color = Color4(1f, 0.87f, 0.69f, 1f),
                    intensity = 8f,
                    attenuationRadius = 0.35f,
                )
            )

        val details = node("Details", root)
        detailTransform = details.transform
        innerMaterial =
            own(PhysicallyBasedMaterial.create(BlendingMode.TRANSPARENT)).apply {
                setOpacity(LearningCrystalOrbGeometry.innerGlassOpacity)
                setRoughness(0.24f)
                setMetallic(0.15f)
                setDepthWrite(false)
                setDepthTest(true)
                setCullingMode(MaterialCullingMode.BACK)
            }
        innerTransform = model("InnerGlass", details.entity, globe, innerMaterial).transform

        // Two batches per color, independent of strand count. Reuse all native resources on update.
        for (stream in LearningCrystalOrbStream.entries) {
            val sweep = LearningCrystalOrbMeshes.sweep(stream)
            val current = node(stream.name, details.entity)
            val threadMaterial = pbr(stream.tint.color(0.34f), 0.22f, 0.18f)
            val bitmap =
                Bitmap.createBitmap(
                    LearningCrystalOrbCaustics.pixels(stream),
                    LearningCrystalOrbCaustics.width,
                    LearningCrystalOrbCaustics.height,
                    Bitmap.Config.ARGB_8888
                )
            val texture = own(TextureResource.create(bitmap))
            val veilMaterial =
                signalMaterial().apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(texture)
                }
            val veins = "${stream.name}Filaments"
            val veil = "${stream.name}Veils"
            model(veins, current.entity, mesh(veins, sweep.filaments), threadMaterial)
            model(veil, current.entity, mesh(veil, sweep.veils), veilMaterial)
            currents += StreamResources(stream, current.transform, threadMaterial, veilMaterial)
        }
    }

    private data class Part(val entity: Entity, val transform: TransformComponent)

    private fun node(
        name: String,
        parent: Entity,
        position: Vector3 = Vector3.ZERO,
        rotation: EulerAngles = EulerAngles(0f, 0f, 0f),
    ): Part {
        val entity = Entity()
        entities += entity
        entity.setName("LearningCrystalOrb_$name")
        val transform = TransformComponent().setPosition(position).setEulerAngles(rotation)
        entity.components.set(transform)
        check(parent.addChild(entity)) { "Could not attach crystal orb detail $name" }
        return Part(entity, transform)
    }

    private fun model(
        name: String,
        parent: Entity,
        mesh: MeshResource,
        material: Material,
        position: Vector3 = Vector3.ZERO,
        rotation: EulerAngles = EulerAngles(0f, 0f, 0f),
    ): Part =
        node(name, parent, position, rotation).also {
            it.entity.components.set(ModelComponent(mesh, material))
        }

    private fun pbr(color: Color4, roughness: Float, metallic: Float): PhysicallyBasedMaterial =
        own(PhysicallyBasedMaterial.create(BlendingMode.OPAQUE)).apply {
            setBaseColor(color)
            setRoughness(roughness)
            setMetallic(metallic)
        }

    private fun signalMaterial(): UnlitMaterial =
        own(UnlitMaterial.create(BlendingMode.ADD)).apply {
            setDepthWrite(false)
            setDepthTest(true)
            setCullingMode(MaterialCullingMode.BACK)
        }

    private fun mesh(name: String, geometry: LearningOrbMesh): MeshResource =
        own(
            MeshResource.createWithMeshModel(
                MeshModel(
                    positions = geometry.positions.map { Vector3(it.x, it.y, it.z) },
                    triangleIndices = geometry.triangleIndices,
                    normals = geometry.normals.map { Vector3(it.x, it.y, it.z) },
                    uv0 = geometry.uv.takeIf { it.isNotEmpty() }?.map { (u, v) -> Vector2(u, v) },
                ),
                name = "LearningCrystalOrb_$name",
            )
        )

    private fun <T : Resource> own(resource: T): T = resource.also { resources += it }

    private fun LearningCrystalOrbTint.color(brightness: Float) =
        Color4(red * brightness, green * brightness, blue * brightness, 1f)
}
