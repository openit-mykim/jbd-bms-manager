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
    const val ABSENT_VOLTAGE_THRESHOLD_VOLTS = 0.5f
    const val PRESENT_RECOVERY_THRESHOLD_VOLTS = 1.0f
    const val ABSENT_DEBOUNCE_SAMPLES = 3
    const val PRESENT_DEBOUNCE_SAMPLES = 3
}

class CellVoltageTracker(
    private val absentThresholdVolts: Float = CellVoltagePolicy.ABSENT_VOLTAGE_THRESHOLD_VOLTS,
    private val recoveryThresholdVolts: Float =
        CellVoltagePolicy.PRESENT_RECOVERY_THRESHOLD_VOLTS,
    private val absentDebounceSamples: Int = CellVoltagePolicy.ABSENT_DEBOUNCE_SAMPLES,
    private val presentDebounceSamples: Int = CellVoltagePolicy.PRESENT_DEBOUNCE_SAMPLES
) {
    private val absentStates = mutableListOf<Boolean>()
    private val belowThresholdCounts = mutableListOf<Int>()
    private val aboveRecoveryCounts = mutableListOf<Int>()

    init {
        require(absentThresholdVolts < recoveryThresholdVolts) {
            "Absent threshold must be below recovery threshold"
        }
        require(absentDebounceSamples > 0) { "Absent debounce must be positive" }
        require(presentDebounceSamples > 0) { "Present debounce must be positive" }
    }

    @Synchronized
    fun update(cells: List<Float>, configuredCellCount: Int?): CellVoltageAnalysis {
        resizeState(cells.size)
        val configuredLimit = configuredCellCount?.coerceAtLeast(0)
        val readings = cells.mapIndexed { index, voltage ->
            val exceedsConfiguredCount = configuredLimit != null && index >= configuredLimit
            val isAbsent = if (exceedsConfiguredCount) {
                belowThresholdCounts[index] = 0
                aboveRecoveryCounts[index] = 0
                true
            } else if (absentStates[index]) {
                belowThresholdCounts[index] = 0
                if (voltage > recoveryThresholdVolts) {
                    aboveRecoveryCounts[index] += 1
                } else {
                    aboveRecoveryCounts[index] = 0
                }
                aboveRecoveryCounts[index] < presentDebounceSamples
            } else {
                aboveRecoveryCounts[index] = 0
                if (voltage < absentThresholdVolts) {
                    belowThresholdCounts[index] += 1
                } else {
                    belowThresholdCounts[index] = 0
                }
                belowThresholdCounts[index] >= absentDebounceSamples
            }
            if (isAbsent != absentStates[index]) {
                belowThresholdCounts[index] = 0
                aboveRecoveryCounts[index] = 0
            }
            absentStates[index] = isAbsent
            CellVoltageReading(
                index = index,
                voltage = voltage,
                isAbsent = isAbsent
            )
        }
        return readings.toAnalysis()
    }

    @Synchronized
    fun reset() {
        absentStates.clear()
        belowThresholdCounts.clear()
        aboveRecoveryCounts.clear()
    }

    private fun resizeState(size: Int) {
        while (absentStates.size < size) {
            absentStates += false
            belowThresholdCounts += 0
            aboveRecoveryCounts += 0
        }
        while (absentStates.size > size) {
            absentStates.removeAt(absentStates.lastIndex)
            belowThresholdCounts.removeAt(belowThresholdCounts.lastIndex)
            aboveRecoveryCounts.removeAt(aboveRecoveryCounts.lastIndex)
        }
    }
}

private fun List<CellVoltageReading>.toAnalysis(): CellVoltageAnalysis {
    val readings = this
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
