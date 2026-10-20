package com.pico.spatial.sample.spatialvideo.ui

import java.net.URI

object VideoPlaybackPolicy {
    fun isRemote(sourceUri: String): Boolean =
        runCatching { URI(sourceUri).scheme?.lowercase() in setOf("http", "https") }
            .getOrDefault(false)

    fun sampleIntervalMs(isRemote: Boolean): Long =
        if (isRemote) REMOTE_SAMPLE_INTERVAL_MS else LOCAL_SAMPLE_INTERVAL_MS

    fun shouldPrefetchEntireVideo(isRemote: Boolean): Boolean = !isRemote

    private const val REMOTE_SAMPLE_INTERVAL_MS = 5_000L
    private const val LOCAL_SAMPLE_INTERVAL_MS = 750L
}
