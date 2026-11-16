package com.pico.spatial.sample.spatialvideo.ui

import com.pico.spatial.sample.spatialvideo.learning.LearningAiConfigurationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStageEntryGateTest {
    private val missing = LearningAiConfigurationStatus(false, false)
    private val complete = LearningAiConfigurationStatus(true, true)

    @Test
    fun missingConfigurationDefersCourseSelectionAndStageUntilContinue() = runBlocking {
        val calls = mutableListOf<String>()
        val gate =
            LearningStageEntryGate(missing) {
                calls += "stage"
                true
            }
        gate.request {
            calls += "course"
            true
        }
        assertTrue(gate.showNotice.value)
        assertTrue(calls.isEmpty())
        gate.continueEntry()
        assertFalse(gate.showNotice.value)
        assertEquals(listOf("course", "stage"), calls)
        gate.continueEntry()
        assertEquals(2, calls.size)
    }

    @Test
    fun completeConfigurationOpensWithoutNotice() = runBlocking {
        val calls = mutableListOf<String>()
        val gate =
            LearningStageEntryGate(complete) {
                calls += "stage"
                true
            }
        gate.request {
            calls += "course"
            true
        }
        assertFalse(gate.showNotice.value)
        assertEquals(listOf("course", "stage"), calls)
    }

    @Test
    fun dismissDoesNotStartSessionAndNextRequestStillWarns() = runBlocking {
        var entries = 0
        val gate =
            LearningStageEntryGate(missing) {
                entries++
                true
            }
        gate.request { error("Cancelled session must not be selected") }
        gate.dismiss()
        gate.continueEntry()
        assertFalse(gate.showNotice.value)
        assertEquals(0, entries)
        gate.request { true }
        assertTrue(gate.showNotice.value)
        gate.continueEntry()
        assertEquals(1, entries)
    }

    @Test
    fun repeatedClicksDoNotReplaceThePendingCourseOrTimestamp() = runBlocking {
        var selected = ""
        val gate = LearningStageEntryGate(missing) { true }
        gate.request {
            selected = "course-one@60000"
            true
        }
        gate.request {
            selected = "course-two@0"
            true
        }
        gate.continueEntry()
        assertEquals("course-one@60000", selected)
    }

    @Test
    fun unavailableNoteTargetDoesNotOpenStage() = runBlocking {
        val gate = LearningStageEntryGate(missing) { error("No valid target") }
        gate.request { false }
        gate.continueEntry()
        assertFalse(gate.showNotice.value)
    }

    @Test
    fun rejectedStageCanBeRequestedAgain() = runBlocking {
        var attempts = 0
        val gate =
            LearningStageEntryGate(missing) {
                attempts++
                false
            }
        repeat(2) {
            gate.request { true }
            assertTrue(gate.showNotice.value)
            gate.continueEntry()
        }
        assertEquals(2, attempts)
    }

    @Test
    fun repeatedRequestsWhileOpeningCannotStartAnotherSession() = runBlocking {
        val opened = CompletableDeferred<Boolean>()
        var preparations = 0
        val gate = LearningStageEntryGate(complete) { opened.await() }
        val first =
            launch(start = CoroutineStart.UNDISPATCHED) {
                gate.request {
                    preparations++
                    true
                }
            }
        gate.request {
            preparations++
            true
        }
        assertEquals(1, preparations)
        opened.complete(true)
        first.join()
    }

    @Test
    fun failedPreparationDoesNotLeaveGateBusy() = runBlocking {
        var entries = 0
        val gate =
            LearningStageEntryGate(complete) {
                entries++
                true
            }
        runCatching { gate.request { error("Preparation failed") } }
        gate.request { true }
        assertEquals(1, entries)
    }
}
