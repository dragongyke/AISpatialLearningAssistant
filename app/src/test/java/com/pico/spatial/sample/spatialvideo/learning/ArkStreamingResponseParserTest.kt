package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArkStreamingResponseParserTest {
    @Test
    fun preservesStandaloneSpacesAndParagraphBoundariesExactly() {
        assertEquals(
            " ",
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\" \"}}]}"
            )
        )
        assertEquals(
            "\n\n",
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\\n\\n\"}}]}"
            )
        )
        assertEquals(
            "\t",
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\\t\"}}]}"
            )
        )
        assertNull(
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}"
            )
        )
    }

    @Test
    fun extractsIncrementalContentFromArkSseEvents() {
        assertEquals(
            "你好",
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"
            ),
        )
        assertEquals(
            "，我们一步一步来看。",
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"content\":\"，我们一步一步来看。\"}}]}"
            ),
        )
    }

    @Test
    fun ignoresDoneAndNonContentEvents() {
        assertNull(ArkStreamingResponseParser.extractDelta("data: [DONE]"))
        assertNull(
            ArkStreamingResponseParser.extractDelta(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"
            )
        )
    }
}
