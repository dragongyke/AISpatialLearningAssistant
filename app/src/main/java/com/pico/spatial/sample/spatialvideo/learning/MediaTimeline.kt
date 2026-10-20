package com.pico.spatial.sample.spatialvideo.learning

data class MediaTimelineSnapshot(
    val positionMs: Long,
    val durationMs: Long,
) {
    fun subtitlePositionMs(offsetMs: Long): Long =
        (positionMs - offsetMs).coerceIn(0L, durationMs.coerceAtLeast(0L))

    fun nearbyFrameTimestamps(radiusMs: Long = FRAME_RADIUS_MS): List<Long> {
        val end = durationMs.coerceAtLeast(0L)
        val current = positionMs.coerceIn(0L, end)
        return listOf(current - radiusMs, current, current + radiusMs)
            .map { it.coerceIn(0L, end) }
            .distinct()
    }

    private companion object {
        const val FRAME_RADIUS_MS = 2_000L
    }
}
