/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.sample.spatialvideo.learning

object SampleLearningContent {
    val video =
        LearningVideo(
            id = "sample-ai-course",
            title = "AI Product Strategy: From Demo To Workflow",
            sourceUri = "https://media.w3.org/2010/05/sintel/trailer.mp4",
            durationMs = 52_000L,
            transcriptStatus = ProcessingStatus.Ready,
            summaryStatus = ProcessingStatus.Ready,
        )

    val chapters =
        listOf(
            LearningChapter(
                id = "chapter-context",
                title = "Context and learning goal",
                startMs = 0L,
                endMs = 14_000L,
                summary =
                    "The opening section frames the video as a knowledge session and defines why timestamped context matters.",
            ),
            LearningChapter(
                id = "chapter-workflow",
                title = "Question loop",
                startMs = 14_000L,
                endMs = 30_000L,
                summary =
                    "The learner pauses at a confusing point, asks a question, and receives an explanation grounded in nearby transcript and visual context.",
            ),
            LearningChapter(
                id = "chapter-review",
                title = "Review and summary",
                startMs = 30_000L,
                endMs = 52_000L,
                summary =
                    "The session ends with a learning record that combines video summary, user questions, AI answers, and suggested review clips.",
            ),
        )

    val transcriptSegments =
        listOf(
            TranscriptSegment(
                videoId = video.id,
                startMs = 0L,
                endMs = 8_000L,
                text =
                    "A strong learning assistant should not behave like a separate chatbot. It should stay attached to the video timestamp and explain the content the learner is seeing.",
                summary = "The AI assistant must stay grounded in the current video moment.",
                visualSummary =
                    "Opening slide introduces the idea of a timestamp-aware learning workspace.",
            ),
            TranscriptSegment(
                videoId = video.id,
                startMs = 8_000L,
                endMs = 16_000L,
                text =
                    "When the learner asks a question, playback pauses first. The system then collects nearby transcript, key frames, OCR text, and previous questions before answering.",
                summary = "The ask flow starts by pausing playback and retrieving local context.",
                visualSummary =
                    "A process diagram shows pause, retrieve context, answer, and resume.",
            ),
            TranscriptSegment(
                videoId = video.id,
                startMs = 16_000L,
                endMs = 25_000L,
                text =
                    "The answer should be short first, then detailed. It should cite the timestamp when possible and say when the video context is not enough.",
                summary =
                    "Good answers are concise, grounded, timestamped, and honest about uncertainty.",
                visualSummary = "A checklist slide lists answer quality rules.",
            ),
            TranscriptSegment(
                videoId = video.id,
                startMs = 25_000L,
                endMs = 36_000L,
                text =
                    "In a spatial layout, the video remains central while outline, notes, AI chat, and timeline markers stay visible around it.",
                summary =
                    "Spatial layout helps learners keep video, outline, chat, and notes visible together.",
                visualSummary =
                    "A spatial workspace mockup shows center video, left outline, right AI panel, and bottom timeline.",
            ),
            TranscriptSegment(
                videoId = video.id,
                startMs = 36_000L,
                endMs = 52_000L,
                text =
                    "After the learning session, the assistant generates a structured document with chapter summary, key concepts, questions asked, answers, review points, and optional quiz items.",
                summary = "The session output is a structured learning summary document.",
                visualSummary =
                    "A summary document preview highlights questions, timestamps, and review items.",
            ),
        )

    val suggestedQuestions =
        listOf(
            "Explain the current concept in simpler terms.",
            "Why does the assistant need to pause before answering?",
            "Summarize the previous section and list the key takeaway.",
            "What should I review again from this part?",
            "How is this different from a normal chatbot next to a video?",
        )
}
