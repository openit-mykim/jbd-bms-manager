package com.gytxtx.openjbd.history

data class HistorySample(
    val timestampMillis: Long,
    val socPercent: Int,
    val packMillivolts: Int,
    val currentMilliamps: Int,
    val cellDeltaMillivolts: Int?
)
