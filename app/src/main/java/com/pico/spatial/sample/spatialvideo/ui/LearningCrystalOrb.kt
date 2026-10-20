package com.pico.spatial.sample.spatialvideo.ui

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
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import java.io.Closeable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    private lateinit var coreMaterial: PhysicallyBasedMaterial
    private lateinit var haloMaterial: UnlitMaterial
    private lateinit var starMaterial: UnlitMaterial
    private lateinit var waitingMaterial: UnlitMaterial
    private lateinit var speakingMaterial: UnlitMaterial
    private lateinit var detailTransform: TransformComponent
    private lateinit var coreTransform: TransformComponent
    private lateinit var haloTransform: TransformComponent
    private lateinit var innerOrbitTransform: TransformComponent
    private lateinit var waitingEntity: Entity
    private lateinit var swirlTransform: TransformComponent
    private lateinit var runeTransform: TransformComponent
    private lateinit var speakingEntity: Entity
    private lateinit var speakingTransform: TransformComponent
    private val orbitTransforms = mutableListOf<TransformComponent>()
    private var previousTimeSeconds: Double? = null
    private var orbitDegrees = 0f
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
        orbitDegrees =
            (orbitDegrees + frame.orbitSpeedDegrees * dt.toFloat()) %
                LearningCrystalOrbMotion.orbitRepeatDegrees
        swirlDegrees =
            (swirlDegrees + frame.swirlSpeedDegrees * dt.toFloat()) %
                LearningCrystalOrbMotion.swirlRepeatDegrees

        detailTransform.setScaleVector(Vector3.ONE * frame.visualScale)
        coreTransform.setScaleVector(Vector3.ONE * frame.coreScale)
        coreTransform.setEulerAngles(EulerAngles(12f, -orbitDegrees * 0.6f, 18f))
        haloTransform.setScaleVector(Vector3.ONE * (0.98f + (frame.coreScale - 1f) * 0.4f))
        innerOrbitTransform.setEulerAngles(EulerAngles(62f, orbitDegrees * 0.8f, 26f))
        waitingEntity.enabled = frame.waitingGlow > 0f
        swirlTransform.setEulerAngles(EulerAngles(18f, swirlDegrees, -12f))
        swirlTransform.setScaleVector(Vector3.ONE * frame.gatheringScale)
        runeTransform.setEulerAngles(EulerAngles(18f, -swirlDegrees * 0.35f, 12f))
        speakingEntity.enabled = frame.speakingGlow > 0f
        speakingTransform.setScaleVector(Vector3.ONE * frame.speakingScale)
        orbitTransforms.forEachIndexed { index, transform ->
            transform.setEulerAngles(
                if (index == 0) EulerAngles(54f, orbitDegrees, 18f)
                else EulerAngles(-38f, -orbitDegrees * 0.72f, 66f)
            )
        }
        val tint = frame.tint
        shellMaterial.setOpacity(frame.shellOpacity)
        shellMaterial.setBaseColor(
            Color4(
                0.64f + tint.red * 0.25f,
                0.68f + tint.green * 0.22f,
                0.76f + tint.blue * 0.2f,
                1f
            )
        )
        shellMaterial.setEmissiveColor(tint.color(frame.glow * 0.045f))
        coreMaterial.setBaseColor(tint.color(0.3f))
        coreMaterial.setEmissiveColor(tint.color(frame.glow))
        haloMaterial.setBaseColor(tint.color(frame.glow))
        haloMaterial.setOpacity(0.06f + frame.glow * 0.12f)
        starMaterial.setBaseColor(tint.color(0.25f + frame.glow * 0.75f))
        waitingMaterial.setBaseColor(tint.color(0.9f))
        waitingMaterial.setOpacity(frame.waitingGlow)
        speakingMaterial.setBaseColor(tint.color(0.9f))
        speakingMaterial.setOpacity(frame.speakingGlow)
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
        orbitTransforms.clear()
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
                setCullingMode(MaterialCullingMode.BACK)
                setDepthWrite(false)
                setDepthTest(true)
            }
        root.components.set(
            ModelComponent(
                own(MeshResource.createSphere(LearningCrystalOrbGeometry.shellRadiusMeters)),
                shellMaterial
            )
        )
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
        coreMaterial = pbr(Color4(0.12f, 0.24f, 0.32f, 1f), 0.16f, 0.18f)
        haloMaterial =
            own(UnlitMaterial.create(BlendingMode.ADD)).apply {
                setDepthWrite(false)
                setDepthTest(true)
                setCullingMode(MaterialCullingMode.BACK)
            }
        starMaterial = own(UnlitMaterial.create(BlendingMode.OPAQUE))
        val brass = pbr(Color4(0.42f, 0.27f, 0.10f, 1f), 0.42f, 0.72f)
        val patinatedBrass = pbr(Color4(0.19f, 0.22f, 0.15f, 1f), 0.52f, 0.58f)
        val walnut = pbr(Color4(0.075f, 0.030f, 0.018f, 1f), 0.46f, 0f)
        waitingMaterial = signalMaterial()
        speakingMaterial = signalMaterial()

        val core = node("LuminousCore", details.entity)
        coreTransform = core.transform
        model("Heart", core.entity, own(MeshResource.createSphere(0.036f)), coreMaterial)
        val crystalTip = own(MeshResource.createCone(0.048f, 0.027f))
        model("UpperCrystal", core.entity, crystalTip, coreMaterial, Vector3(0f, 0.024f, 0f))
        model(
            "LowerCrystal",
            core.entity,
            crystalTip,
            coreMaterial,
            Vector3(0f, -0.024f, 0f),
            EulerAngles(180f, 0f, 0f)
        )
        haloTransform =
            model(
                    "InnerLight",
                    details.entity,
                    own(MeshResource.createSphere(0.078f)),
                    haloMaterial
                )
                .transform
        val innerOrbit = node("InnerOrbit", details.entity)
        innerOrbitTransform = innerOrbit.transform
        model(
            "LightFilament",
            innerOrbit.entity,
            own(MeshResource.createTorus(0.087f, 0.085f)),
            starMaterial
        )
        model(
            "InnerMeridian",
            innerOrbit.entity,
            own(MeshResource.createTorus(0.065f, 0.0638f)),
            patinatedBrass,
            rotation = EulerAngles(72f, 25f, 12f)
        )

        val starMesh = own(MeshResource.createSphere(0.0028f))
        repeat(2) { index ->
            val orbit = node("Orbit$index", details.entity)
            orbitTransforms += orbit.transform
            val radius = if (index == 0) 0.145f else 0.153f
            model(
                "OrbitRing$index",
                orbit.entity,
                own(MeshResource.createTorus(radius, radius - 0.002f)),
                if (index == 0) brass else patinatedBrass
            )
            repeat(2) { star ->
                val angle = star * PI + index * PI / 2.0
                val center =
                    Vector3(
                        (cos(angle) * (radius - 0.001f)).toFloat(),
                        0f,
                        (sin(angle) * (radius - 0.001f)).toFloat()
                    )
                val sparkle = node("Star$index$star", orbit.entity, center)
                for (scale in
                    listOf(
                        Vector3(2.6f, 0.6f, 0.6f),
                        Vector3(0.6f, 2.6f, 0.6f),
                        Vector3(0.6f, 0.6f, 2.6f)
                    )) {
                    model("StarRay", sparkle.entity, starMesh, starMaterial)
                        .transform
                        .setScaleVector(scale)
                }
            }
        }

        model(
            "CarvedWalnut",
            details.entity,
            mesh("Walnut", LearningCrystalOrbMeshes.pedestalWood()),
            walnut
        )
        model(
            "AgedBrassInlay",
            details.entity,
            mesh("Brass", LearningCrystalOrbMeshes.pedestalBrass()),
            brass
        )

        val waiting = node("Waiting", details.entity)
        waitingEntity = waiting.entity
        swirlTransform =
            model(
                    "GatheringFilaments",
                    waiting.entity,
                    mesh("Swirl", LearningCrystalOrbMeshes.waitingSwirl()),
                    waitingMaterial
                )
                .transform
        runeTransform =
            model(
                    "OrbitingRunes",
                    waiting.entity,
                    mesh("Runes", LearningCrystalOrbMeshes.waitingRunes()),
                    waitingMaterial
                )
                .transform

        val speaking = node("Speaking", details.entity, rotation = EulerAngles(16f, 0f, 0f))
        speakingEntity = speaking.entity
        speakingTransform = speaking.transform
        val speakingRing = own(MeshResource.createTorus(0.134f, 0.1315f))
        model("VoiceResonance", speaking.entity, speakingRing, speakingMaterial)
        model(
                "VoiceEcho",
                speaking.entity,
                speakingRing,
                speakingMaterial,
                rotation = EulerAngles(24f, 0f, 0f)
            )
            .transform
            .setScaleVector(Vector3.ONE * 0.86f)
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
                ),
                name = "LearningCrystalOrb_$name",
            )
        )

    private fun <T : Resource> own(resource: T): T = resource.also { resources += it }

    private fun LearningCrystalOrbTint.color(brightness: Float) =
        Color4(red * brightness, green * brightness, blue * brightness, 1f)
}
