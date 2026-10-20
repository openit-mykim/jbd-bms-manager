package com.gytxtx.openjbd.balance

import com.gytxtx.openjbd.cells.CellVoltageAnalysis

enum class CellHighlight {
    HIGHEST,
    LOWEST,
    NORMAL
}

data class BalanceCellDiagnostic(
    val cellNumber: Int,
    val voltage: Float,
    val isAbsent: Boolean,
    val isBalancing: Boolean,
    val highlight: CellHighlight,
    val band: CellSafetyBand?,
    val progress: Int
)

object BalanceCellDiagnostics {
    fun analyze(
        analysis: CellVoltageAnalysis,
        balanceStates: BooleanArray,
        thresholds: CellThresholds
    ): List<BalanceCellDiagnostic> {
        if (analysis.readings.isEmpty()) return emptyList()

        val minimum = analysis.stats?.minimum
        val maximum = analysis.stats?.maximum
        val span = if (minimum != null && maximum != null) {
            maxOf(MINIMUM_SPAN_VOLTS, maximum - minimum)
        } else {
            MINIMUM_SPAN_VOLTS
        }

        return analysis.readings.map { reading ->
            val highlight = when {
                reading.isAbsent -> CellHighlight.NORMAL
                reading.voltage == maximum -> CellHighlight.HIGHEST
                reading.voltage == minimum -> CellHighlight.LOWEST
                else -> CellHighlight.NORMAL
            }
            val progress = if (reading.isAbsent || minimum == null) {
                0
            } else {
                (PROGRESS_MAX * ((reading.voltage - minimum) / span)).toInt()
                    .coerceIn(PROGRESS_MIN, PROGRESS_MAX)
            }
            BalanceCellDiagnostic(
                cellNumber = reading.index + 1,
                voltage = reading.voltage,
                isAbsent = reading.isAbsent,
                isBalancing = !reading.isAbsent &&
                    balanceStates.getOrElse(reading.index) { false },
                highlight = highlight,
                band = if (reading.isAbsent) {
                    null
                } else {
                    bandOf(voltsToMillivolts(reading.voltage), thresholds)
                },
                progress = progress
            )
        }
    }

    private const val MINIMUM_SPAN_VOLTS = 0.001f
    private const val PROGRESS_MIN = 80
    private const val PROGRESS_MAX = 1000
}
