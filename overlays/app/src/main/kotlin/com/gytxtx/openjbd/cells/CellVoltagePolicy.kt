package com.gytxtx.openjbd.cells

import kotlin.math.abs

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
    val stats: ConnectedCellStats?,
    val effectiveCellCount: Int? = null,
    val wiringShortCellNumber: Int? = null,
    val zeroVoltageCellNumber: Int? = null
) {
    val connectedCellIndices: Set<Int> = readings
        .asSequence()
        .filterNot { it.isAbsent }
        .map { it.index }
        .toSet()

    val hasWiringShort: Boolean = wiringShortCellNumber != null
}

object CellVoltagePolicy {
    const val ABSENT_VOLTAGE_THRESHOLD_VOLTS = 0.5f
    const val PRESENT_RECOVERY_THRESHOLD_VOLTS = 1.0f
    const val ZERO_VOLTAGE_CHANNEL_TOLERANCE_VOLTS = 0.050f
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
        val zeroVoltageChannel = detectZeroVoltageChannel(cells, configuredLimit)
        val wiringShort = if (zeroVoltageChannel == null) {
            detectWiringShort(readings, configuredLimit)
        } else {
            null
        }
        val unusedStart = zeroVoltageChannel?.startIndex ?: wiringShort?.ghostStartIndex
        val collapsedReadings = if (unusedStart == null) {
            readings
        } else {
            readings.mapIndexed { index, reading ->
                if (index >= unusedStart) {
                    reading.copy(isAbsent = true)
                } else {
                    reading
                }
            }
        }
        return collapsedReadings.toAnalysis(
            effectiveCellCount = zeroVoltageChannel?.effectiveCellCount
                ?: wiringShort?.effectiveCellCount
                ?: configuredLimit
                ?: cells.size,
            wiringShortCellNumber = wiringShort?.cellNumber,
            zeroVoltageCellNumber = zeroVoltageChannel?.cellNumber
        )
    }

    @Synchronized
    fun reset() {
        absentStates.clear()
        belowThresholdCounts.clear()
        aboveRecoveryCounts.clear()
    }

    private fun detectZeroVoltageChannel(
        cells: List<Float>,
        configuredLimit: Int?
    ): ZeroVoltageChannel? {
        for (index in cells.indices) {
            if (configuredLimit != null && index >= configuredLimit) break
            if (abs(cells[index]) <= CellVoltagePolicy.ZERO_VOLTAGE_CHANNEL_TOLERANCE_VOLTS) {
                return ZeroVoltageChannel(
                    startIndex = index,
                    effectiveCellCount = index,
                    cellNumber = index + 1
                )
            }
        }
        return null
    }

    private fun detectWiringShort(
        readings: List<CellVoltageReading>,
        configuredLimit: Int?
    ): WiringShortDetection? {
        for (index in 0 until readings.lastIndex) {
            if (configuredLimit != null && index >= configuredLimit) break
            if (!readings[index].isAbsent) continue
            val next = readings[index + 1]
            if (next.isAbsent || next.voltage <= recoveryThresholdVolts) continue
            if (configuredLimit != null && index + 1 >= configuredLimit) continue
            return WiringShortDetection(
                ghostStartIndex = index,
                effectiveCellCount = index,
                cellNumber = index + 1
            )
        }
        return null
    }

    private data class WiringShortDetection(
        val ghostStartIndex: Int,
        val effectiveCellCount: Int,
        val cellNumber: Int
    )

    private data class ZeroVoltageChannel(
        val startIndex: Int,
        val effectiveCellCount: Int,
        val cellNumber: Int
    )

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

private fun List<CellVoltageReading>.toAnalysis(
    effectiveCellCount: Int?,
    wiringShortCellNumber: Int?,
    zeroVoltageCellNumber: Int?
): CellVoltageAnalysis {
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

    return CellVoltageAnalysis(
        readings = readings,
        stats = stats,
        effectiveCellCount = effectiveCellCount,
        wiringShortCellNumber = wiringShortCellNumber,
        zeroVoltageCellNumber = zeroVoltageCellNumber
    )
}
