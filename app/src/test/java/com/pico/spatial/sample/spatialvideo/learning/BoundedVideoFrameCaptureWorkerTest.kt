package com.pico.spatial.sample.spatialvideo.learning

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedVideoFrameCaptureWorkerTest {
    private val request =
        VideoFrameCaptureRequest("course", "https://video.invalid/course.mp4", 1, 30_000)
    private val frame = CapturedVideoFrame(30_000, "data:image/jpeg;base64,/9j/2Q==")

    @Test
    fun deadlineReturnsWhileNativeCaptureIsBlockedAndLateFrameIsReusable() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { _, publish ->
                attempts.incrementAndGet()
                release.await()
                publish(frame)
            }
        try {
            val result = withTimeout(1_000) { worker.capture(request, timeoutMs = 50) }
            assertTrue(result.isEmpty())
            assertEquals(1, attempts.get())
            release.countDown()
            assertEquals(listOf(frame), withTimeout(1_000) { worker.capture(request, 800) })
            assertEquals(listOf(frame), worker.capture(request, 1))
            assertEquals(1, attempts.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun validFrameIsDeliveredBeforeNativeCleanupCompletes() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val releaseCleanup = CountDownLatch(1)
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { _, publish ->
                publish(frame)
                releaseCleanup.await()
            }
        try {
            assertEquals(listOf(frame), withTimeout(1_000) { worker.capture(request, 800) })
            assertFalse(worker.prefetch(request.copy(timestampMs = 90_000)))
        } finally {
            releaseCleanup.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun busyWorkerDoesNotQueueMoreDecodesOrLeakAnotherCoursesFrame() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { _, publish ->
                attempts.incrementAndGet()
                release.await()
                publish(frame)
            }
        try {
            assertTrue(worker.prefetch(request))
            repeat(20) {
                assertFalse(worker.prefetch(request.copy(videoId = "other", timestampMs = 90_000)))
            }
            assertTrue(worker.capture(request.copy(videoId = "other"), 50).isEmpty())
            release.countDown()
            assertEquals(listOf(frame), withTimeout(1_000) { worker.capture(request, 800) })
            assertEquals(1, attempts.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellingWaiterDoesNotDiscardTheSharedCapture() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { _, publish ->
                started.countDown()
                release.await()
                publish(frame)
            }
        try {
            val waiter = async { worker.capture(request, 5_000) }
            withTimeout(1_000) { while (started.count != 0L) delay(5) }
            withTimeout(500) { waiter.cancelAndJoin() }
            release.countDown()
            assertEquals(listOf(frame), withTimeout(1_000) { worker.capture(request, 800) })
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun unavailableOrOversizedFramesStayEmpty() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { _, publish ->
                publish(frame.copy(jpegDataUrl = "data:image/jpeg;base64," + "A".repeat(300_000)))
            }
        try {
            assertTrue(worker.capture(request, 800).isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun prefetchTimingIncludesNativeWorkEvenWhenNoCallerIsWaiting() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val observations = CopyOnWriteArrayList<VideoFrameCaptureObservation>()
        val worker =
            BoundedVideoFrameCaptureWorker(
                executor,
                onObservation = {
                    observations += it
                    if (it.phase == "finished") finished.countDown()
                }
            ) { _, publish ->
                release.await()
                publish(frame)
            }
        try {
            assertTrue(worker.prefetch(request))
            delay(40)
            release.countDown()
            withTimeout(1_000) { while (finished.count != 0L) delay(5) }
            assertEquals(listOf("frame_ready", "finished"), observations.map { it.phase })
            assertTrue(observations.first().elapsedMs >= 30)
            assertTrue(observations.last().elapsedMs >= observations.first().elapsedMs)
            assertTrue(observations.all { it.hasFrame })
            assertFalse(observations.toString().contains("video.invalid"))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cachedFrameKeepsItsTimestampAndCannotCrossSessionBoundary() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val worker =
            BoundedVideoFrameCaptureWorker(executor) { request, publish ->
                if (request.sessionId == 1L) publish(frame)
            }
        try {
            assertEquals(listOf(frame), worker.capture(request, 800))
            assertEquals(listOf(frame), worker.capture(request.copy(timestampMs = 30_500), 1))
            assertTrue(worker.capture(request.copy(sessionId = 2L), 800).isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }
}
