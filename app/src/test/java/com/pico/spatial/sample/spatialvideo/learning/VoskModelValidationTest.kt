package com.pico.spatial.sample.spatialvideo.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskModelValidationTest {
    @Test
    fun acceptsACompleteSmallModelLayout() {
        assertTrue(
            VoskModelLayout.isComplete(
                setOf("am/final.mdl", "conf/mfcc.conf", "conf/model.conf", "graph/HCLr.fst")
            )
        )
    }

    @Test
    fun rejectsPartialOrEmptyModelLayouts() {
        assertFalse(VoskModelLayout.isComplete(emptySet()))
        assertFalse(VoskModelLayout.isComplete(setOf("am/final.mdl")))
        assertFalse(VoskModelLayout.isComplete(setOf("conf/model.conf", "graph/HCLr.fst")))
    }
}
