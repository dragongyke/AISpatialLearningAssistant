package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningNoteMarkdownParserTest {
    @Test
    fun parsesHeadingsListsQuotesAndParagraphsForInAppReading() {
        val blocks =
            LearningNoteMarkdownParser.parse(
                """
                # Course note

                > Updated while learning

                ## Key idea
                - First point
                1. Review this

                A short explanation.
                """
                    .trimIndent()
            )

        assertEquals(
            listOf(
                LearningNoteBlock.Heading(1, "Course note"),
                LearningNoteBlock.Quote("Updated while learning"),
                LearningNoteBlock.Heading(2, "Key idea"),
                LearningNoteBlock.Bullet("First point"),
                LearningNoteBlock.Numbered(1, "Review this"),
                LearningNoteBlock.Paragraph("A short explanation."),
            ),
            blocks,
        )
    }

    @Test
    fun extractsClickableTimestampsFromHeadingsAndBullets() {
        val blocks =
            LearningNoteMarkdownParser.parse(
                """
                ## 01:02 Key concept
                - **1:02:03** Revisit the complete example
                """
                    .trimIndent()
            )

        assertEquals(62_000L, blocks[0].timestampMs)
        assertEquals(3_723_000L, blocks[1].timestampMs)
    }

    @Test
    fun exposesPlainTextForNoteSearch() {
        val block = LearningNoteBlock.Bullet("Gradient descent example", 2_000L)

        assertEquals("Gradient descent example", block.searchableText)
    }
}
