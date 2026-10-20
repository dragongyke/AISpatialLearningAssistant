package com.pico.spatial.sample.spatialvideo.learning

internal fun LearningUiState.canSubmitDraftedQuestion(): Boolean =
    draftedQuestion.isNotBlank() && !isListening && !isAnswering && !isAnalyzingVideo

object VisualQuestionPolicy {
    private val visualTerms =
        listOf(
            "画面",
            "屏幕",
            "黑板",
            "白板",
            "幻灯片",
            "这张图",
            "图里",
            "图上",
            "老师写",
            "老师指",
            "on screen",
            "blackboard",
            "whiteboard",
            "slide",
            "diagram",
            "image",
            "picture",
            "chart",
            "graph",
            "written here",
            "written there",
            "what is written",
            "pointing at",
        )

    fun requiresVisualContext(question: String): Boolean {
        val normalized = question.trim().lowercase()
        return normalized.isNotBlank() && visualTerms.any(normalized::contains)
    }
}

class StreamingSentenceChunker(private val lowLatency: Boolean = false) {
    private val pending = StringBuilder()
    private var emitted = false

    fun append(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        pending.append(delta)
        val completed = mutableListOf<String>()
        while (true) {
            val boundary = sentenceBoundary()
            if (boundary < 0 && pending.length <= MAX_CHUNK_CHARACTERS) break
            val end =
                if (boundary in 0 until MAX_CHUNK_CHARACTERS) boundary + 1
                else {
                    val whitespace = pending.substring(0, MAX_CHUNK_CHARACTERS).lastIndexOf(' ')
                    if (whitespace >= MAX_CHUNK_CHARACTERS / 2) whitespace + 1
                    else MAX_CHUNK_CHARACTERS
                }
            pending.substring(0, end).trim().takeIf(String::isNotBlank)?.let {
                completed.add(it)
                emitted = true
            }
            pending.delete(0, end)
        }
        return completed
    }

    private fun sentenceBoundary(): Int {
        for (index in pending.indices) {
            if (pending[index] in SENTENCE_BOUNDARIES) return index
            if (
                lowLatency &&
                    !emitted &&
                    index >= 12 &&
                    pending[index] in setOf('，', ';', '；', ':', '：')
            )
                return index
            if (
                lowLatency &&
                    !emitted &&
                    index >= 12 &&
                    pending[index] == ',' &&
                    index < pending.lastIndex &&
                    pending[index + 1].isWhitespace()
            )
                return index
            if (pending[index] != '.' || index == pending.lastIndex) continue
            if (!pending[index + 1].isWhitespace()) continue
            val word =
                pending.substring(0, index).takeLastWhile { it.isLetter() || it == '.' }.lowercase()
            if (word in ABBREVIATIONS || word.length == 1) continue
            return index
        }
        return -1
    }

    fun finish(): String? =
        pending.toString().trim().takeIf(String::isNotBlank).also { pending.clear() }

    private companion object {
        val SENTENCE_BOUNDARIES = setOf('。', '！', '？', '!', '?', '\n')
        val ABBREVIATIONS = setOf("dr", "mr", "mrs", "ms", "prof", "e.g", "i.e", "vs", "etc")
        const val MAX_CHUNK_CHARACTERS = 240
    }
}

object LiveLearningNoteBuilder {
    fun build(state: LearningUiState, progressMs: Long): String = buildString {
        appendLine("# ${state.video.title}")
        appendLine()
        appendLine("> 实时学习笔记 · 已学习到 ${formatTime(progressMs)}")
        appendLine()
        appendLine("## 课程知识点")
        val coveredSegments =
            state.transcriptSegments
                .filter { it.startMs <= progressMs }
                .distinctBy { it.startMs to it.text }
                .takeLast(MAX_TRANSCRIPT_SEGMENTS)
        if (coveredSegments.isEmpty()) {
            appendLine("- 视频刚刚开始，笔记会随着学习进度自动补充。")
        } else {
            coveredSegments.forEach { segment ->
                appendLine("- **${formatTime(segment.startMs)}** ${segment.text.trim()}")
            }
        }
        appendLine()
        appendLine("## 用户问题与 AI 解释")
        if (state.questionAnswers.isEmpty()) {
            appendLine("- 暂无提问。")
        } else {
            state.questionAnswers.forEach { qa ->
                appendLine("### **${formatTime(qa.timestampMs)}** · ${qa.questionText}")
                appendLine()
                appendLine(qa.answerText.trim())
                appendLine()
            }
        }
        appendLine("## 待复习")
        val activeChapter =
            state.chapters.lastOrNull { it.startMs <= progressMs } ?: state.chapters.firstOrNull()
        appendLine(
            activeChapter?.let { "- ${it.title}：${it.summary}" } ?: "- 学习结束后，AI 会在这里整理需要复习的知识点。"
        )
    }

    private const val MAX_TRANSCRIPT_SEGMENTS = 24
}

object LiveNoteUpdatePolicy {
    fun shouldUpdate(
        videoId: String,
        progressMs: Long,
        questionCount: Int,
        lastVideoId: String?,
        lastProgressMs: Long,
        lastQuestionCount: Int,
    ): Boolean =
        videoId != lastVideoId ||
            questionCount != lastQuestionCount ||
            progressMs - lastProgressMs >= UPDATE_INTERVAL_MS

    private const val UPDATE_INTERVAL_MS = 15_000L
}
