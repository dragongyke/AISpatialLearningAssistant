package com.pico.spatial.sample.spatialvideo.learning

import kotlinx.coroutines.channels.ReceiveChannel

internal class SpeechAnswerQueue(private val speaker: SpeechAnswerSpeaker) {
    suspend fun play(
        sentences: ReceiveChannel<String>,
        isCurrent: () -> Boolean,
        onFirstSentence: () -> Unit,
        onPlaybackStarted: () -> Unit,
        onResult: (SpeechPlaybackResult) -> Unit,
    ) {
        try {
            speaker.speakStream(sentences, isCurrent, onFirstSentence, onPlaybackStarted, onResult)
        } finally {
            sentences.cancel()
        }
    }
}
