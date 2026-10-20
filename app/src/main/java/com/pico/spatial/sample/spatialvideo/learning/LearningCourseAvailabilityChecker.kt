package com.pico.spatial.sample.spatialvideo.learning

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object LearningCourseAvailabilityPolicy {
    fun fromHttpCode(code: Int): CourseAvailability =
        if (code in 200..399) CourseAvailability.Available else CourseAvailability.Unavailable
}

class LearningCourseAvailabilityChecker(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
) {
    suspend fun check(sourceUri: String): CourseAvailability {
        val request =
            Request.Builder()
                .url(sourceUri)
                .header("Range", "bytes=0-0")
                .header("Accept-Encoding", "identity")
                .get()
                .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(CourseAvailability.Unavailable)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (continuation.isActive) {
                                continuation.resume(
                                    LearningCourseAvailabilityPolicy.fromHttpCode(response.code)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
