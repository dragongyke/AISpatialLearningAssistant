package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.PI
import kotlin.math.sin

internal object LearningCrystalOrbGeometry {
    const val shellRadiusMeters = 0.12f
    const val maxVisualRadiusMeters = 0.178f
    const val colliderRadiusMeters = 0.19f
}

internal object LearningCrystalOrbMotion {
    // Fractional counter-rotations must also complete whole turns at the wrap boundary.
    const val orbitRepeatDegrees = 9000f
    const val swirlRepeatDegrees = 7200f
}

internal data class LearningCrystalOrbTint(val red: Float, val green: Float, val blue: Float)

internal data class LearningCrystalOrbFrame(
    val tint: LearningCrystalOrbTint,
    val glow: Float,
    val coreScale: Float,
    val visualScale: Float,
    val shellOpacity: Float,
    val orbitSpeedDegrees: Float,
    val waitingGlow: Float,
    val gatheringScale: Float,
    val swirlSpeedDegrees: Float,
    val speakingGlow: Float,
    val speakingScale: Float,
)

internal fun learningCrystalOrbFrame(
    state: LearningVoiceControlState,
    hovered: Boolean,
    pressed: Boolean,
    timeSeconds: Double,
    audioLevel: Float? = null,
): LearningCrystalOrbFrame {
    val tint =
        when (state) {
            LearningVoiceControlState.Ready -> LearningCrystalOrbTint(0.45f, 0.82f, 1f)
            LearningVoiceControlState.Listening -> LearningCrystalOrbTint(0.20f, 1f, 0.72f)
            LearningVoiceControlState.Analyzing -> LearningCrystalOrbTint(0.69f, 0.48f, 1f)
            LearningVoiceControlState.Answering -> LearningCrystalOrbTint(0.35f, 0.59f, 1f)
            LearningVoiceControlState.Speaking -> LearningCrystalOrbTint(1f, 0.77f, 0.36f)
            LearningVoiceControlState.Unavailable -> LearningCrystalOrbTint(0.43f, 0.47f, 0.54f)
        }
    val period =
        when (state) {
            LearningVoiceControlState.Ready -> 4.0
            LearningVoiceControlState.Listening -> 2.4
            LearningVoiceControlState.Analyzing -> 2.6
            LearningVoiceControlState.Answering -> 2.2
            LearningVoiceControlState.Speaking -> 1.8
            LearningVoiceControlState.Unavailable -> 4.0
        }
    val time = timeSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val wave =
        if (state == LearningVoiceControlState.Unavailable) 0f
        else ((sin((time % period) / period * 2.0 * PI) + 1.0) * 0.5).toFloat()
    val audioReactive =
        state == LearningVoiceControlState.Listening || state == LearningVoiceControlState.Speaking
    val level =
        if (audioReactive) audioLevel?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f else 0f
    val waiting =
        state == LearningVoiceControlState.Analyzing || state == LearningVoiceControlState.Answering
    val speaking = state == LearningVoiceControlState.Speaking
    val baseGlow =
        when {
            state == LearningVoiceControlState.Unavailable -> 0.16f
            waiting -> 0.76f
            else -> 0.52f
        }
    return LearningCrystalOrbFrame(
        tint = tint,
        glow =
            (baseGlow + wave * 0.12f + level * 0.18f + if (hovered) 0.12f else 0f).coerceIn(0f, 1f),
        coreScale = if (waiting) 1.06f + wave * 0.1f else 0.96f + wave * 0.06f + level * 0.16f,
        visualScale =
            when {
                pressed -> 0.96f
                hovered -> 1.035f
                else -> 1f
            },
        shellOpacity =
            if (state == LearningVoiceControlState.Unavailable) 0.28f
            else if (hovered) 0.22f else 0.16f,
        orbitSpeedDegrees =
            when (state) {
                LearningVoiceControlState.Ready -> 6f
                LearningVoiceControlState.Listening -> 10f
                LearningVoiceControlState.Analyzing -> 28f
                LearningVoiceControlState.Answering -> 26f
                LearningVoiceControlState.Speaking -> 12f
                LearningVoiceControlState.Unavailable -> 0f
            },
        waitingGlow = if (waiting) 0.52f + wave * 0.22f else 0f,
        gatheringScale = if (waiting) 1.08f - wave * 0.28f else 1f,
        swirlSpeedDegrees = if (waiting) 82f else 0f,
        speakingGlow = if (speaking) 0.38f + wave * 0.18f + level * 0.2f else 0f,
        speakingScale = if (speaking) 0.84f + wave * 0.2f + level * 0.1f else 1f,
    )
}
