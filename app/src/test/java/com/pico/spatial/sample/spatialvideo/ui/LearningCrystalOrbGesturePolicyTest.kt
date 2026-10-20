package com.pico.spatial.sample.spatialvideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCrystalOrbGesturePolicyTest {
    private val owner = Any()
    private val origin = SpatialDragPoint(0f, 0f, 0f)
    private val policy = LearningCrystalOrbGesturePolicy()

    @Test
    fun shortPinchWithSmallJitterEmitsExactlyOneTap() {
        assertTrue(policy.begin(owner, 7, origin, 1_000))
        assertNull(policy.move(owner, 7, SpatialDragPoint(0.003f, 0f, 0f), 1_050))
        assertEquals(LearningCrystalOrbRelease.Tap, policy.end(owner, 7, origin, 1_100))
        assertFalse(policy.owns(owner, 7))
        assertNull(policy.end(owner, 7, origin, 1_101))
    }

    @Test
    fun dragStaysADragAfterReturningToTheStart() {
        policy.begin(owner, 7, origin, 0)
        assertEquals(
            SpatialDragDelta(0.04f, 0.02f, -0.01f),
            policy.move(owner, 7, SpatialDragPoint(0.04f, 0.02f, -0.01f), 100),
        )
        assertEquals(SpatialDragDelta(0f, 0f, 0f), policy.move(owner, 7, origin, 120))
        assertEquals(
            LearningCrystalOrbRelease.Drag(SpatialDragDelta(0f, 0f, 0f)),
            policy.end(owner, 7, origin, 140),
        )
    }

    @Test
    fun releasePositionAlsoParticipatesInDragDetection() {
        policy.begin(owner, 7, origin, 0)
        assertEquals(
            LearningCrystalOrbRelease.Drag(SpatialDragDelta(0f, 0f, 0.025f)),
            policy.end(owner, 7, SpatialDragPoint(0f, 0f, 0.025f), 100),
        )
    }

    @Test
    fun thresholdUsesThreeDimensionalTravelFromStartNotAccumulatedJitter() {
        policy.begin(owner, 7, origin, 0)
        repeat(20) { index ->
            val x = if (index % 2 == 0) 0.004f else -0.004f
            assertNull(policy.move(owner, 7, SpatialDragPoint(x, 0f, 0f), index.toLong()))
        }
        assertEquals(LearningCrystalOrbRelease.Tap, policy.end(owner, 7, origin, 100))
        policy.begin(owner, 8, origin, 200)
        assertEquals(
            SpatialDragDelta(0.008f, 0.008f, 0.008f),
            policy.move(owner, 8, SpatialDragPoint(0.008f, 0.008f, 0.008f), 220),
        )
    }

    @Test
    fun thresholdIsInclusiveAndDragDeltaRemainsRelativeToInitialPoint() {
        val policy = LearningCrystalOrbGesturePolicy(dragThresholdMeters = 0.02f)
        policy.begin(owner, 7, SpatialDragPoint(1f, 0f, 0f), 0)
        assertEquals(
            SpatialDragDelta(0f, 0.02f, 0f),
            policy.move(owner, 7, SpatialDragPoint(1f, 0.02f, 0f), 20),
        )
        assertEquals(
            SpatialDragDelta(0f, 0.04f, 0f),
            policy.move(owner, 7, SpatialDragPoint(1f, 0.04f, 0f), 40),
        )
    }

    @Test
    fun longHoldNeverAsksButCanStillBecomeADrag() {
        policy.begin(owner, 7, origin, 0)
        assertEquals(LearningCrystalOrbRelease.Cancelled, policy.end(owner, 7, origin, 351))
        policy.begin(owner, 8, origin, 1_000)
        assertEquals(
            SpatialDragDelta(0.03f, 0f, 0f),
            policy.move(owner, 8, SpatialDragPoint(0.03f, 0f, 0f), 3_000),
        )
        assertTrue(
            policy.end(owner, 8, SpatialDragPoint(0.03f, 0f, 0f), 4_000)
                is LearningCrystalOrbRelease.Drag
        )
    }

    @Test
    fun tapDurationBoundaryIsInclusive() {
        policy.begin(owner, 7, origin, 0)
        assertEquals(LearningCrystalOrbRelease.Tap, policy.end(owner, 7, origin, 350))
    }

    @Test
    fun competingOwnerOrPointerCannotStealMoveReleaseOrCancel() {
        val otherOwner = Any()
        assertTrue(policy.begin(owner, 7, origin, 0))
        assertFalse(policy.begin(owner, 8, origin, 0))
        assertFalse(policy.begin(otherOwner, 7, origin, 0))
        assertNull(policy.move(otherOwner, 7, SpatialDragPoint(1f, 0f, 0f), 100))
        assertNull(policy.end(owner, 8, origin, 100))
        policy.cancel(otherOwner, 7)
        policy.cancel(owner, 8)
        assertTrue(policy.owns(owner, 7))
        assertEquals(LearningCrystalOrbRelease.Tap, policy.end(owner, 7, origin, 100))
    }

    @Test
    fun ownersAreComparedByIdentityNotEquality() {
        val first = listOf(1)
        val equalButDifferent = listOf(1)
        policy.begin(first, 7, origin, 0)
        assertFalse(policy.owns(equalButDifferent, 7))
        assertNull(policy.end(equalButDifferent, 7, origin, 100))
        assertTrue(policy.owns(first, 7))
    }

    @Test
    fun cancellationAndResetReleaseCaptureWithoutATap() {
        policy.begin(owner, 7, origin, 0)
        policy.cancel(owner, 7)
        assertNull(policy.end(owner, 7, origin, 100))
        assertTrue(policy.begin(owner, 8, origin, 200))
        policy.reset()
        assertFalse(policy.owns(owner, 8))
        assertTrue(policy.begin(owner, 9, origin, 300))
    }

    @Test
    fun nonFiniteCoordinatesNeverStartOrActivateAGesture() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (point in
                listOf(
                    SpatialDragPoint(value, 0f, 0f),
                    SpatialDragPoint(0f, value, 0f),
                    SpatialDragPoint(0f, 0f, value)
                )) {
                assertFalse(policy.begin(owner, 7, point, 0))
                assertTrue(policy.begin(owner, 7, origin, 0))
                assertNull(policy.move(owner, 7, point, 10))
                assertFalse(policy.owns(owner, 7))
                assertNull(policy.end(owner, 7, origin, 20))
                assertTrue(policy.begin(owner, 7, origin, 0))
                assertEquals(LearningCrystalOrbRelease.Cancelled, policy.end(owner, 7, point, 10))
            }
        }
    }

    @Test
    fun overflowingDeltaCancelsInsteadOfLeakingInfinityToPose() {
        policy.begin(owner, 7, SpatialDragPoint(-Float.MAX_VALUE, 0f, 0f), 0)
        assertNull(policy.move(owner, 7, SpatialDragPoint(Float.MAX_VALUE, 0f, 0f), 10))
        assertFalse(policy.owns(owner, 7))
    }

    @Test
    fun invalidOrReversedClockCancelsAndCannotBecomeATap() {
        assertFalse(policy.begin(owner, 7, origin, -1))
        policy.begin(owner, 7, origin, 100)
        assertNull(policy.move(owner, 7, origin, 99))
        assertFalse(policy.owns(owner, 7))
        policy.begin(owner, 7, origin, 100)
        policy.move(owner, 7, origin, 150)
        assertEquals(LearningCrystalOrbRelease.Cancelled, policy.end(owner, 7, origin, 149))
        policy.begin(owner, 7, origin, 0)
        assertEquals(
            LearningCrystalOrbRelease.Cancelled,
            policy.end(owner, 7, origin, Long.MAX_VALUE)
        )
    }

    @Test
    fun invalidConfigurationIsRejected() {
        for (threshold in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrowsIllegalArgument {
                LearningCrystalOrbGesturePolicy(dragThresholdMeters = threshold)
            }
        }
        assertThrowsIllegalArgument { LearningCrystalOrbGesturePolicy(maxTapDurationMs = -1) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected constructor validation.
        }
    }
}
