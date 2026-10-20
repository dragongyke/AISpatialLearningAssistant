package com.pico.spatial.sample.spatialvideo.learning

sealed interface LearningNoteBlock {
    val timestampMs: Long?

    data class Heading(
        val level: Int,
        val text: String,
        override val timestampMs: Long? = null,
    ) : LearningNoteBlock

    data class Bullet(val text: String, override val timestampMs: Long? = null) : LearningNoteBlock

    data class Numbered(
        val number: Int,
        val text: String,
        override val timestampMs: Long? = null,
    ) : LearningNoteBlock

    data class Quote(val text: String, override val timestampMs: Long? = null) : LearningNoteBlock

    data class Paragraph(val text: String, override val timestampMs: Long? = null) :
        LearningNoteBlock
}

val LearningNoteBlock.searchableText: String
    get() =
        when (this) {
            is LearningNoteBlock.Heading -> text
            is LearningNoteBlock.Bullet -> text
            is LearningNoteBlock.Numbered -> text
            is LearningNoteBlock.Quote -> text
            is LearningNoteBlock.Paragraph -> text
        }

object LearningNoteMarkdownParser {
    private val numberedPattern = Regex("""^(\d+)\.\s+(.+)$""")

    fun parse(markdown: String): List<LearningNoteBlock> =
        markdown
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(LearningNoteContent::isManagementMarker)
            .map { line ->
                val timestampMs = LearningNoteTimestampParser.parse(line)
                when {
                    line.startsWith("### ") ->
                        LearningNoteBlock.Heading(
                            3,
                            line.removePrefix("### ").cleanInline(),
                            timestampMs,
                        )
                    line.startsWith("## ") ->
                        LearningNoteBlock.Heading(
                            2,
                            line.removePrefix("## ").cleanInline(),
                            timestampMs,
                        )
                    line.startsWith("# ") ->
                        LearningNoteBlock.Heading(
                            1,
                            line.removePrefix("# ").cleanInline(),
                            timestampMs,
                        )
                    line.startsWith("> ") ->
                        LearningNoteBlock.Quote(
                            line.removePrefix("> ").cleanInline(),
                            timestampMs,
                        )
                    line.startsWith("- ") ->
                        LearningNoteBlock.Bullet(
                            line.removePrefix("- ").cleanInline(),
                            timestampMs,
                        )
                    numberedPattern.matches(line) -> {
                        val match = numberedPattern.matchEntire(line)!!
                        LearningNoteBlock.Numbered(
                            number = match.groupValues[1].toInt(),
                            text = match.groupValues[2].cleanInline(),
                            timestampMs = timestampMs,
                        )
                    }
                    else -> LearningNoteBlock.Paragraph(line.cleanInline(), timestampMs)
                }
            }
            .toList()
}

object LearningNoteTimestampParser {
    private val timestampPattern = Regex("""(?<!\d)(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?!\d)""")

    fun parse(text: String): Long? {
        val match = timestampPattern.find(text) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (minutes > 59L && hours > 0L || seconds > 59L) return null
        return (hours * 3_600L + minutes * 60L + seconds) * 1_000L
    }
}

private fun String.cleanInline(): String = replace("**", "").replace("__", "")
