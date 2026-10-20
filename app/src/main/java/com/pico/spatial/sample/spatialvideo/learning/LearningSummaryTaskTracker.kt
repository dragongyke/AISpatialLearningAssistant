package com.pico.spatial.sample.spatialvideo.learning

data class LearningSummaryTask(val videoId: String, val id: Long)

data class LearningSummaryRevision(
    val sessionId: Long,
    val progressMs: Long,
    val questionIds: List<String>,
)

class LearningSummaryTaskTracker {
    private var nextId = 0L
    private val tasks = mutableMapOf<String, LearningSummaryTask>()
    private val revisions = mutableMapOf<String, LearningSummaryRevision>()

    fun needsUpdate(videoId: String, revision: LearningSummaryRevision): Boolean =
        revisions[videoId] != revision

    fun begin(videoId: String, revision: LearningSummaryRevision? = null): LearningSummaryTask =
        LearningSummaryTask(videoId, ++nextId).also {
            tasks[videoId] = it
            if (revision == null) revisions.remove(videoId) else revisions[videoId] = revision
        }

    fun finish(task: LearningSummaryTask, succeeded: Boolean = true) {
        if (tasks[task.videoId] != task) return
        tasks.remove(task.videoId)
        if (!succeeded) revisions.remove(task.videoId)
    }

    fun isRunning(videoId: String): Boolean = tasks.containsKey(videoId)

    fun cancel(videoId: String) {
        tasks.remove(videoId)
        revisions.remove(videoId)
    }
}
