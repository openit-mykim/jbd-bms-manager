package com.gytxtx.openjbd.cells

data class CellVoltageReading(
    val index: Int,
    val voltage: Float,
    val isAbsent: Boolean
)

data class ConnectedCellStats(
    val minimum: Float,
    val maximum: Float,
    val average: Float,
    val delta: Float
)

data class CellVoltageAnalysis(
    val readings: List<CellVoltageReading>,
    val stats: ConnectedCellStats?
) {
    val connectedCellIndices: Set<Int> = readings
        .asSequence()
        .filterNot { it.isAbsent }
        .map { it.index }
        .toSet()
}

object CellVoltagePolicy {
    fun analyze(cells: List<Float>, configuredCellCount: Int?): CellVoltageAnalysis {
        val configuredLimit = configuredCellCount?.coerceAtLeast(0)
        val readings = cells.mapIndexed { index, voltage ->
            CellVoltageReading(
                index = index,
                voltage = voltage,
                isAbsent = configuredLimit != null && index >= configuredLimit
            )
        }
        val connectedVoltages = readings
            .asSequence()
            .filterNot { it.isAbsent }
            .map { it.voltage }
            .toList()
        val minimum = connectedVoltages.minOrNull()
        val maximum = connectedVoltages.maxOrNull()
        val stats = if (minimum == null || maximum == null) {
            null
        } else {
            ConnectedCellStats(
                minimum = minimum,
                maximum = maximum,
                average = connectedVoltages.average().toFloat(),
                delta = maximum - minimum
            )
        }

        return CellVoltageAnalysis(readings = readings, stats = stats)
    }
}
