package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraControlStatusTest {
    @Test
    fun convertsSupportStateForMetadata() {
        assertEquals("supported", true.toMetadataState())
        assertEquals("unsupported", false.toMetadataState())
        assertEquals("unknown", null.toMetadataState())
    }

    @Test
    fun convertsAppliedStateForMetadata() {
        assertEquals("applied", true.toAppliedState())
        assertEquals("not_applied", false.toAppliedState())
        assertEquals("unknown", null.toAppliedState())
    }
}
