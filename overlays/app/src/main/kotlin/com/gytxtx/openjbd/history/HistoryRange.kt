package com.gytxtx.openjbd.history

enum class HistoryRange(val durationMillis: Long) {
    LAST_24H(24L * 60L * 60L * 1_000L),
    LAST_7D(7L * 24L * 60L * 60L * 1_000L);

    fun startMillis(nowMillis: Long): Long = nowMillis - durationMillis
}

enum class HistoryMetric {
    SOC_PERCENT,
    PACK_VOLTS,
    CELL_DELTA_MV
}

fun valueOf(metric: HistoryMetric, sample: HistorySample): Float? = when (metric) {
    HistoryMetric.SOC_PERCENT -> sample.socPercent.toFloat()
    HistoryMetric.PACK_VOLTS -> sample.packMillivolts / 1_000f
    HistoryMetric.CELL_DELTA_MV -> sample.cellDeltaMillivolts?.toFloat()
}
