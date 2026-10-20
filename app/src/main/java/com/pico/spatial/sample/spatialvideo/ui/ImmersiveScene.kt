/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ResourceLoadingException
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sample.spatialvideo.CATALOG_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.HISTORY_WINDOW_CONTAINER_ID
import com.pico.spatial.sample.spatialvideo.HISTORY_WINDOW_TAG
import com.pico.spatial.sample.spatialvideo.R
import com.pico.spatial.sample.spatialvideo.data.ACADEMIC_LECTURE_HALL_SCENE
import com.pico.spatial.sample.spatialvideo.data.lectureHallAssetBundle
import com.pico.spatial.sample.spatialvideo.di.VIDEO_SESSION_SCOPE_ID
import com.pico.spatial.sample.spatialvideo.learning.LearningSessionViewModel
import com.pico.spatial.sample.spatialvideo.learning.TranscriptCueSelector
import com.pico.spatial.sample.spatialvideo.learning.TranscriptSegment
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.panelSize
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.hover.tween
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.getKoin

@Composable
fun ImmersiveScene() {
    val scope = getKoin().getScope(VIDEO_SESSION_SCOPE_ID)
    val videoViewModel: VideoViewModel = remember(scope) { scope.get<VideoViewModel>() }
    val learningViewModel: LearningSessionViewModel =
        remember(scope) { scope.get<LearningSessionViewModel>() }
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val coroutineScope = rememberCoroutineScope()
    val spatialNavigator = LocalSpatialNavigator.current
    val learningState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val playbackTime by videoViewModel.playbackTime.collectAsStateWithLifecycle()
    val isStageOnstage by LocalSpatialContainerStateManager.current.isOnstage
    val stageVisibilityTracker = remember { StageVisibilityTracker() }
    val layoutStore = remember(context) { LearningSpatialLayoutStore(context.applicationContext) }
    val dragState = remember { LearningPanelDragState(layoutStore.load()) }
    val hmdTrackingProvider = remember { HMDTrackingProvider() }
    val outlinePanelSize =
        panelSize(
            LearningStageDimensions.OutlineWidth,
            LearningStageDimensions.SidePanelHeight + LearningStageDimensions.PanelFooterHeight,
        )
    val toolbarPanelSize =
        panelSize(
            LearningStageDimensions.ToolbarWidth,
            LearningStageDimensions.ToolbarHeight + LearningStageDimensions.PanelFooterHeight,
        )
    val aiCoachPanelSize =
        panelSize(
            LearningStageDimensions.AiCoachWidth,
            LearningStageDimensions.SidePanelHeight + LearningStageDimensions.PanelFooterHeight,
        )
    val subtitlePanelSize =
        panelSize(
            LearningStageDimensions.SubtitleWidth,
            LearningStageDimensions.SubtitleHeight,
        )
    val dragBarPanelSize =
        panelSize(
            LearningStageDimensions.StandaloneDragBarSurfaceWidth,
            LearningStageDimensions.DragBarHeight,
        )
    var layoutStatus by remember { mutableStateOf<String?>(null) }
    val dragRuntime = remember { StagePanelDragRuntime(dragState) }
    val voiceOrbInput = remember { StageVoiceOrbInput(dragState) }
    val currentVoicePolicy by rememberUpdatedState(learningVoiceControlPolicy(learningState))
    val currentVoiceAction by
        rememberUpdatedState(rememberAskVoiceQuestionAction(learningViewModel))
    val currentlyOnstage by rememberUpdatedState(isStageOnstage)
    val sceneInputOwner = remember { Any() }
    var outlineEntity by remember { mutableStateOf<Entity?>(null) }
    var toolbarEntity by remember { mutableStateOf<Entity?>(null) }
    var aiCoachEntity by remember { mutableStateOf<Entity?>(null) }
    var subtitleEntity by remember { mutableStateOf<Entity?>(null) }
    var voiceControlEntity by remember { mutableStateOf<Entity?>(null) }
    var voiceOrb by remember { mutableStateOf<LearningCrystalOrbRenderer?>(null) }
    var lectureHallEntity by remember { mutableStateOf<Entity?>(null) }
    var blackboard by remember { mutableStateOf<LearningBlackboard?>(null) }
    var sceneRoot by remember { mutableStateOf<Entity?>(null) }
    var sceneGeometry by remember { mutableStateOf<LearningStageGeometry?>(null) }
    var sceneRootPose by remember { mutableStateOf(LearningStagePoseResolver.Fallback) }
    var sceneEyeHeight by remember { mutableStateOf(LearningStagePoseResolver.Fallback.y) }
    var environmentMode by remember { mutableStateOf(LearningEnvironmentModePolicy.initial) }
    val subtitle =
        TranscriptCueSelector.current(
            learningState.transcriptSegments,
            playbackTime,
            learningState.subtitleOffsetMs,
        )
    val layoutAdjustedMessage = stringResource(R.string.learning_layout_adjusted)
    val layoutRestoredMessage = stringResource(R.string.learning_layout_restored)
    val layoutSavedMessage = stringResource(R.string.learning_layout_saved)
    val layoutSaveFailedMessage = stringResource(R.string.learning_layout_save_failed)
    val windowCoordinator =
        remember(spatialNavigator) {
            LearningWindowCoordinator(
                openStage = { false },
                minimizeCatalog = {},
                minimizeHistory = {},
                restoreCatalog = {
                    spatialNavigator.restoreWindowContainer(CATALOG_WINDOW_CONTAINER_ID, null)
                },
                restoreHistory = {
                    spatialNavigator.restoreWindowContainer(
                        HISTORY_WINDOW_CONTAINER_ID,
                        HISTORY_WINDOW_TAG,
                    )
                },
            )
        }

    fun applyPanelPose(panel: LearningPanel, pose: SpatialPanelPose) {
        when (panel) {
            LearningPanel.Video -> videoViewModel.videoPanel
            LearningPanel.Outline -> outlineEntity
            LearningPanel.Toolbar -> toolbarEntity
            LearningPanel.AiCoach -> aiCoachEntity
            LearningPanel.VoiceControl -> voiceControlEntity
        }?.applyPose(pose)
        if (com.pico.spatial.sample.spatialvideo.BuildConfig.DEBUG) {
            Log.i(
                "LearningDrag",
                org.json
                    .JSONObject()
                    .put("pid", android.os.Process.myPid())
                    .put("panel", panel.name)
                    .put("x", pose.x)
                    .put("y", pose.y)
                    .put("z", pose.z)
                    .toString()
            )
        }
    }

    fun finishPanelDrag() {
        layoutStatus = layoutAdjustedMessage
    }

    fun restoreCourseCatalog() {
        windowCoordinator.restoreSharedWindows()
    }

    LaunchedEffect(Unit) { lectureHallAssetBundle.start() }

    LaunchedEffect(layoutStatus) {
        if (layoutStatus != null) {
            delay(LAYOUT_STATUS_DURATION_MS)
            layoutStatus = null
        }
    }

    LaunchedEffect(isStageOnstage) {
        if (!isStageOnstage) {
            dragRuntime.cancelAll()
            voiceOrbInput.reset()
        }
        val shouldEndSession =
            stageVisibilityTracker.shouldEndSession(
                isStageOnstage,
                learningViewModel.uiState.value.sessionId
            )
        if (isStageOnstage) {
            environmentMode = LearningEnvironmentModePolicy.initial
            lectureHallEntity?.enabled = true
            videoViewModel.initialize(converter, context)
            videoViewModel.toggleFullSpace(true)
            videoViewModel.preparePlayback()
        } else if (shouldEndSession) {
            hmdTrackingProvider.stop()
            if (
                stageVisibilityTracker.ownerSessionId?.let(learningViewModel::onSessionExit) == true
            ) {
                videoViewModel.toggleFullSpace(false)
                restoreCourseCatalog()
            }
        }
    }

    LaunchedEffect(voiceOrb, isStageOnstage) {
        val orb = voiceOrb ?: return@LaunchedEffect
        if (!isStageOnstage) return@LaunchedEffect
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (frame - previousFrame >= 32_000_000L) {
                    previousFrame = frame
                    orb.update(
                        currentVoicePolicy.state,
                        hovered = false,
                        pressed = voiceOrbInput.isPressed,
                        timeSeconds = frame / 1_000_000_000.0
                    )
                }
            }
        }
    }

    DisposableEffect(stageVisibilityTracker) {
        onDispose {
            dragRuntime.clear()
            voiceOrbInput.clear()
            runCatching { voiceOrb?.close() }
                .onFailure {
                    Log.w(LECTURE_HALL_TAG, "Unable to fully release the voice crystal", it)
                }
            runCatching { blackboard?.close() }
                .onFailure { Log.w(LECTURE_HALL_TAG, "Unable to fully release the blackboard", it) }
            hmdTrackingProvider.stop()
            if (
                stageVisibilityTracker.ownerSessionId?.let(learningViewModel::onSessionExit) == true
            ) {
                videoViewModel.toggleFullSpace(false)
                restoreCourseCatalog()
            }
        }
    }

    SpatialView(
        modifier =
            Modifier.fillMaxSize().pointerInput(context, dragRuntime, voiceOrbInput) {
                try {
                    detectSpatialPointerEvent(
                        context = context,
                        targetedToEntity =
                            TargetEntity.any {
                                dragRuntime.isHandle(it) || voiceOrbInput.isTarget(it)
                            },
                    ) { events ->
                        val orbConsumed =
                            voiceOrbInput.onEvents(sceneInputOwner, events) {
                                if (currentlyOnstage && currentVoicePolicy.isEnabled)
                                    currentVoiceAction()
                            }
                        val panelConsumed = dragRuntime.onEvents(sceneInputOwner, events)
                        orbConsumed || panelConsumed
                    }
                } finally {
                    dragRuntime.cancel(sceneInputOwner)
                    voiceOrbInput.reset()
                }
            },
        update = { _, _ ->
            val currentLayout = dragState.snapshot()
            videoViewModel.videoPanel.applyPose(currentLayout.video)
            outlineEntity?.applyPose(currentLayout.outline)
            toolbarEntity?.applyPose(currentLayout.toolbar)
            aiCoachEntity?.applyPose(currentLayout.aiCoach)
            voiceControlEntity?.applyPose(currentLayout.voiceControl)
            subtitleEntity?.apply {
                applyPose(LearningStageLocalPose.Subtitle)
                enabled = subtitle != null
            }
            lectureHallEntity?.enabled =
                LearningEnvironmentModePolicy.showsVirtualHall(environmentMode)
            blackboard?.root?.enabled =
                LearningEnvironmentModePolicy.showsVirtualHall(environmentMode)
        },
        attachments = {
            AttachmentPanel(
                id = OUTLINE_ATTACHMENT_ID,
                size = outlinePanelSize,
            ) {
                LearningStagePanelTheme(
                    LearningPanel.Outline,
                    dragRuntime,
                    LearningStageDimensions.OutlineWidth,
                    LearningStageDimensions.SidePanelHeight
                ) {
                    LearningOutlineSpacePanel(
                        modifier =
                            Modifier.size(
                                LearningStageDimensions.OutlineWidth,
                                LearningStageDimensions.SidePanelHeight,
                            )
                    )
                }
            }
            AttachmentPanel(
                id = TOOLBAR_ATTACHMENT_ID,
                size = toolbarPanelSize,
            ) {
                LearningStagePanelTheme(
                    LearningPanel.Toolbar,
                    dragRuntime,
                    LearningStageDimensions.ToolbarWidth,
                    LearningStageDimensions.ToolbarHeight
                ) {
                    LearningPlaybackToolbarPanel(
                        modifier =
                            Modifier.size(
                                LearningStageDimensions.ToolbarWidth,
                                LearningStageDimensions.ToolbarHeight,
                            ),
                        layoutStatus = layoutStatus,
                        environmentMode = environmentMode,
                        onToggleEnvironment = {
                            environmentMode = LearningEnvironmentModePolicy.toggle(environmentMode)
                        },
                        onExit = {
                            if (
                                stageVisibilityTracker.ownerSessionId?.let(
                                    learningViewModel::onSessionExit
                                ) == true
                            ) {
                                coroutineScope.launch(Dispatchers.Main.immediate) {
                                    videoViewModel.toggleFullSpace(false)
                                    spatialNavigator.closeStage()
                                    restoreCourseCatalog()
                                }
                            }
                        },
                        onRestoreLayout = {
                            dragRuntime.cancelAll()
                            voiceOrbInput.reset()
                            val geometry = sceneGeometry
                            val saved = layoutStore.load().dockedToBlackboard()
                            val restored = geometry?.alignPanels(saved) ?: saved
                            if (geometry != null) {
                                sceneRootPose =
                                    sceneRootPose.copy(
                                        y = geometry.rootHeight(sceneEyeHeight, restored)
                                    )
                                sceneRoot?.applyRootPose(sceneRootPose)
                                dragState.setFloorLimits(geometry.minimumCenterY(sceneRootPose.y))
                            }
                            dragState.replace(restored)
                            layoutStatus = layoutRestoredMessage
                        },
                        onSaveLayout = {
                            layoutStatus =
                                if (layoutStore.save(dragState.snapshot())) {
                                    layoutSavedMessage
                                } else {
                                    layoutSaveFailedMessage
                                }
                        },
                    )
                }
            }
            AttachmentPanel(
                id = AI_COACH_ATTACHMENT_ID,
                size = aiCoachPanelSize,
            ) {
                LearningStagePanelTheme(
                    LearningPanel.AiCoach,
                    dragRuntime,
                    LearningStageDimensions.AiCoachWidth,
                    LearningStageDimensions.SidePanelHeight
                ) {
                    LearningAiCoachSpacePanel(
                        modifier =
                            Modifier.size(
                                LearningStageDimensions.AiCoachWidth,
                                LearningStageDimensions.SidePanelHeight,
                            )
                    )
                }
            }
            AttachmentPanel(
                id = SUBTITLE_ATTACHMENT_ID,
                size = subtitlePanelSize,
            ) {
                PicoTheme { LearningSubtitlePanel(subtitle) }
            }
            AttachmentPanel(
                id = VIDEO_DRAG_BAR_ATTACHMENT_ID,
                size = dragBarPanelSize,
                alignment = AttachmentPanelComponent.Alignment.CENTER,
            ) {
                StandaloneLearningPanelDragBar(LearningPanel.Video, dragRuntime)
            }
        },
        initial = { content, attachments ->
            val metersPerDp =
                content
                    .convertSize(
                        Vector3(converter.density, converter.density, 0f),
                        ViewCoordinateSpace.Local,
                        content.localSpatialCoordinateSpace,
                    )
                    .y
            val geometry = LearningStageGeometry(metersPerDp)
            sceneGeometry = geometry
            val currentLayout = geometry.alignPanels(dragState.snapshot())
            dragState.replace(currentLayout)
            hmdTrackingProvider.start()
            videoViewModel.initialize(converter, context)
            videoViewModel.toggleFullSpace(true)

            val headSamples = mutableListOf<StageHeadPose>()
            withTimeoutOrNull(HMD_POSE_TIMEOUT_MS) {
                hmdTrackingProvider.dataFlow.take(HMD_SAMPLE_COUNT).collect { tracked ->
                    tracked.hmdPose
                        .let { pose ->
                            StageHeadPose(
                                x = pose.position.x,
                                y = pose.position.y,
                                z = pose.position.z,
                                yawDegrees = pose.rotation.toEulerAngles().yaw,
                            )
                        }
                        .takeIf(StageHeadPoseStabilizer::isPlausible)
                        ?.let(headSamples::add)
                }
            }
            val headPose =
                headSamples
                    .takeIf(List<StageHeadPose>::isNotEmpty)
                    ?.let(StageHeadPoseStabilizer::average)
            val eyePose =
                headPose?.let(LearningStagePoseResolver::resolve)
                    ?: LearningStagePoseResolver.Fallback
            val rootPose = eyePose.copy(y = geometry.rootHeight(eyePose.y, currentLayout))
            sceneEyeHeight = eyePose.y
            sceneRootPose = rootPose
            dragState.setFloorLimits(geometry.minimumCenterY(rootPose.y))
            val lectureHallPose =
                headPose?.let(LearningStagePoseResolver::resolveLectureHall)
                    ?: LearningStagePoseResolver.LectureHallFallback

            lectureHallEntity =
                try {
                        withContext(Dispatchers.IO) {
                            lectureHallAssetBundle.await().loadModel(ACADEMIC_LECTURE_HALL_SCENE)
                        }
                    } catch (error: ResourceLoadingException) {
                        Log.e(LECTURE_HALL_TAG, "Unable to load the academic lecture hall", error)
                        null
                    }
                    ?.apply {
                        applyRootPose(lectureHallPose)
                        enabled = LearningEnvironmentModePolicy.showsVirtualHall(environmentMode)
                        content.addEntity(this)
                    }
            val root =
                Entity().apply {
                    components.set(TransformComponent())
                    applyRootPose(rootPose)
                }
            content.addEntity(root)
            sceneRoot = root
            blackboard?.close()
            val boardTexture =
                withContext(Dispatchers.IO) { TextureResource.load("materials/academic-oak.png") }
            blackboard =
                LearningBlackboard(content, root, geometry.blackboard(), boardTexture).also {
                    it.root.enabled =
                        LearningEnvironmentModePolicy.showsVirtualHall(environmentMode)
                }
            dragRuntime.bind(
                content,
                root,
                metersPerDp / converter.density,
                ::applyPanelPose,
                ::finishPanelDrag
            )

            videoViewModel.videoPanel.applyPose(currentLayout.video)
            content.addEntity(videoViewModel.videoPanel)
            root.addChild(videoViewModel.videoPanel)

            outlineEntity =
                createLearningPanelRoot(
                    content,
                    root,
                    attachments.entity(OUTLINE_ATTACHMENT_ID),
                    currentLayout.outline,
                    geometry.frame(LearningPanel.Outline)
                )
            toolbarEntity =
                createLearningPanelRoot(
                    content,
                    root,
                    attachments.entity(TOOLBAR_ATTACHMENT_ID),
                    currentLayout.toolbar,
                    geometry.frame(LearningPanel.Toolbar)
                )
            aiCoachEntity =
                createLearningPanelRoot(
                    content,
                    root,
                    attachments.entity(AI_COACH_ATTACHMENT_ID),
                    currentLayout.aiCoach,
                    geometry.frame(LearningPanel.AiCoach)
                )
            subtitleEntity =
                attachments.entity(SUBTITLE_ATTACHMENT_ID)?.apply {
                    applyPose(LearningStageLocalPose.Subtitle)
                    enabled = subtitle != null
                    content.addEntity(this)
                    videoViewModel.videoPanel.addChild(this)
                }
            voiceOrb?.close()
            val orb = LearningCrystalOrbRenderer(content, root)
            voiceOrb = orb
            voiceControlEntity = orb.root.apply { applyPose(currentLayout.voiceControl) }
            voiceOrbInput.bind(
                content,
                root,
                orb.root,
                { applyPanelPose(LearningPanel.VoiceControl, it) },
                ::finishPanelDrag
            )

            val videoHandle = Entity()
            val outlineHandle = Entity()
            val toolbarHandle = Entity()
            val coachHandle = Entity()
            attachDragBar(
                content = content,
                entity = videoHandle,
                parent = videoViewModel.videoPanel,
                panel = LearningPanel.Video,
                panelHeight = (geometry.videoContentLowerExtent * 2f / metersPerDp).dp,
                barWidth = LearningStageDimensions.DragBarWidth,
                converter = converter,
                runtime = dragRuntime,
                surface = attachments.entity(VIDEO_DRAG_BAR_ATTACHMENT_ID),
                handleScale = LearningStageDimensions.PanelScale,
            )
            attachDragBar(
                content = content,
                entity = outlineHandle,
                parent = outlineEntity,
                panel = LearningPanel.Outline,
                panelHeight = LearningStageDimensions.SidePanelHeight,
                barWidth = LearningStageDimensions.DragBarWidth,
                converter = converter,
                runtime = dragRuntime,
            )
            attachDragBar(
                content = content,
                entity = toolbarHandle,
                parent = toolbarEntity,
                panel = LearningPanel.Toolbar,
                panelHeight = LearningStageDimensions.ToolbarHeight,
                barWidth = LearningStageDimensions.DragBarWidth,
                converter = converter,
                runtime = dragRuntime,
            )
            attachDragBar(
                content = content,
                entity = coachHandle,
                parent = aiCoachEntity,
                panel = LearningPanel.AiCoach,
                panelHeight = LearningStageDimensions.SidePanelHeight,
                barWidth = LearningStageDimensions.DragBarWidth,
                converter = converter,
                runtime = dragRuntime,
            )
            videoViewModel.preparePlayback()
            if (com.pico.spatial.sample.spatialvideo.BuildConfig.DEBUG) {
                coroutineScope.launch {
                    delay(2_000)
                    val videoY = videoViewModel.videoPanel.convertPositionTo(Vector3.ZERO, null).y
                    listOf(
                            Triple(LearningPanel.Video, videoViewModel.videoPanel, videoHandle),
                            Triple(
                                LearningPanel.Outline,
                                attachments.entity(OUTLINE_ATTACHMENT_ID),
                                outlineHandle
                            ),
                            Triple(
                                LearningPanel.AiCoach,
                                attachments.entity(AI_COACH_ATTACHMENT_ID),
                                coachHandle
                            ),
                            Triple(
                                LearningPanel.Toolbar,
                                attachments.entity(TOOLBAR_ATTACHMENT_ID),
                                toolbarHandle
                            ),
                        )
                        .forEach { (panel, surface, handle) ->
                            surface?.let {
                                val standalone = panel == LearningPanel.Video
                                val frame = if (standalone) null else geometry.frame(panel)
                                val bodyOffset = frame?.surfaceY?.let { offset -> -offset } ?: 0f
                                val bodyHeight = frame?.bodyHeight ?: geometry.bodyHeight(panel)
                                val handleHeight =
                                    LearningStageDimensions.DragBarHeight.value * metersPerDp
                                fun surfaceY(y: Float) =
                                    it.convertPositionTo(Vector3(0f, y, 0f), null).y
                                fun handleY(y: Float) =
                                    handle.convertPositionTo(Vector3(0f, y, 0f), null).y
                                fun worldWidth(entity: Entity, width: Dp): Float {
                                    val start = entity.convertPositionTo(Vector3.ZERO, null)
                                    val end =
                                        entity.convertPositionTo(
                                            Vector3(width.value * metersPerDp, 0f, 0f),
                                            null
                                        )
                                    val delta = end - start
                                    return kotlin.math.sqrt(
                                        delta.x * delta.x + delta.y * delta.y + delta.z * delta.z
                                    )
                                }
                                val report =
                                    org.json
                                        .JSONObject()
                                        .put("pid", android.os.Process.myPid())
                                        .put("panel", panel.name)
                                        .put("videoY", videoY)
                                        .put("centerY", surfaceY(bodyOffset))
                                        .put("bottomY", surfaceY(bodyOffset - bodyHeight / 2f))
                                        .put("topY", surfaceY(bodyOffset + bodyHeight / 2f))
                                        .put("handleTopY", handleY(handleHeight / 2f))
                                        .put("handleBottomY", handleY(-handleHeight / 2f))
                                        .put(
                                            "handleWidth",
                                            worldWidth(handle, LearningStageDimensions.DragBarWidth)
                                        )
                                if (standalone) {
                                    val barSurface =
                                        attachments.entity(VIDEO_DRAG_BAR_ATTACHMENT_ID)
                                    barSurface?.let { bar ->
                                        report.put(
                                            "handleSurfaceWidth",
                                            worldWidth(
                                                bar,
                                                LearningStageDimensions
                                                    .StandaloneDragBarSurfaceWidth
                                            )
                                        )
                                    }
                                }
                                if (panel == LearningPanel.Video) {
                                    report.put(
                                        "subtitleBottomY",
                                        surfaceY(-geometry.videoContentLowerExtent)
                                    )
                                }
                                Log.i("LearningGeometry", report.toString())
                            }
                        }
                }
            }
        },
    )
}

@Composable
private fun LearningStagePanelTheme(
    panel: LearningPanel,
    dragRuntime: StagePanelDragRuntime,
    width: Dp,
    bodyHeight: Dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density, density.fontScale * LearningStageDimensions.PanelFontScale)
    ) {
        PicoTheme {
            Column(
                modifier =
                    Modifier.size(width, bodyHeight + LearningStageDimensions.PanelFooterHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width, bodyHeight)) { content() }
                Spacer(Modifier.height(LearningStageDimensions.DragBarGap))
                LearningPanelDragBar(LearningStageDimensions.DragBarWidth, panel, dragRuntime)
            }
        }
    }
}

@Composable
private fun StandaloneLearningPanelDragBar(
    panel: LearningPanel,
    dragRuntime: StagePanelDragRuntime
) {
    Box(
        modifier =
            Modifier.size(
                LearningStageDimensions.StandaloneDragBarSurfaceWidth,
                LearningStageDimensions.DragBarHeight,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PicoTheme { LearningPanelDragBar(LearningStageDimensions.DragBarWidth, panel, dragRuntime) }
    }
}

@Composable
private fun LearningPanelDragBar(
    width: Dp,
    panel: LearningPanel,
    dragRuntime: StagePanelDragRuntime
) {
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val owner = remember(panel) { Any() }
    val widthPixels = with(density) { width.toPx() }
    val heightPixels = with(density) { LearningStageDimensions.DragBarHeight.toPx() }
    Box(
        modifier =
            Modifier.size(width, LearningStageDimensions.DragBarHeight)
                .pointerInput(context, dragRuntime, panel, widthPixels, heightPixels) {
                    try {
                        detectSpatialPointerEvent(context = context) { events ->
                            dragRuntime.onEvents(owner, events, panel, widthPixels, heightPixels)
                        }
                    } finally {
                        dragRuntime.cancel(owner)
                    }
                }
                .spatialHoverEffect { hover ->
                    animation(tween(durationMillis = 140)) {
                        scale(if (hover.isActive) LearningStageDimensions.DragBarHoverScale else 1f)
                        alpha(if (hover.isActive) 1f else 0.78f)
                    }
                }
                .onGloballyPositioned { coordinates ->
                    if (com.pico.spatial.sample.spatialvideo.BuildConfig.DEBUG) {
                        val bounds = coordinates.boundsInRoot()
                        Log.i(
                            "LearningHandleInput",
                            org.json
                                .JSONObject()
                                .put("pid", android.os.Process.myPid())
                                .put("panel", panel.name)
                                .put("view", System.identityHashCode(view))
                                .put("x", bounds.center.x)
                                .put("y", bounds.center.y)
                                .toString()
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(width, 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE7C27D))
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.72f),
                        RoundedCornerShape(9.dp),
                    )
        )
    }
}

@Composable
private fun LearningSubtitlePanel(segment: TranscriptSegment?) {
    Box(
        modifier =
            Modifier.size(
                    LearningStageDimensions.SubtitleWidth,
                    LearningStageDimensions.SubtitleHeight,
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        if (segment != null) {
            Column {
                Text(
                    text = TranscriptCueSelector.sourceLabel(segment),
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                )
                Text(
                    text = segment.text,
                    fontSize = 34.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PicoTheme.colorScheme.labelPrimaryLight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class StagePanelDragRuntime(dragState: LearningPanelDragState) {
    private val gesture = LearningDragGestureState(dragState)
    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var metersPerPixel = 0f
    private val handlePanels = mutableMapOf<Entity, LearningPanel>()
    private val handles = mutableMapOf<LearningPanel, Entity>()
    private var applyPose: ((LearningPanel, SpatialPanelPose) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null

    fun bind(
        content: SpatialViewContent,
        stageRoot: Entity,
        metersPerPixel: Float,
        applyPose: (LearningPanel, SpatialPanelPose) -> Unit,
        onFinished: () -> Unit
    ) {
        clear()
        this.content = content
        this.stageRoot = stageRoot
        this.metersPerPixel = metersPerPixel
        this.applyPose = applyPose
        this.onFinished = onFinished
    }

    fun register(handle: Entity, panel: LearningPanel) {
        handlePanels[handle] = panel
        handles[panel] = handle
    }

    fun isHandle(entity: Entity): Boolean = handlePanels.containsKey(entity)

    fun onEvents(
        owner: Any,
        events: List<SpatialPointerInfo>,
        uiPanel: LearningPanel? = null,
        widthPixels: Float = 0f,
        heightPixels: Float = 0f
    ): Boolean {
        var consumed = false
        for (event in events) {
            val pointer = event.pointerId.value
            if (event.isDownEvent()) {
                val panel = uiPanel ?: event.targetedEntity?.let(handlePanels::get) ?: continue
                val point =
                    toStagePoint(event.position3D, uiPanel, widthPixels, heightPixels) ?: continue
                consumed = gesture.begin(owner, pointer, panel, point) || consumed
            } else if (gesture.owns(owner, pointer)) {
                consumed = true
                if (event.pressed) {
                    val point =
                        toStagePoint(event.position3D, uiPanel, widthPixels, heightPixels)
                            ?: continue
                    gesture.move(owner, pointer, point)?.let { (panel, pose) ->
                        applyPose?.invoke(panel, pose)
                    }
                } else {
                    gesture.end(owner, pointer)?.let { (panel, pose) ->
                        applyPose?.invoke(panel, pose)
                        onFinished?.invoke()
                    }
                }
            }
        }
        return consumed
    }

    fun cancel(owner: Any) = gesture.cancel(owner)

    fun cancelAll() = gesture.reset()

    fun clear() {
        gesture.reset()
        handlePanels.clear()
        handles.clear()
        content = null
        stageRoot = null
        applyPose = null
        onFinished = null
    }

    private fun toStagePoint(
        point: Offset3D,
        uiPanel: LearningPanel?,
        widthPixels: Float,
        heightPixels: Float
    ): SpatialDragPoint? {
        val root = stageRoot ?: return null
        val stagePoint =
            if (uiPanel != null) {
                val handle = handles[uiPanel] ?: return null
                // Include the moving UI's current transform; its own movement is not hand motion.
                handle.convertPositionTo(
                    Vector3(
                        (point.x - widthPixels / 2f) * metersPerPixel,
                        -(point.y - heightPixels / 2f) * metersPerPixel,
                        point.z * metersPerPixel - DRAG_BAR_FORWARD_OFFSET_METERS,
                    ),
                    root
                )
            } else {
                val activeContent = content ?: return null
                val contentPoint =
                    activeContent.convertPosition(
                        Vector3(point.x, point.y, point.z),
                        ViewCoordinateSpace.Local,
                        activeContent.localSpatialCoordinateSpace
                    )
                root.convertPositionFrom(contentPoint, null)
            }
        return SpatialDragPoint(stagePoint.x, stagePoint.y, stagePoint.z)
    }
}

private fun createLearningPanelRoot(
    content: SpatialViewContent,
    stageRoot: Entity,
    surface: Entity?,
    pose: SpatialPanelPose,
    frame: SpatialPanelFrameGeometry,
): Entity? {
    if (surface == null) return null
    val panelRoot =
        Entity().apply {
            components.set(
                TransformComponent()
                    .setScaleVector(Vector3.ONE * LearningStageDimensions.PanelScale)
            )
            applyPose(pose)
        }
    content.addEntity(panelRoot)
    stageRoot.addChild(panelRoot)
    content.addEntity(surface)
    panelRoot.addChild(surface)
    // The body and visible handle share one centered attachment; only its footer needs
    // compensation.
    surface.components.get<TransformComponent>()?.setPosition(Vector3(0f, frame.surfaceY, 0f))
    return panelRoot
}

private fun attachDragBar(
    content: SpatialViewContent,
    entity: Entity?,
    parent: Entity?,
    panel: LearningPanel,
    panelHeight: Dp,
    barWidth: Dp,
    converter: PhysicalLengthConverter,
    runtime: StagePanelDragRuntime,
    surface: Entity? = null,
    handleScale: Float = 1f,
) {
    if (entity == null || parent == null) return

    val panelHeightMeters = converter.dpToLength(panelHeight, LengthUnit.Meters)
    val handleSize =
        content.convertSize(
            with(converter) {
                Vector3(
                    barWidth.toPx(),
                    LearningStageDimensions.DragBarHeight.toPx(),
                    LearningStageDimensions.DragBarGap.toPx()
                )
            },
            ViewCoordinateSpace.Local,
            content.localSpatialCoordinateSpace,
        )
    val barWidthMeters = handleSize.x
    val barHeightMeters = handleSize.y
    val gapMeters = handleSize.z
    val localPose =
        SpatialDragBarGeometry.localPose(
            panelHeightMeters = panelHeightMeters,
            gapMeters = gapMeters,
            barHeightMeters = barHeightMeters,
            forwardOffsetMeters = DRAG_BAR_FORWARD_OFFSET_METERS,
            handleScale = handleScale,
        )

    entity.components.set(InteractableComponent())
    entity.components.set(
        CollisionComponent(
            collisionShape =
                listOf(
                    ShapeResource.createBox(
                        Vector3(
                            barWidthMeters,
                            barHeightMeters,
                            DRAG_BAR_COLLIDER_DEPTH_METERS,
                        )
                    )
                ),
            physicsMaterial = PhysicsMaterialResource(),
        )
    )
    content.addEntity(entity)
    parent.addChild(entity)
    entity.components.get<TransformComponent>()?.apply {
        setPosition(Vector3(localPose.x, localPose.y, localPose.z))
        setEulerAngles(EulerAngles(0f, 0f, 0f))
        setScaleVector(Vector3.ONE * handleScale)
    }
    surface?.let {
        content.addEntity(it)
        entity.addChild(it)
        // Keep the UI in front of the fallback hitbox so gaze reaches its hover effect.
        it.components
            .get<TransformComponent>()
            ?.setPosition(Vector3(0f, 0f, -DRAG_BAR_FORWARD_OFFSET_METERS))
    }
    runtime.register(entity, panel)
}

private fun Entity.applyPose(pose: SpatialPanelPose) {
    components.get<TransformComponent>()?.apply {
        setPosition(Vector3(pose.x, pose.y, pose.z))
        setEulerAngles(EulerAngles(0f, pose.yawDegrees, 0f))
    }
}

private fun Entity.applyRootPose(pose: StageRootPose) {
    components.get<TransformComponent>()?.apply {
        setPosition(Vector3(pose.x, pose.y, pose.z))
        setEulerAngles(EulerAngles(0f, pose.yawDegrees, 0f))
    }
}

private const val OUTLINE_ATTACHMENT_ID = "learning-outline-attachment"
private const val TOOLBAR_ATTACHMENT_ID = "learning-toolbar-attachment"
private const val AI_COACH_ATTACHMENT_ID = "learning-ai-coach-attachment"
private const val SUBTITLE_ATTACHMENT_ID = "learning-subtitle-attachment"
private const val VIDEO_DRAG_BAR_ATTACHMENT_ID = "learning-video-drag-bar"
private const val DRAG_BAR_COLLIDER_DEPTH_METERS = 0.06f
private const val DRAG_BAR_FORWARD_OFFSET_METERS = -0.035f
private const val HMD_POSE_TIMEOUT_MS = 2_000L
private const val HMD_SAMPLE_COUNT = 8
private const val LAYOUT_STATUS_DURATION_MS = 2_400L
private const val LECTURE_HALL_TAG = "AcademicLectureHall"
