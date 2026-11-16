package com.pico.spatial.sample.spatialvideo.learning

// Expose the selected resource and its arguments to JVM tests without Android Resources.
internal val testStatusStrings = LearningStatusStrings { id, args ->
    (listOf(id) + args).joinToString(" ")
}
