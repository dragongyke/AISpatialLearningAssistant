package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebVttTranscriptParserTest {
    @Test
    fun parsesExactCueTimesAndMultilineText() {
        val segments =
            WebVttTranscriptParser.parse(
                videoId = "course-1",
                sourceUri = "asset:///transcripts/course-1.vtt",
                content =
                    """
                    WEBVTT

                    00:00:01.000 --> 00:00:03.500
                    Welcome to the course.

                    cue-2
                    00:00:04.250 --> 00:00:07.000 align:middle
                    This cue spans
                    two lines.
                    """
                        .trimIndent(),
            )

        assertEquals(2, segments.size)
        assertEquals(1_000L, segments[0].startMs)
        assertEquals(3_500L, segments[0].endMs)
        assertEquals("Welcome to the course.", segments[0].text)
        assertEquals(4_250L, segments[1].startMs)
        assertEquals(7_000L, segments[1].endMs)
        assertEquals("This cue spans two lines.", segments[1].text)
        assertFalse(segments.any { it.timingIsEstimated })
    }

    @Test
    fun skipsMalformedAndEmptyCues() {
        val segments =
            WebVttTranscriptParser.parse(
                videoId = "course-1",
                sourceUri = "asset:///transcripts/course-1.vtt",
                content =
                    """
                    WEBVTT

                    not a timestamp
                    ignored text

                    00:00:08.000 --> 00:00:09.000

                    00:00:10.000 --> 00:00:12.000
                    Valid cue.
                    """
                        .trimIndent(),
            )

        assertEquals(1, segments.size)
        assertEquals("Valid cue.", segments.single().text)
    }
}
