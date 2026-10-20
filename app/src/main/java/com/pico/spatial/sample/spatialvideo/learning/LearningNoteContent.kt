package com.pico.spatial.sample.spatialvideo.learning

object LearningNoteContent {
    fun mergeLive(existing: String, generated: String): String =
        replaceSection(existing, "live", generated)

    fun mergeSummary(existing: String, generated: String): String =
        replaceSection(existing, "summary", generated)

    fun protectUserEdits(original: String, edited: String): String =
        listOf("live", "summary").fold(edited) { result, section ->
            val pattern = sectionPattern(section)
            val before = pattern.find(original)?.value
            val after = pattern.find(result)
            if (after != null && before != after.value) {
                result.replaceRange(after.range, after.groupValues[1].trim())
            } else result
        }

    fun mergeUserEdits(current: String, original: String, edited: String): String {
        var result = protectUserEdits(original, edited)
        for (section in listOf("live", "summary")) {
            val pattern = sectionPattern(section)
            val latest = pattern.find(current) ?: continue
            val before = pattern.find(original)?.value
            val userVersion = pattern.find(edited)?.value
            if (before != null && userVersion == before) {
                val match = pattern.find(result)
                if (match != null) result = result.replaceRange(match.range, latest.value)
            } else if (before == null && userVersion == null) {
                result = listOf(result.trimEnd(), latest.value).joinToString("\n\n")
            }
        }
        return result
    }

    fun isManagementMarker(line: String): Boolean =
        Regex("<!-- ai-learning:(live|summary):(start|end) -->").matches(line.trim())

    private fun replaceSection(existing: String, section: String, generated: String): String {
        val body =
            generated.lineSequence().filterNot(::isManagementMarker).joinToString("\n").trim()
        val block = "<!-- ai-learning:$section:start -->\n$body\n<!-- ai-learning:$section:end -->"
        val match = sectionPattern(section).find(existing)
        return if (match == null)
            listOf(existing.trimEnd(), block).filter(String::isNotBlank).joinToString("\n\n")
        else existing.replaceRange(match.range, block)
    }

    private fun sectionPattern(section: String): Regex =
        Regex(
            "<!-- ai-learning:$section:start -->(.*?)<!-- ai-learning:$section:end -->",
            RegexOption.DOT_MATCHES_ALL
        )
}
