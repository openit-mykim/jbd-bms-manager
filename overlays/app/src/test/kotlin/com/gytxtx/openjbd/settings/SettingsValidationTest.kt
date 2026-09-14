package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationTest {
    @Test
    fun fieldBoundsAcceptEdgesAndRejectOutsideValues() {
        assertValid(SettingField.COVP, 2_000)
        assertValid(SettingField.COVP, 4_500)
        assertInvalid(SettingField.COVP, 1_999)
        assertInvalid(SettingField.COVP, 4_501)

        assertValid(SettingField.BAL_WINDOW, 5)
        assertInvalid(SettingField.BAL_WINDOW, 4)
        assertValid(SettingField.CHG_OVER_TEMP, 3_760)
        assertInvalid(SettingField.CHG_OVER_TEMP, 3_761)
        assertValid(SettingField.DESIGN_CAPACITY, 0xFFFF)
        assertInvalid(SettingField.DESIGN_CAPACITY, 0x10000)
    }

    @Test
    fun covpReleaseBoundaryRequiresStrictlyLowerValue() {
        val current = mapOf(SettingField.COVP to 4_200)
        assertValid(SettingField.COVP_RELEASE, 4_199, current)
        assertInvalid(SettingField.COVP_RELEASE, 4_200, current)
    }

    @Test
    fun cuvpReleaseBoundaryRequiresStrictlyHigherValue() {
        val current = mapOf(SettingField.CUVP to 2_800)
        assertValid(SettingField.CUVP_RELEASE, 2_801, current)
        assertInvalid(SettingField.CUVP_RELEASE, 2_800, current)
    }

    @Test
    fun povpReleaseBoundaryRequiresStrictlyLowerValue() {
        val current = mapOf(SettingField.POVP to 5_800)
        assertValid(SettingField.POVP_RELEASE, 5_799, current)
        assertInvalid(SettingField.POVP_RELEASE, 5_800, current)
    }

    @Test
    fun puvpReleaseBoundaryRequiresStrictlyHigherValue() {
        val current = mapOf(SettingField.PUVP to 4_000)
        assertValid(SettingField.PUVP_RELEASE, 4_001, current)
        assertInvalid(SettingField.PUVP_RELEASE, 4_000, current)
    }

    @Test
    fun chargeCurrentBoundaryIsPositiveAndConservative() {
        assertValid(SettingField.CHG_OVERCURRENT, 100)
        assertInvalid(SettingField.CHG_OVERCURRENT, 99)
        assertInvalid(SettingField.CHG_OVERCURRENT, 0)
    }

    @Test
    fun dischargeCurrentBoundaryIsNegativeAndConservative() {
        assertValid(SettingField.DSG_OVERCURRENT, -100)
        assertInvalid(SettingField.DSG_OVERCURRENT, -99)
        assertInvalid(SettingField.DSG_OVERCURRENT, 0)
    }

    @Test
    fun relationIsCheckedWhenThresholdSideChangesToo() {
        assertInvalid(
            SettingField.COVP,
            4_100,
            mapOf(SettingField.COVP_RELEASE to 4_100)
        )
        assertValid(
            SettingField.COVP,
            4_101,
            mapOf(SettingField.COVP_RELEASE to 4_100)
        )
    }

    @Test
    fun balanceWindowAcceptsFiveMillivoltStepsAcrossRange() {
        listOf(5, 10, 15, 20, 1_000).forEach { raw ->
            assertValid(SettingField.BAL_WINDOW, raw)
        }
    }

    @Test
    fun balanceWindowRejectsValuesOutsideFiveMillivoltStepsWithStepReason() {
        listOf(6, 12, 17).forEach { raw ->
            assertInvalidReason(SettingField.BAL_WINDOW, raw, BALANCE_WINDOW_STEP_REASON)
        }
    }

    @Test
    fun balanceWindowRejectsValuesOutsideExistingRangeWithRangeReason() {
        assertInvalidReasonContains(SettingField.BAL_WINDOW, 4, " is outside ")
        assertInvalidReasonContains(SettingField.BAL_WINDOW, 1_001, " is outside ")
    }

    @Test
    fun balanceWindowDecreaseClampsAtFiveMillivolts() {
        assertEquals(5, stepBalanceWindow(5, increase = false))
    }

    @Test
    fun balanceWindowIncreaseClampsAtOneThousandMillivolts() {
        assertEquals(1_000, stepBalanceWindow(1_000, increase = true))
    }

    @Test
    fun balanceWindowIncreaseAdvancesByFiveMillivolts() {
        val fifteen = stepBalanceWindow(10, increase = true)
        val twenty = stepBalanceWindow(fifteen, increase = true)

        assertEquals(15, fifteen)
        assertEquals(20, twenty)
    }

    private fun assertValid(
        field: SettingField,
        raw: Int,
        values: Map<SettingField, Int> = emptyMap()
    ) {
        assertTrue(validateField(field, raw, values) is ValidationResult.Valid)
    }

    private fun assertInvalid(
        field: SettingField,
        raw: Int,
        values: Map<SettingField, Int> = emptyMap()
    ) {
        assertTrue(validateField(field, raw, values) is ValidationResult.Invalid)
    }

    private fun assertInvalidReason(field: SettingField, raw: Int, expectedReason: String) {
        val result = validateField(field, raw) as ValidationResult.Invalid

        assertEquals(expectedReason, result.reason)
    }

    private fun assertInvalidReasonContains(field: SettingField, raw: Int, expected: String) {
        val result = validateField(field, raw) as ValidationResult.Invalid

        assertTrue(result.reason.contains(expected))
    }
}
