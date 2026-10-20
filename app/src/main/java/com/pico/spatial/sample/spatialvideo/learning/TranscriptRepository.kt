package com.pico.spatial.sample.spatialvideo.learning

import android.content.Context
import android.text.Html
import kotlin.math.roundToLong

class TranscriptRepository(private val context: Context) {
    fun load(video: LearningVideo): List<TranscriptSegment> =
        loadTimedTranscript(video).ifEmpty { loadEstimatedTranscript(video) }

    private fun loadTimedTranscript(video: LearningVideo): List<TranscriptSegment> =
        runCatching {
                val assetPath = "transcripts/${video.id}.vtt"
                val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                WebVttTranscriptParser.parse(
                    videoId = video.id,
                    sourceUri = "asset:///$assetPath",
                    content = content,
                )
            }
            .getOrDefault(emptyList())

    private fun loadEstimatedTranscript(video: LearningVideo): List<TranscriptSegment> =
        runCatching {
                val html =
                    context.assets.open("transcripts/${video.id}.html").bufferedReader().use {
                        it.readText()
                    }
                val plainText = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
                TranscriptSegmentation.segmentAnchored(
                    videoId = video.id,
                    durationMs = video.durationMs,
                    paragraphs = plainText.toTranscriptParagraphs(),
                    sourceUri = transcriptSourceUri(video.id),
                    anchors = transcriptTimelineAnchors(video.id),
                )
            }
            .getOrDefault(emptyList())

    private fun transcriptSourceUri(videoId: String): String =
        when (videoId) {
            "stanford-cs106a-lecture01" ->
                "https://see.stanford.edu/materials/icspmcs106a/transcripts/ProgrammingMethodology-Lecture01.html"
            "stanford-cs229-lecture01" ->
                "https://see.stanford.edu/materials/aimlcs229/transcripts/MachineLearning-Lecture01.html"
            "stanford-ee364a-lecture01" ->
                "https://see.stanford.edu/materials/lsocoee364a/transcripts/ConvexOptimizationI-Lecture01.html"
            else -> ""
        }
}

data class TranscriptTimelineAnchor(
    val timestampMs: Long,
    val marker: String,
)

object TranscriptSegmentation {
    fun segment(
        videoId: String,
        durationMs: Long,
        paragraphs: List<String>,
        sourceUri: String,
    ): List<TranscriptSegment> {
        val cleaned = paragraphs.map(String::trim).filter(String::isNotBlank)
        if (cleaned.isEmpty()) return emptyList()
        val safeDuration = durationMs.coerceAtLeast(1L)
        val totalWeight = cleaned.sumOf { it.length.coerceAtLeast(1) }.toLong()
        var consumedWeight = 0L
        return cleaned.mapIndexed { index, text ->
            val startMs =
                (safeDuration * consumedWeight.toDouble() / totalWeight)
                    .roundToLong()
                    .coerceAtMost(safeDuration - 1L)
            consumedWeight += text.length.coerceAtLeast(1)
            val endMs =
                if (index == cleaned.lastIndex) safeDuration
                else (safeDuration * consumedWeight.toDouble() / totalWeight).roundToLong()
            TranscriptSegment(
                videoId = videoId,
                startMs = startMs,
                endMs = endMs.coerceAtLeast(startMs + 1L).coerceAtMost(safeDuration),
                text = text,
                summary = text.take(SUMMARY_CHARACTER_LIMIT),
                visualSummary = "",
                sourceUri = sourceUri,
                timingIsEstimated = true,
            )
        }
    }

    fun segmentAnchored(
        videoId: String,
        durationMs: Long,
        paragraphs: List<String>,
        sourceUri: String,
        anchors: List<TranscriptTimelineAnchor>,
    ): List<TranscriptSegment> {
        val cleaned = paragraphs.map(String::trim).filter(String::isNotBlank)
        if (cleaned.isEmpty()) return emptyList()
        val safeDuration = durationMs.coerceAtLeast(1L)
        val resolved = resolveAnchors(cleaned, safeDuration, anchors).toMutableList()
        if (resolved.firstOrNull()?.paragraphIndex != 0) {
            if (resolved.firstOrNull()?.timestampMs == 0L) {
                resolved[0] = resolved.first().copy(paragraphIndex = 0)
            } else {
                resolved.add(0, ResolvedAnchor(0L, 0))
            }
        }
        resolved.add(ResolvedAnchor(safeDuration, cleaned.size))

        return resolved.zipWithNext().flatMap { (start, end) ->
            if (end.paragraphIndex <= start.paragraphIndex) {
                emptyList()
            } else {
                buildReadableSegments(
                    videoId = videoId,
                    sourceUri = sourceUri,
                    startMs = start.timestampMs,
                    endMs = end.timestampMs,
                    paragraphs = cleaned.subList(start.paragraphIndex, end.paragraphIndex),
                )
            }
        }
    }

    private fun resolveAnchors(
        paragraphs: List<String>,
        durationMs: Long,
        anchors: List<TranscriptTimelineAnchor>,
    ): List<ResolvedAnchor> {
        var paragraphStart = 0
        var lastTimestamp = -1L
        return buildList {
            anchors
                .sortedBy { it.timestampMs }
                .forEach { anchor ->
                    val timestamp = anchor.timestampMs.coerceIn(0L, durationMs - 1L)
                    if (timestamp <= lastTimestamp) return@forEach
                    val relativeIndex =
                        paragraphs.drop(paragraphStart).indexOfFirst {
                            it.contains(anchor.marker, ignoreCase = true)
                        }
                    if (relativeIndex >= 0) {
                        val paragraphIndex = paragraphStart + relativeIndex
                        add(ResolvedAnchor(timestamp, paragraphIndex))
                        paragraphStart = paragraphIndex + 1
                        lastTimestamp = timestamp
                    }
                }
        }
    }

    private fun buildReadableSegments(
        videoId: String,
        sourceUri: String,
        startMs: Long,
        endMs: Long,
        paragraphs: List<String>,
    ): List<TranscriptSegment> {
        val cues = paragraphs.flatMap(::splitReadableCues)
        if (cues.isEmpty()) return emptyList()
        val safeEnd = endMs.coerceAtLeast(startMs + 1L)
        val duration = safeEnd - startMs
        val totalWeight = cues.sumOf { it.length.coerceAtLeast(1) }.toLong()
        var consumedWeight = 0L
        return cues.mapIndexed { index, text ->
            val cueStart =
                startMs + (duration * consumedWeight.toDouble() / totalWeight).roundToLong()
            consumedWeight += text.length.coerceAtLeast(1)
            val cueEnd =
                if (index == cues.lastIndex) safeEnd
                else {
                    startMs + (duration * consumedWeight.toDouble() / totalWeight).roundToLong()
                }
            TranscriptSegment(
                videoId = videoId,
                startMs = cueStart.coerceAtMost(safeEnd - 1L),
                endMs = cueEnd.coerceAtLeast(cueStart + 1L).coerceAtMost(safeEnd),
                text = text,
                summary = text.take(SUMMARY_CHARACTER_LIMIT),
                visualSummary = "",
                sourceUri = sourceUri,
                timingIsEstimated = true,
            )
        }
    }

    private fun splitReadableCues(text: String): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val cues = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val pieces =
                if (word.length <= CUE_CHARACTER_LIMIT) listOf(word)
                else word.chunked(CUE_CHARACTER_LIMIT)
            pieces.forEach { piece ->
                val candidate = if (current.isEmpty()) piece else "$current $piece"
                if (candidate.length <= CUE_CHARACTER_LIMIT) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) cues += current
                    current = piece
                }
            }
        }
        if (current.isNotEmpty()) cues += current
        return cues
    }

    private data class ResolvedAnchor(
        val timestampMs: Long,
        val paragraphIndex: Int,
    )

    private const val SUMMARY_CHARACTER_LIMIT = 280
    private const val CUE_CHARACTER_LIMIT = 96
}

private fun transcriptTimelineAnchors(videoId: String): List<TranscriptTimelineAnchor> =
    when (videoId) {
        "stanford-cs106a-lecture01" ->
            listOf(
                TranscriptTimelineAnchor(0L, "welcome to CS106A"),
                TranscriptTimelineAnchor(257_000L, "There is also a head TA"),
                TranscriptTimelineAnchor(
                    348_000L,
                    "why is this class called Programming Methodology"
                ),
                TranscriptTimelineAnchor(633_000L, "another class called CS106X"),
                TranscriptTimelineAnchor(
                    919_000L,
                    "a few other things you should know, some mechanics"
                ),
                TranscriptTimelineAnchor(1_246_000L, "let's talk a little bit about assignments"),
                TranscriptTimelineAnchor(1_689_000L, "everyone gets two free extensions"),
                TranscriptTimelineAnchor(1_853_000L, "There's two exams in this class"),
                TranscriptTimelineAnchor(1_920_000L, "this is how your grade breaks down"),
                TranscriptTimelineAnchor(2_072_000L, "Plagiarism and the honor code"),
                TranscriptTimelineAnchor(2_661_000L, "Karel the Robot is basically"),
            )
        "stanford-cs229-lecture01" ->
            listOf(
                TranscriptTimelineAnchor(0L, "Welcome to CS229"),
                TranscriptTimelineAnchor(310_000L, "large impact on many applications"),
                TranscriptTimelineAnchor(634_000L, "few words about logistics"),
                TranscriptTimelineAnchor(1_957_000L, "what machine learning is"),
                TranscriptTimelineAnchor(2_177_000L, "first of which is supervised learning"),
                TranscriptTimelineAnchor(2_718_000L, "second of the four major topics"),
                TranscriptTimelineAnchor(3_013_000L, "that's unsupervised learning"),
                TranscriptTimelineAnchor(3_743_000L, "last of the four major topics"),
            )
        "stanford-ee364a-lecture01" ->
            listOf(
                TranscriptTimelineAnchor(0L, "Welcome to 364a"),
                TranscriptTimelineAnchor(900_000L, "everything is an optimization problem"),
                TranscriptTimelineAnchor(1_980_000L, "illumination on a patch"),
                TranscriptTimelineAnchor(3_360_000L, "course goals and topics"),
            )
        else -> emptyList()
    }

private fun String.toTranscriptParagraphs(): List<String> =
    lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { line ->
            line.length >= MIN_PARAGRAPH_LENGTH &&
                !line.startsWith("Duration:", ignoreCase = true) &&
                !line.startsWith("[End of Audio]", ignoreCase = true)
        }
        .toList()

private const val MIN_PARAGRAPH_LENGTH = 40
