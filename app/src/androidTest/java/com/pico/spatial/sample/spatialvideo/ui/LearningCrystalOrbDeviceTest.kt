package com.pico.spatial.sample.spatialvideo.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sample.spatialvideo.STAGE_ID
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.LearningCourseCatalog
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Native SDK6 smoke test. Launches the app; requires an awake, rendering PICO headset. */
@RunWith(AndroidJUnit4::class)
class LearningCrystalOrbDeviceTest {
    @Test(timeout = 75_000)
    fun nativeOrbRendersStateUpdatesClosesTwiceAndRecreates() = runBlocking {
        withTimeout(60_000) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val launchIntent =
                checkNotNull(
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ) {
                        "Target app has no launcher activity"
                    }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // This blocking call must remain off Main; the JUnit timeout also bounds startup.
            instrumentation.startActivitySync(launchIntent)
            withContext(Dispatchers.Main) {
                val learning =
                    GlobalContext.get()
                        .getScope(VIDEO_SESSION_SCOPE_ID)
                        .get<LearningSessionViewModel>()
                learning.selectCourseVideo(LearningCourseCatalog.videos.first())
                val result = context.openStage(STAGE_ID, StageStyle.Mixed)
                assertTrue("Stage opening denied: $result", result is OpenStageResult.Allowed)
                val (content, appOrb) = awaitStageOrb()
                val sceneParent = checkNotNull(appOrb.getParent())
                val scene = checkNotNull(appOrb.scene)
                val frames = AtomicInteger()
                val updates =
                    content.subscribe(SceneEvents.Update::class.java, on = scene) {
                        frames.incrementAndGet()
                    }
                val parent = Entity()
                try {
                    parent.setName("LearningCrystalOrbDeviceTest_Parent")
                    parent.components.set(
                        TransformComponent()
                            .setPosition(
                                appOrb.components.get<TransformComponent>()!!.position +
                                    Vector3(0.42f, 0f, 0f)
                            )
                    )
                    content.addEntity(parent)
                    assertTrue(sceneParent.addChild(parent))
                    repeat(2) {
                        val renderer = LearningCrystalOrbRenderer(content, parent)
                        try {
                            renderer.attach(content, parent)
                            assertEquals(
                                "Repeated attach duplicated the root",
                                1,
                                parent.getChildrenCount()
                            )
                            val entities = entityTree(renderer.root).toList()
                            val resources = nativeResources(entities)
                            assertTrue(resources.isNotEmpty())
                            assertNotNull(renderer.root.components.get<CollisionComponent>())
                            assertNotNull(renderer.root.components.get<InteractableComponent>())
                            assertNotNull(renderer.root.components.get<HoverEffectComponent>())
                            for (name in
                                listOf(
                                    "CarvedWalnut",
                                    "AgedBrassInlay",
                                    "GatheringFilaments",
                                    "OrbitingRunes"
                                )) {
                                assertNotNull(
                                    "Missing generated model: $name",
                                    renderer.root
                                        .findEntity("LearningCrystalOrb_$name")
                                        ?.components
                                        ?.get<ModelComponent>()
                                )
                            }
                            val pose = renderer.root.components.get<TransformComponent>()!!.position
                            var time = 0.0
                            for (state in
                                listOf(
                                    LearningVoiceControlState.Ready,
                                    LearningVoiceControlState.Analyzing,
                                    LearningVoiceControlState.Answering,
                                    LearningVoiceControlState.Speaking,
                                    LearningVoiceControlState.Ready,
                                )) {
                                repeat(4) { tick ->
                                    val before = frames.get()
                                    renderer.update(
                                        state,
                                        hovered = tick == 1,
                                        pressed = tick == 2,
                                        timeSeconds = time,
                                        audioLevel = if (tick % 2 == 0) 0f else 1f
                                    )
                                    time += 1.0 / 30.0
                                    awaitSceneFrame(frames, before)
                                }
                                assertNativeGeometry(entities)
                                assertTrue(
                                    "A native resource became invalid during $state",
                                    resources.all { it.valid }
                                )
                                assertEquals(
                                    "Animation moved the grab root",
                                    pose,
                                    renderer.root.components.get<TransformComponent>()!!.position
                                )
                                val waiting =
                                    state == LearningVoiceControlState.Analyzing ||
                                        state == LearningVoiceControlState.Answering
                                assertEquals(
                                    waiting,
                                    renderer.root.findEntity("LearningCrystalOrb_Waiting")!!.enabled
                                )
                                assertEquals(
                                    state == LearningVoiceControlState.Speaking,
                                    renderer.root
                                        .findEntity("LearningCrystalOrb_Speaking")!!
                                        .enabled
                                )
                                assertEquals(scene, renderer.root.scene)
                            }
                            renderer.close()
                            renderer.close()
                            renderer.update(LearningVoiceControlState.Ready, false, false, time)
                            awaitSceneFrame(frames, frames.get())
                            assertTrue(
                                "Owned entities leaked after close",
                                entities.none { it.valid }
                            )
                            assertTrue(
                                "Owned meshes/materials/shapes leaked after close",
                                resources.none { it.valid }
                            )
                            assertEquals(0, parent.getChildrenCount())
                            assertFalse(content.entities.contains(renderer.root))
                        } finally {
                            renderer.close()
                        }
                        assertTrue("Closing a test orb destroyed the app orb", appOrb.valid)
                        assertTrue(sceneParent.valid)
                    }
                } finally {
                    updates.cancel()
                    if (parent.valid) {
                        content.removeEntity(parent)
                        parent.removeFromParent()
                        parent.destroy()
                    }
                }
            }
        }
    }

    private suspend fun awaitStageOrb(): Pair<SpatialViewContent, Entity> =
        withTimeout(30_000) {
            var found: Pair<SpatialViewContent, Entity>? = null
            while (found == null) {
                found =
                    WindowInspector.getGlobalWindowViews()
                        .asSequence()
                        .flatMap(::viewTree)
                        .filter { it.isAttachedToWindow && it.isShown }
                        .mapNotNull { view ->
                            if (
                                view.javaClass.name != "com.pico.spatial.core.container.SpatialView"
                            )
                                return@mapNotNull null
                            // SDK6's Android wrapper is Kotlin-internal. Keep this bridge
                            // test-only.
                            val content =
                                view.javaClass.getMethod("getContent").invoke(view)
                                    as SpatialViewContent
                            content.entities
                                .asSequence()
                                .flatMap(::entityTree)
                                .firstOrNull {
                                    it.valid &&
                                        it.getName() == "LearningCrystalOrb" &&
                                        it.scene != null
                                }
                                ?.let { content to it }
                        }
                        .firstOrNull()
                if (found == null) delay(100)
            }
            found
        }

    private suspend fun awaitSceneFrame(frames: AtomicInteger, after: Int) =
        withTimeout(5_000) { while (frames.get() <= after) delay(10) }

    private fun assertNativeGeometry(entities: List<Entity>) {
        entities.forEach { entity ->
            assertTrue("Invalid entity: ${entity.getName()}", entity.valid)
            assertNotNull(entity.components.get<TransformComponent>())
            entity.components.get<ModelComponent>()?.let { model ->
                val mesh = checkNotNull(model.mesh)
                assertTrue(mesh.valid)
                val size = mesh.getBoundingBox().size
                assertTrue(
                    "Empty or invalid native bounds: ${entity.getName()}",
                    listOf(size.x, size.y, size.z).all { it.isFinite() && it > 0f }
                )
                assertTrue(model.materials.size > 0)
                model.materials.forEach { assertTrue("Invalid native material", it.valid) }
            }
        }
    }

    private fun nativeResources(entities: List<Entity>): List<Resource> =
        buildList {
                entities.forEach { entity ->
                    entity.components.get<ModelComponent>()?.let { model ->
                        add(checkNotNull(model.mesh))
                        addAll(model.materials.toList())
                    }
                    entity.components.get<CollisionComponent>()?.let { collision ->
                        addAll(collision.collisionShape)
                        add(checkNotNull(collision.physicsMaterial))
                    }
                }
            }
            .distinct()

    private fun entityTree(root: Entity): Sequence<Entity> = sequence {
        yield(root)
        root.getChildren().forEach { yieldAll(entityTree(it)) }
    }

    private fun viewTree(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) repeat(root.childCount) { yieldAll(viewTree(root.getChildAt(it))) }
    }
}
