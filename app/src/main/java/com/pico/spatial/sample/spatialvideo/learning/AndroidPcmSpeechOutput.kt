package com.pico.spatial.sample.spatialvideo.learning

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal interface PcmSpeechOutput {
    suspend fun write(pcm: ByteArray)

    suspend fun finish()

    fun stop()

    fun close()
}

/** A single PCM track keeps sentence boundaries free of player teardown/startup gaps. */
internal class AndroidPcmSpeechOutput(private val onFirstAudio: () -> Unit) : PcmSpeechOutput {
    private val track =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(
                maxOf(
                    SAMPLE_RATE,
                    AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                )
            )
            .build()
    private var bytesWritten = 0L
    private var firstSignalFrame: Long? = null
    private var started = false
    private val notified = AtomicBoolean(false)
    @Volatile private var stopped = false

    init {
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) {
                    notifyAudible()
                }

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            },
            Handler(Looper.getMainLooper())
        )
        track.setBufferSizeInFrames(START_FRAMES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            track.setStartThresholdInFrames(START_FRAMES)
    }

    override suspend fun write(pcm: ByteArray) {
        require(pcm.size % 2 == 0) { "Invalid PCM16 sample boundary." }
        synchronized(this) {
            if (stopped) throw CancellationException("Audio output stopped.")
            if (firstSignalFrame == null)
                firstPcmSignalFrame(pcm)?.let { frame ->
                    firstSignalFrame = bytesWritten / 2 + frame + 1
                    track.notificationMarkerPosition = firstSignalFrame!!.toInt()
                }
        }
        var offset = 0
        while (offset < pcm.size) {
            currentCoroutineContext().ensureActive()
            if (stopped) throw CancellationException("Audio output stopped.")
            val count =
                track.write(
                    pcm,
                    offset,
                    minOf(8192, pcm.size - offset),
                    AudioTrack.WRITE_NON_BLOCKING
                )
            check(count >= 0) { "Audio output write failed." }
            offset += count
            bytesWritten += count
            // 160 ms absorbs normal packet jitter without adding a sentence-sized delay.
            if (bytesWritten >= SAMPLE_RATE * 2 * 160 / 1000) start()
            notifyAudible()
            if (count == 0) delay(5)
        }
    }

    override suspend fun finish() {
        if (bytesWritten == 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            track.setStartThresholdInFrames(1)
        } else {
            // Older Android starts only at its effective buffer threshold, including after
            // underruns.
            val remainingFrames =
                bytesWritten / 2 - (track.playbackHeadPosition.toLong() and 0xffffffffL)
            val padding = (track.bufferSizeInFrames - remainingFrames).coerceAtLeast(0).toInt()
            if (padding > 0) write(ByteArray(padding * 2))
        }
        start()
        withTimeout(bytesWritten * 1000 / (SAMPLE_RATE * 2) + 5_000) {
            while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < bytesWritten / 2) {
                if (stopped) throw CancellationException("Audio output stopped.")
                notifyAudible()
                delay(5)
            }
        }
        notifyAudible()
    }

    @Synchronized
    private fun start() {
        if (!started && !stopped) {
            track.play()
            started = true
        }
    }

    @Synchronized
    private fun notifyAudible() {
        if (stopped) return
        val signal = firstSignalFrame ?: return
        if (
            (track.playbackHeadPosition.toLong() and 0xffffffffL) >= signal &&
                notified.compareAndSet(false, true)
        ) {
            onFirstAudio()
        }
    }

    @Synchronized
    override fun stop() {
        if (stopped) return
        stopped = true
        runCatching {
            track.pause()
            track.flush()
        }
    }

    @Synchronized
    override fun close() {
        stop()
        track.setPlaybackPositionUpdateListener(null)
        track.release()
    }

    private companion object {
        const val SAMPLE_RATE = 24000
        const val START_FRAMES = SAMPLE_RATE * 160 / 1000
    }
}
