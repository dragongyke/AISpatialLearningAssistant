package com.pico.spatial.sample.spatialvideo.learning

object WebVttTranscriptParser {
    fun parse(videoId: String, sourceUri: String, content: String): List<TranscriptSegment> =
        content.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n{2,}")).mapNotNull { block
            ->
            parseCue(videoId, sourceUri, block)
        }

    private fun parseCue(
        videoId: String,
        sourceUri: String,
        block: String,
    ): TranscriptSegment? {
        val lines = block.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) return null
        val timingMatch = TIMING_PATTERN.matchEntire(lines[timingIndex]) ?: return null
        val text =
            lines
                .drop(timingIndex + 1)
                .joinToString(" ")
                .replace(TAG_PATTERN, "")
                .trim()
                .takeIf(String::isNotBlank) ?: return null
        val startMs = timingMatch.groupValues[1].toTimestampMs() ?: return null
        val endMs = timingMatch.groupValues[2].toTimestampMs() ?: return null
        if (endMs <= startMs) return null
        return TranscriptSegment(
            videoId = videoId,
            startMs = startMs,
            endMs = endMs,
            text = text,
            summary = text.take(SUMMARY_CHARACTER_LIMIT),
            visualSummary = "",
            sourceUri = sourceUri,
            timingIsEstimated = false,
        )
    }

    private fun String.toTimestampMs(): Long? {
        val parts = replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val secondsParts = parts.last().split('.')
        if (secondsParts.size != 2) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val seconds = secondsParts[0].toLongOrNull() ?: return null
        val milliseconds = secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
    }

    private val TIMING_PATTERN =
        Regex(
            "(\\d{2,}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})\\s+-->\\s+(\\d{2,}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})(?:\\s+.*)?"
        )
    private val TAG_PATTERN = Regex("<[^>]+>")
    private const val SUMMARY_CHARACTER_LIMIT = 280
}
