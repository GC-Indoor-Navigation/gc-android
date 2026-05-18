package com.gc.collector.network

import com.gc.collector.model.CalibrationUploadOutcome

object InternalCalibrationUploadOutcomeMapper {
    fun toOutcome(result: InternalCalibrationUploadResult): CalibrationUploadOutcome {
        return when (result) {
            InternalCalibrationUploadResult.Uploaded -> CalibrationUploadOutcome.Uploaded
            is InternalCalibrationUploadResult.Failed -> CalibrationUploadOutcome.Failed(result.message)
        }
    }
}
