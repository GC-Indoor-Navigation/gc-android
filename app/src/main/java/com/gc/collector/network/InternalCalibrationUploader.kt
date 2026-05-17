package com.gc.collector.network

import com.gc.collector.camera.CapturedFrame
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val internalCalibrationPath = "/capture/internal-calibration"
private val jpegMediaType = "image/jpeg".toMediaType()

class InternalCalibrationUploader(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun upload(
        baseUrl: String,
        frame: CapturedFrame,
    ): InternalCalibrationUploadResult {
        val endpoint = parseHttpBaseUrl(baseUrl).getOrElse { error ->
            return InternalCalibrationUploadResult.Failed(error.message ?: "Invalid calibration HTTP URL")
        }

        return runCatching {
            client.newCall(buildInternalCalibrationRequest(endpoint, frame)).execute().use { response ->
                if (response.isSuccessful) {
                    InternalCalibrationUploadResult.Uploaded
                } else {
                    InternalCalibrationUploadResult.Failed("HTTP ${response.code}: ${response.message}")
                }
            }
        }.getOrElse { error ->
            InternalCalibrationUploadResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }
}

sealed interface InternalCalibrationUploadResult {
    data object Uploaded : InternalCalibrationUploadResult
    data class Failed(val message: String) : InternalCalibrationUploadResult
}

internal fun buildInternalCalibrationRequest(
    baseUrl: HttpBaseUrl,
    frame: CapturedFrame,
): Request {
    val metadata = frame.metadata
    val filename = "${metadata.deviceTimestampMs}_${metadata.deviceId}_${metadata.cameraId}_${metadata.frameSequence}.jpg"
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            name = "file",
            filename = filename,
            body = frame.jpegBytes.toRequestBody(jpegMediaType),
        )
        .addFormDataPart("device_id", metadata.deviceId)
        .addFormDataPart("camera_id", metadata.cameraId)
        .addFormDataPart("frame_sequence", metadata.frameSequence.toString())
        .addFormDataPart("device_timestamp_ms", metadata.deviceTimestampMs.toString())
        .build()

    return Request.Builder()
        .url(baseUrl.resolve(internalCalibrationPath))
        .post(body)
        .build()
}
