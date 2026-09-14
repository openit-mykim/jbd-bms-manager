package com.gytxtx.openjbd.balance

enum class CellHighlight {
    HIGHEST,
    LOWEST,
    NORMAL
}

data class BalanceCellDiagnostic(
    val cellNumber: Int,
    val voltage: Float,
    val isBalancing: Boolean,
    val highlight: CellHighlight,
    val band: CellSafetyBand,
    val progress: Int
)

object BalanceCellDiagnostics {
    fun analyze(
        cells: List<Float>,
        balanceStates: BooleanArray,
        thresholds: CellThresholds
    ): List<BalanceCellDiagnostic> {
        if (cells.isEmpty()) return emptyList()

        val minimum = cells.minOrNull() ?: return emptyList()
        val maximum = cells.maxOrNull() ?: return emptyList()
        val span = maxOf(MINIMUM_SPAN_VOLTS, maximum - minimum)

        return cells.mapIndexed { index, voltage ->
            val highlight = when (voltage) {
                maximum -> CellHighlight.HIGHEST
                minimum -> CellHighlight.LOWEST
                else -> CellHighlight.NORMAL
            }
            val progress = (PROGRESS_MAX * ((voltage - minimum) / span)).toInt()
                .coerceIn(PROGRESS_MIN, PROGRESS_MAX)
            BalanceCellDiagnostic(
                cellNumber = index + 1,
                voltage = voltage,
                isBalancing = balanceStates.getOrElse(index) { false },
                highlight = highlight,
                band = bandOf(voltsToMillivolts(voltage), thresholds),
                progress = progress
            )
        }
    }

    private const val MINIMUM_SPAN_VOLTS = 0.001f
    private const val PROGRESS_MIN = 80
    private const val PROGRESS_MAX = 1000
}
