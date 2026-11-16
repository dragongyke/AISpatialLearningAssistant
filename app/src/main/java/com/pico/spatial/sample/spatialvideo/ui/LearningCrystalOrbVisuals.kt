package com.pico.spatial.sample.spatialvideo.ui

import kotlin.math.PI
import kotlin.math.sin

internal object LearningCrystalOrbGeometry {
    const val shellRadiusMeters = 0.12f
    const val maxVisualRadiusMeters = shellRadiusMeters
    const val colliderRadiusMeters = shellRadiusMeters + 0.012f
    const val innerGlassScale = 0.78f
    const val innerGlassOpacity = 0.65f
}

internal object LearningCrystalOrbMotion {
    // Fractional counter-rotations must also complete whole turns at the wrap boundary.
    const val swirlRepeatDegrees = 1440f
}

internal data class LearningCrystalOrbTint(val red: Float, val green: Float, val blue: Float)

internal enum class LearningCrystalOrbStream(
    val tint: LearningCrystalOrbTint,
    val speedRatio: Float
) {
    Cyan(LearningCrystalOrbTint(0.035f, 0.78f, 1f), 1f),
    Magenta(LearningCrystalOrbTint(1f, 0.075f, 0.48f), -0.75f),
    Gold(LearningCrystalOrbTint(1f, 0.57f, 0.045f), 0.5f)
}

internal data class LearningCrystalOrbFrame(
    val tint: LearningCrystalOrbTint,
    val glow: Float,
    val coreScale: Float,
    val visualScale: Float,
    val shellOpacity: Float,
    val gatheringScale: Float,
    val swirlSpeedDegrees: Float,
    val speakingScale: Float,
) {
    val sweepScale
        get() = gatheringScale * speakingScale

    val innerScale
        get() =
            (LearningCrystalOrbGeometry.innerGlassScale * coreScale).coerceAtMost(
                0.97f / visualScale
            )

    val threadLight
        get() = 0.70f + glow * 0.80f

    val veilOpacity
        get() = 0.50f + glow * 0.49f

    val shellTint
        get() =
            LearningCrystalOrbTint(
                0.12f + tint.red * 0.10f,
                0.20f + tint.green * 0.10f,
                0.38f + tint.blue * 0.12f
            )

    val innerTint
        get() =
            LearningCrystalOrbTint(
                0.006f + tint.red * 0.013f,
                0.005f + tint.green * 0.009f,
                0.021f + tint.blue * 0.027f
            )
}

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
        swirlSpeedDegrees =
            when (state) {
                LearningVoiceControlState.Ready -> 9f
                LearningVoiceControlState.Listening -> 16f
                LearningVoiceControlState.Analyzing -> 74f
                LearningVoiceControlState.Answering -> 66f
                LearningVoiceControlState.Speaking -> 19f
                LearningVoiceControlState.Unavailable -> 0f
            },
        gatheringScale = if (waiting) 0.96f - wave * 0.20f else 1f,
        speakingScale = if (speaking) 0.80f + wave * 0.135f + level * 0.06f else 1f,
    )
}
