package com.gytxtx.openjbd.settings

enum class CalibrationKind {
    IDLE_CURRENT,
    CHARGE_CURRENT,
    DISCHARGE_CURRENT,
    CELL_VOLTAGE,
    NTC
}

enum class CalibrationGuidanceKey {
    IDLE_ZERO,
    CHARGE_REFERENCE,
    DISCHARGE_REFERENCE,
    CELL_REFERENCE,
    NTC_REFERENCE
}

enum class CalibrationUnit {
    RAW,
    AMP,
    MILLIVOLT,
    CELSIUS
}

enum class CalibrationOperationNoteKey {
    HARDWARE_UNVERIFIED,
    CURRENT_SCALE_HARDWARE_UNVERIFIED
}

data class CalibrationTarget(
    val kind: CalibrationKind,
    val field: SettingField,
    val slotNumber: Int? = null,
    val guidanceKey: CalibrationGuidanceKey
)

data class CalibrationMeasurement(
    val value: Double,
    val unit: CalibrationUnit
)

data class CalibrationOperation(
    val registerAddress: Int,
    val encodedRaw: Int,
    val noteKey: CalibrationOperationNoteKey
)

/**
 * Keeps the calibration UX's three concepts separate even before an external reference is staged.
 */
data class CalibrationConceptViewModel(
    val target: CalibrationTarget,
    val storedCalibrationRaw: Int?,
    val bmsReading: CalibrationMeasurement?,
    val externalReferenceMeasurement: CalibrationMeasurement?,
    val resultingOperation: CalibrationOperation?
)

/** Pure target selection and view-model construction for the calibration screen. */
object CalibrationTargets {
    val cellFields = listOf(
        SettingField.CAL_CELL_01, SettingField.CAL_CELL_02, SettingField.CAL_CELL_03,
        SettingField.CAL_CELL_04, SettingField.CAL_CELL_05, SettingField.CAL_CELL_06,
        SettingField.CAL_CELL_07, SettingField.CAL_CELL_08, SettingField.CAL_CELL_09,
        SettingField.CAL_CELL_10, SettingField.CAL_CELL_11, SettingField.CAL_CELL_12,
        SettingField.CAL_CELL_13, SettingField.CAL_CELL_14, SettingField.CAL_CELL_15,
        SettingField.CAL_CELL_16, SettingField.CAL_CELL_17, SettingField.CAL_CELL_18,
        SettingField.CAL_CELL_19, SettingField.CAL_CELL_20, SettingField.CAL_CELL_21,
        SettingField.CAL_CELL_22, SettingField.CAL_CELL_23, SettingField.CAL_CELL_24,
        SettingField.CAL_CELL_25, SettingField.CAL_CELL_26, SettingField.CAL_CELL_27,
        SettingField.CAL_CELL_28, SettingField.CAL_CELL_29, SettingField.CAL_CELL_30,
        SettingField.CAL_CELL_31, SettingField.CAL_CELL_32
    )

    val ntcFields = listOf(
        SettingField.CAL_NTC_01, SettingField.CAL_NTC_02, SettingField.CAL_NTC_03,
        SettingField.CAL_NTC_04, SettingField.CAL_NTC_05, SettingField.CAL_NTC_06,
        SettingField.CAL_NTC_07, SettingField.CAL_NTC_08
    )

    val currentTargets = listOf(
        CalibrationTarget(
            CalibrationKind.IDLE_CURRENT,
            SettingField.CAL_IDLE_CURRENT,
            guidanceKey = CalibrationGuidanceKey.IDLE_ZERO
        ),
        CalibrationTarget(
            CalibrationKind.CHARGE_CURRENT,
            SettingField.CAL_CHARGE_CURRENT,
            guidanceKey = CalibrationGuidanceKey.CHARGE_REFERENCE
        ),
        CalibrationTarget(
            CalibrationKind.DISCHARGE_CURRENT,
            SettingField.CAL_DISCHARGE_CURRENT,
            guidanceKey = CalibrationGuidanceKey.DISCHARGE_REFERENCE
        )
    )

    private val cellTargets = cellFields.mapIndexed { index, field ->
        CalibrationTarget(
            CalibrationKind.CELL_VOLTAGE,
            field,
            slotNumber = index + 1,
            guidanceKey = CalibrationGuidanceKey.CELL_REFERENCE
        )
    }
    private val ntcTargets = ntcFields.mapIndexed { index, field ->
        CalibrationTarget(
            CalibrationKind.NTC,
            field,
            slotNumber = index + 1,
            guidanceKey = CalibrationGuidanceKey.NTC_REFERENCE
        )
    }
    private val targetsByField = (currentTargets + cellTargets + ntcTargets).associateBy {
        it.field
    }

    /**
     * Basic-info counts take precedence. A received sensor list is a safe fallback when basic info
     * is absent or reports zero; all counts are clamped to the documented register-map capacity.
     */
    fun visibleTargets(
        cellCount: Int?,
        ntcCount: Int?,
        observedCellCount: Int = 0,
        observedNtcCount: Int = 0
    ): List<CalibrationTarget> {
        val visibleCells = safeCount(cellCount, observedCellCount, cellFields.size)
        val visibleNtcs = safeCount(ntcCount, observedNtcCount, ntcFields.size)
        return currentTargets + cellTargets.take(visibleCells) + ntcTargets.take(visibleNtcs)
    }

    fun targetFor(field: SettingField): CalibrationTarget? = targetsByField[field]

    fun buildConceptViewModel(
        target: CalibrationTarget,
        storedCalibrationRaw: Int?,
        bmsReading: CalibrationMeasurement?,
        proposedRaw: Int?
    ): CalibrationConceptViewModel {
        val externalReference = proposedRaw?.let { measurementFor(target.kind, it) }
        val operation = proposedRaw?.let {
            CalibrationOperation(
                registerAddress = target.field.register.address,
                encodedRaw = it,
                noteKey = if (
                    target.kind == CalibrationKind.CHARGE_CURRENT ||
                    target.kind == CalibrationKind.DISCHARGE_CURRENT
                ) {
                    CalibrationOperationNoteKey.CURRENT_SCALE_HARDWARE_UNVERIFIED
                } else {
                    CalibrationOperationNoteKey.HARDWARE_UNVERIFIED
                }
            )
        }
        return CalibrationConceptViewModel(
            target = target,
            storedCalibrationRaw = storedCalibrationRaw,
            bmsReading = bmsReading,
            externalReferenceMeasurement = externalReference,
            resultingOperation = operation
        )
    }

    private fun measurementFor(kind: CalibrationKind, raw: Int): CalibrationMeasurement = when (kind) {
        CalibrationKind.IDLE_CURRENT -> CalibrationMeasurement(raw.toDouble(), CalibrationUnit.RAW)
        CalibrationKind.CHARGE_CURRENT,
        CalibrationKind.DISCHARGE_CURRENT -> CalibrationMeasurement(raw / 100.0, CalibrationUnit.AMP)
        CalibrationKind.CELL_VOLTAGE -> CalibrationMeasurement(raw.toDouble(), CalibrationUnit.MILLIVOLT)
        CalibrationKind.NTC -> CalibrationMeasurement(raw / 10.0 - 273.15, CalibrationUnit.CELSIUS)
    }

    private fun safeCount(primary: Int?, observed: Int, maximum: Int): Int =
        (primary?.takeIf { it > 0 } ?: observed).coerceIn(0, maximum)
}
