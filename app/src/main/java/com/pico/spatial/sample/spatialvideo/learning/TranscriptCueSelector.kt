package com.pico.spatial.sample.spatialvideo.learning

object TranscriptCueSelector {
    fun current(
        segments: List<TranscriptSegment>,
        playbackTimeMs: Long,
        presentationDelayMs: Long = 0L,
    ): TranscriptSegment? =
        (playbackTimeMs - presentationDelayMs).coerceAtLeast(0L).let { subtitleTimeMs ->
            segments.firstOrNull { segment ->
                subtitleTimeMs >= segment.startMs && subtitleTimeMs < segment.endMs
            }
        }

    fun sourceLabel(segment: TranscriptSegment): String =
        if (segment.timingIsEstimated) "Estimated transcript" else "Transcript"
}
