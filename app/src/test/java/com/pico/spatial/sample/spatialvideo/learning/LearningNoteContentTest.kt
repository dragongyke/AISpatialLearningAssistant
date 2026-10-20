package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningNoteContentTest {
    @Test
    fun correctingGeneratedTextDoesNotReinsertTheOriginal() {
        val original = LearningNoteContent.mergeSummary("# Notes", "Wrong explanation")
        val edited = original.replace("Wrong explanation", "My correction")
        val saved = LearningNoteContent.mergeUserEdits(original, original, edited)
        assertTrue(saved.contains("My correction"))
        assertFalse(saved.contains("Wrong explanation"))
    }

    @Test
    fun deletingAGeneratedSectionDoesNotRestoreItOnSave() {
        val original = LearningNoteContent.mergeSummary("# Notes", "Remove this section")
        assertEquals("# Notes", LearningNoteContent.mergeUserEdits(original, original, "# Notes"))
    }

    @Test
    fun savingAnOlderPreviewPreservesNewlyArrivedSections() {
        val original = "# Notes"
        val current = LearningNoteContent.mergeSummary(original, "New summary")
        val saved = LearningNoteContent.mergeUserEdits(current, original, "# Notes\nMy addition")
        assertTrue(saved.contains("New summary"))
        assertTrue(saved.contains("My addition"))
    }

    @Test
    fun savingAnEditDoesNotRestoreAnOlderAutomaticallyGeneratedSummary() {
        val original = LearningNoteContent.mergeSummary("# Personal notes", "Old AI summary")
        val current = LearningNoteContent.mergeSummary(original, "New AI summary")
        val edited = original.replace("# Personal notes", "# Personal notes\nMy addition")
        val saved = LearningNoteContent.mergeUserEdits(current, original, edited)
        assertTrue(saved.contains("My addition"))
        assertTrue(saved.contains("New AI summary"))
        assertFalse(saved.contains("Old AI summary"))
    }

    @Test
    fun preservesLegacyAndPersonalNotesAcrossLiveUpdates() {
        val original = "# My edited note\n\nKeep this explanation exactly."
        val first = LearningNoteContent.mergeLive(original, "First draft")
        val next = LearningNoteContent.mergeLive(first, "Next draft")
        assertTrue(next.contains(original))
        assertFalse(next.contains("First draft"))
        assertTrue(next.contains("Next draft"))
    }

    @Test
    fun summaryAndLiveDraftHaveIndependentManagedSections() {
        val summary = LearningNoteContent.mergeSummary("", "Prior summary")
        val live = LearningNoteContent.mergeLive(summary, "New learning record")
        assertTrue(live.contains("Prior summary"))
        val updated = LearningNoteContent.mergeSummary(live, "Updated summary")
        assertTrue(updated.contains("New learning record"))
        assertFalse(updated.contains("Prior summary"))
        assertEquals(updated, LearningNoteContent.mergeSummary(updated, "Updated summary"))
    }

    @Test
    fun editedGeneratedTextIsProtectedFromFutureGeneration() {
        val original = LearningNoteContent.mergeSummary("", "Original explanation")
        val edited = original.replace("Original explanation", "My correction")
        val protected = LearningNoteContent.protectUserEdits(original, edited)
        val regenerated = LearningNoteContent.mergeSummary(protected, "New AI explanation")
        assertTrue(regenerated.contains("My correction"))
        assertTrue(regenerated.contains("New AI explanation"))
    }
}
