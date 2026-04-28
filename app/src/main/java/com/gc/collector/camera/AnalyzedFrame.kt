package com.gc.collector.camera

data class AnalyzedFrame(
    val width: Int,
    val height: Int,
    val sensorTimestampNs: Long,
)
