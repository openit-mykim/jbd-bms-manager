package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationDisplayTest {
    @Test
    fun calibrationRegisterCandidatesCoverDocumentedAddresses() {
        assertEquals(0xAD, SettingField.CAL_IDLE_CURRENT.register.address)
        assertEquals(0xAE, SettingField.CAL_CHARGE_CURRENT.register.address)
        assertEquals(0xAF, SettingField.CAL_DISCHARGE_CURRENT.register.address)
        assertEquals((0xB0..0xCF).toList(), CalibrationTargets.cellFields.map { it.register.address })
        assertEquals((0xD0..0xD7).toList(), CalibrationTargets.ntcFields.map { it.register.address })
    }

    @Test
    fun idleCurrentAcceptsAndDisplaysOnlyZero() {
        assertEquals("0 raw", literal(SettingField.CAL_IDLE_CURRENT, 0))
        assertEquals(
            SettingsInputResult.Valid(0),
            SettingsDisplay.parseInput(SettingField.CAL_IDLE_CURRENT, "0")
        )
        assertTrue(validateField(SettingField.CAL_IDLE_CURRENT, 0).isValid())
        assertFalse(validateField(SettingField.CAL_IDLE_CURRENT, 1).isValid())
    }

    @Test
    fun chargeCurrentUsesTwoDecimalAmpsAndTenMilliampRaw() {
        assertEquals("12.34 A", literal(SettingField.CAL_CHARGE_CURRENT, 1_234))
        assertEquals(
            SettingsInputResult.Valid(1_234),
            SettingsDisplay.parseInput(SettingField.CAL_CHARGE_CURRENT, "12.34")
        )
    }

    @Test
    fun dischargeCurrentInputIsPositiveMagnitude() {
        assertEquals("8.50 A", literal(SettingField.CAL_DISCHARGE_CURRENT, 850))
        assertEquals(
            SettingsInputResult.Valid(850),
            SettingsDisplay.parseInput(SettingField.CAL_DISCHARGE_CURRENT, "8.50")
        )
        assertFalse(validateField(SettingField.CAL_DISCHARGE_CURRENT, 0).isValid())
    }

    @Test
    fun currentInputMustAlignToTenMilliampScale() {
        val result = SettingsDisplay.parseInput(SettingField.CAL_CHARGE_CURRENT, "1.005")

        assertEquals(
            R.string.settings_validation_precision,
            (result as SettingsInputResult.Invalid).message.stringResId
        )
    }

    @Test
    fun cellVoltageUsesIntegerMillivolts() {
        assertEquals("3,315 mV", literal(SettingField.CAL_CELL_03, 3_315))
        assertEquals(
            SettingsInputResult.Valid(3_315),
            SettingsDisplay.parseInput(SettingField.CAL_CELL_03, "3315")
        )
    }

    @Test
    fun cellVoltageRejectsFractionalMillivolts() {
        val result = SettingsDisplay.parseInput(SettingField.CAL_CELL_01, "3315.5")

        assertEquals(
            R.string.settings_validation_precision,
            (result as SettingsInputResult.Invalid).message.stringResId
        )
    }

    @Test
    fun ntcCelsiusInputConvertsToTenthKelvinRaw() {
        assertEquals(
            SettingsInputResult.Valid(2_982),
            SettingsDisplay.parseInput(SettingField.CAL_NTC_02, "25.0")
        )
        assertEquals("25.1 °C", literal(SettingField.CAL_NTC_02, 2_982))
    }

    @Test
    fun ntcInputRejectsMoreThanOneDecimalPlace() {
        val result = SettingsDisplay.parseInput(SettingField.CAL_NTC_01, "25.01")

        assertEquals(
            R.string.settings_validation_temperature_precision,
            (result as SettingsInputResult.Invalid).message.stringResId
        )
    }

    @Test
    fun calibrationLabelsProvideSlotNumbers() {
        assertEquals(
            R.string.calibration_field_cell_voltage,
            SettingsDisplay.fieldLabelResId(SettingField.CAL_CELL_03)
        )
        assertEquals(listOf(3), SettingsDisplay.fieldLabelFormatArgs(SettingField.CAL_CELL_03))
        assertEquals(listOf(2), SettingsDisplay.fieldLabelFormatArgs(SettingField.CAL_NTC_02))
        assertTrue(SettingsDisplay.fieldLabelFormatArgs(SettingField.CAL_CHARGE_CURRENT).isEmpty())
    }

    @Test
    fun visibleTargetsUseBasicInfoCounts() {
        val targets = CalibrationTargets.visibleTargets(cellCount = 4, ntcCount = 2)

        assertEquals(9, targets.size)
        assertEquals((1..4).toList(), targets.filterCells().map { it.slotNumber })
        assertEquals((1..2).toList(), targets.filterNtcs().map { it.slotNumber })
    }

    @Test
    fun observedCountsAreSafeFallbacksForMissingBasicInfo() {
        val targets = CalibrationTargets.visibleTargets(
            cellCount = null,
            ntcCount = 0,
            observedCellCount = 6,
            observedNtcCount = 3
        )

        assertEquals(6, targets.filterCells().size)
        assertEquals(3, targets.filterNtcs().size)
    }

    @Test
    fun visibleTargetCountsAreClampedToRegisterMapCapacity() {
        val targets = CalibrationTargets.visibleTargets(cellCount = 99, ntcCount = 99)

        assertEquals(32, targets.filterCells().size)
        assertEquals(8, targets.filterNtcs().size)
    }

    @Test
    fun noCountsExposeOnlyCurrentCalibrationTargets() {
        val targets = CalibrationTargets.visibleTargets(cellCount = null, ntcCount = null)

        assertEquals(CalibrationTargets.currentTargets, targets)
    }

    @Test
    fun perKindGuidanceKeysRemainDistinct() {
        val keys = CalibrationTargets.currentTargets.map { it.guidanceKey } +
            listOf(
                CalibrationTargets.targetFor(SettingField.CAL_CELL_01)!!.guidanceKey,
                CalibrationTargets.targetFor(SettingField.CAL_NTC_01)!!.guidanceKey
            )

        assertEquals(CalibrationGuidanceKey.values().toSet(), keys.toSet())
    }

    @Test
    fun threeConceptModelKeepsUnenteredReferenceAndOperationEmpty() {
        val target = CalibrationTargets.targetFor(SettingField.CAL_CELL_01)!!
        val bmsReading = CalibrationMeasurement(3_310.0, CalibrationUnit.MILLIVOLT)
        val model = CalibrationTargets.buildConceptViewModel(
            target,
            storedCalibrationRaw = 3_300,
            bmsReading = bmsReading,
            proposedRaw = null
        )

        assertEquals(3_300, model.storedCalibrationRaw)
        assertEquals(bmsReading, model.bmsReading)
        assertNull(model.externalReferenceMeasurement)
        assertNull(model.resultingOperation)
    }

    @Test
    fun threeConceptModelBuildsExternalReferenceAndEncodedOperation() {
        val target = CalibrationTargets.targetFor(SettingField.CAL_CHARGE_CURRENT)!!
        val model = CalibrationTargets.buildConceptViewModel(
            target,
            storedCalibrationRaw = 1_000,
            bmsReading = CalibrationMeasurement(9.8, CalibrationUnit.AMP),
            proposedRaw = 1_025
        )

        assertEquals(CalibrationMeasurement(10.25, CalibrationUnit.AMP), model.externalReferenceMeasurement)
        assertEquals(0xAE, model.resultingOperation!!.registerAddress)
        assertEquals(1_025, model.resultingOperation!!.encodedRaw)
        assertEquals(
            CalibrationOperationNoteKey.CURRENT_SCALE_HARDWARE_UNVERIFIED,
            model.resultingOperation!!.noteKey
        )
    }

    @Test
    fun calibrationRangeValidationMapsToLocalizedDisplayRange() {
        val message = SettingsDisplay.validationMessage(
            SettingField.CAL_CELL_01,
            validateField(SettingField.CAL_CELL_01, 4_501),
            ::unit
        )!!

        assertEquals(R.string.settings_validation_range, message.stringResId)
        assertEquals(listOf("2,000 mV – 4,500 mV"), message.formatArgs)
    }

    private fun literal(field: SettingField, raw: Int): String {
        val text = SettingsDisplay.displayValue(field, raw).text as
            SettingsDisplayText.NumberWithUnit
        return "${text.number} ${unit(text.unitStringResId)}"
    }

    private fun unit(stringResId: Int): String = when (stringResId) {
        R.string.calibration_unit_raw -> "raw"
        R.string.settings_unit_mv -> "mV"
        R.string.settings_unit_celsius -> "°C"
        R.string.settings_unit_amp -> "A"
        else -> error("Unexpected unit resource: $stringResId")
    }

    private fun List<CalibrationTarget>.filterCells() =
        filter { it.kind == CalibrationKind.CELL_VOLTAGE }

    private fun List<CalibrationTarget>.filterNtcs() =
        filter { it.kind == CalibrationKind.NTC }

    private fun Any.isValid(): Boolean = this is com.gytxtx.openjbd.maintenance.ValidationResult.Valid
}
