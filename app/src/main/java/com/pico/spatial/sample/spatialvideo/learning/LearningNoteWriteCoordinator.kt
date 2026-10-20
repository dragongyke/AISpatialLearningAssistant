package com.pico.spatial.sample.spatialvideo.learning

data class LearningNoteWriteToken(val videoId: String, val revision: Long)

class LearningNoteWriteCoordinator {
    private val revisions = mutableMapOf<String, Long>()
    private val locks = mutableMapOf<String, Any>()

    @Synchronized
    fun begin(videoId: String): LearningNoteWriteToken {
        val revision = (revisions[videoId] ?: 0L) + 1L
        revisions[videoId] = revision
        return LearningNoteWriteToken(videoId, revision)
    }

    fun <T> commitIfCurrent(token: LearningNoteWriteToken, block: () -> T): T? {
        val lock = synchronized(this) { locks.getOrPut(token.videoId) { Any() } }
        return synchronized(lock) { if (!isCurrent(token)) null else block() }
    }

    @Synchronized
    fun isCurrent(token: LearningNoteWriteToken): Boolean =
        revisions[token.videoId] == token.revision
}
