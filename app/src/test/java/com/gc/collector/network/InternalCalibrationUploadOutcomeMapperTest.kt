package com.gc.collector.network

import com.gc.collector.model.CalibrationUploadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalCalibrationUploadOutcomeMapperTest {
    @Test
    fun uploadedMapsToUploadedOutcome() {
        val outcome = InternalCalibrationUploadOutcomeMapper.toOutcome(
            InternalCalibrationUploadResult.Uploaded,
        )

        assertEquals(CalibrationUploadOutcome.Uploaded, outcome)
    }

    @Test
    fun failedMapsToFailedOutcomeWithMessage() {
        val outcome = InternalCalibrationUploadOutcomeMapper.toOutcome(
            InternalCalibrationUploadResult.Failed("HTTP 500"),
        )

        assertTrue(outcome is CalibrationUploadOutcome.Failed)
        assertEquals("HTTP 500", (outcome as CalibrationUploadOutcome.Failed).message)
    }
}
