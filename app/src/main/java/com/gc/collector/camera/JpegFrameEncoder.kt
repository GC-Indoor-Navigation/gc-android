package com.gc.collector.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object JpegFrameEncoder {
    fun encode(
        imageProxy: ImageProxy,
        quality: Int = 80,
    ): ByteArray {
        require(imageProxy.format == ImageFormat.YUV_420_888) {
            "Unsupported image format: ${imageProxy.format}"
        }

        val nv21 = imageProxy.toNv21()
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null,
        )
        val output = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            quality,
            output,
        )
        return output.toByteArray()
    }
}

private fun ImageProxy.toNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val width = width
    val height = height
    val ySize = width * height
    val uvSize = width * height / 4
    val nv21 = ByteArray(ySize + uvSize * 2)

    copyPlane(
        buffer = yPlane.buffer,
        width = width,
        height = height,
        rowStride = yPlane.rowStride,
        pixelStride = yPlane.pixelStride,
        output = nv21,
        outputOffset = 0,
        outputPixelStride = 1,
    )

    copyPlane(
        buffer = vPlane.buffer,
        width = width / 2,
        height = height / 2,
        rowStride = vPlane.rowStride,
        pixelStride = vPlane.pixelStride,
        output = nv21,
        outputOffset = ySize,
        outputPixelStride = 2,
    )

    copyPlane(
        buffer = uPlane.buffer,
        width = width / 2,
        height = height / 2,
        rowStride = uPlane.rowStride,
        pixelStride = uPlane.pixelStride,
        output = nv21,
        outputOffset = ySize + 1,
        outputPixelStride = 2,
    )

    return nv21
}

private fun copyPlane(
    buffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    output: ByteArray,
    outputOffset: Int,
    outputPixelStride: Int,
) {
    val row = ByteArray(rowStride)
    var outputIndex = outputOffset

    buffer.rewind()
    for (rowIndex in 0 until height) {
        val bytesToRead = if (rowIndex == height - 1) {
            minOf(rowStride, buffer.remaining())
        } else {
            rowStride
        }
        buffer.get(row, 0, bytesToRead)

        var inputIndex = 0
        for (columnIndex in 0 until width) {
            output[outputIndex] = row[inputIndex]
            outputIndex += outputPixelStride
            inputIndex += pixelStride
        }
    }
}
