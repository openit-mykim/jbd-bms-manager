package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.R
import com.gytxtx.openjbd.maintenance.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDisplayTest {
    @Test
    fun cellMillivoltsFormatAndRoundTrip() {
        assertEquals("4,250 mV", literal(SettingField.COVP, 4_250))
        assertEquals(
            SettingsInputResult.Valid(4_250),
            SettingsDisplay.parseInput(SettingField.COVP, "4250")
        )
    }

    @Test
    fun packVoltageFormatsInVoltsAndRoundTripsToTenMillivoltRaw() {
        assertEquals("58.80 V", literal(SettingField.POVP, 5_880))
        assertEquals(
            SettingsInputResult.Valid(5_880),
            SettingsDisplay.parseInput(SettingField.POVP, "58.80")
        )
    }

    @Test
    fun temperatureKelvinRawFormatsInCelsiusAndUsesRoundedRaw() {
        assertEquals("0.0 °C", literal(SettingField.CHG_OVER_TEMP, 2_731))
        assertEquals(
            SettingsInputResult.Valid(2_732),
            SettingsDisplay.parseInput(SettingField.CHG_OVER_TEMP, "0.0")
        )
    }

    @Test
    fun temperatureRejectsMoreThanOneDisplayDecimal() {
        val result = SettingsDisplay.parseInput(SettingField.CHG_UNDER_TEMP, "12.34")
        assertEquals(
            R.string.settings_validation_temperature_precision,
            (result as SettingsInputResult.Invalid).message.stringResId
        )
    }

    @Test
    fun chargeCurrentFormatsPositiveAmpsAndRoundTrips() {
        assertEquals("200.0 A", literal(SettingField.CHG_OVERCURRENT, 20_000))
        assertEquals(
            SettingsInputResult.Valid(20_000),
            SettingsDisplay.parseInput(SettingField.CHG_OVERCURRENT, "200.0")
        )
    }

    @Test
    fun dischargeCurrentFormatsSignedAmpsAndRoundTrips() {
        assertEquals("-200.0 A", literal(SettingField.DSG_OVERCURRENT, -20_000))
        assertEquals(
            SettingsInputResult.Valid(-20_000),
            SettingsDisplay.parseInput(SettingField.DSG_OVERCURRENT, "-200.0")
        )
    }

    @Test
    fun designCapacityFormatsAmpHoursAndRoundTrips() {
        assertEquals("100.0 Ah", literal(SettingField.DESIGN_CAPACITY, 10_000))
        assertEquals(
            SettingsInputResult.Valid(10_000),
            SettingsDisplay.parseInput(SettingField.DESIGN_CAPACITY, "100.0")
        )
    }

    @Test
    fun bitFieldsUseLocalizedResourcesAndToggleRawValues() {
        assertEquals(
            SettingsDisplayText.Resource(R.string.settings_value_on),
            SettingsDisplay.displayValue(SettingField.BALANCE_ENABLE, 1).text
        )
        assertEquals(
            SettingsDisplayText.Resource(R.string.settings_value_off),
            SettingsDisplay.displayValue(SettingField.BALANCE_ENABLE, 0).text
        )
        assertEquals(1, SettingsDisplay.rawFromToggle(true))
        assertEquals(0, SettingsDisplay.rawFromToggle(false))
    }

    @Test
    fun changeSummaryContainsLabelOldNewAndDisplayUnits() {
        val summary = SettingsDisplay.buildChangeSummary(
            SettingField.COVP,
            4_250,
            4_200
        ) { id ->
            when (id) {
                R.string.settings_field_covp -> "셀 과전압 보호"
                R.string.settings_unit_mv -> "mV"
                else -> "?"
            }
        }

        assertEquals("셀 과전압 보호 4,250 mV → 4,200 mV", summary)
    }

    @Test
    fun outcomeAndDetailLabelsCoverCommitAndVerificationResults() {
        assertEquals(
            R.string.settings_outcome_committed,
            SettingsDisplay.outcomeLabelResId(WriteSessionOutcome.Committed)
        )
        assertEquals(
            R.string.settings_outcome_not_committed,
            SettingsDisplay.outcomeLabelResId(WriteSessionOutcome.NotCommitted)
        )
        assertEquals(
            R.string.settings_detail_verified,
            SettingsDisplay.detailLabelResId(ChangeDetail.Verified(1, 2))
        )
        assertEquals(
            R.string.settings_detail_read_back_mismatch,
            SettingsDisplay.detailLabelResId(ChangeDetail.ReadBackMismatch(1, 2))
        )
        assertEquals(
            R.string.settings_detail_skipped_no_change,
            SettingsDisplay.detailLabelResId(ChangeDetail.SkippedNoChange)
        )
    }

    @Test
    fun transportDetailLabelsCoverEveryFailureKind() {
        assertEquals(
            R.string.settings_detail_no_response,
            SettingsDisplay.detailLabelResId(ChangeDetail.NoResponse(ChangePhase.READ_CURRENT))
        )
        assertEquals(
            R.string.settings_detail_error_status,
            SettingsDisplay.detailLabelResId(ChangeDetail.ErrorStatus(ChangePhase.WRITE, 0x80))
        )
        assertEquals(
            R.string.settings_detail_transport_failure,
            SettingsDisplay.detailLabelResId(
                ChangeDetail.TransportFailure(ChangePhase.WRITE, "lost")
            )
        )
        assertEquals(
            R.string.settings_detail_malformed_response,
            SettingsDisplay.detailLabelResId(
                ChangeDetail.MalformedResponse(ChangePhase.READ_BACK, "short")
            )
        )
    }

    @Test
    fun warningLabelsCoverEverySessionWarning() {
        val labels = SettingsSessionWarning.values().map(SettingsDisplay::warningLabelResId).toSet()

        assertEquals(SettingsSessionWarning.values().size, labels.size)
        assertTrue(labels.contains(R.string.settings_warning_exit_unconfirmed))
        assertTrue(labels.contains(R.string.settings_warning_factory_mode_may_remain))
        assertTrue(labels.contains(R.string.settings_warning_counter_snapshot_unavailable))
        assertTrue(labels.contains(R.string.settings_warning_post_commit_unavailable))
        assertTrue(labels.contains(R.string.settings_warning_post_commit_mismatch))
        assertTrue(labels.contains(R.string.settings_warning_direct_fallback))
    }

    @Test
    fun fieldIsEditableOnlyWithFactoryValueAndNoRegisterError() {
        val field = SettingField.COVP
        val values = mapOf(field to FieldValue(4_250, 4_250.0))

        assertTrue(SettingsDisplay.isEditable(field, SettingsAccessMode.FACTORY, values, emptyMap()))
        assertFalse(SettingsDisplay.isEditable(field, SettingsAccessMode.DIRECT, values, emptyMap()))
        assertFalse(SettingsDisplay.isEditable(field, SettingsAccessMode.BLOCKED, values, emptyMap()))
        assertFalse(SettingsDisplay.isEditable(field, SettingsAccessMode.FACTORY, emptyMap(), emptyMap()))
        assertFalse(
            SettingsDisplay.isEditable(
                field,
                SettingsAccessMode.FACTORY,
                values,
                mapOf(field.register.address to RegisterAccessError.NoResponse)
            )
        )
    }

    @Test
    fun readOnlyNoticeStatesAreDistinctAndFactoryValuesNeedNoNotice() {
        assertEquals(
            SettingsReadOnlyNotice.NOT_CONNECTED,
            SettingsDisplay.readOnlyNotice(true, SettingsAccessMode.FACTORY, true)
        )
        assertEquals(
            SettingsReadOnlyNotice.NO_VALUES,
            SettingsDisplay.readOnlyNotice(false, SettingsAccessMode.FACTORY, false)
        )
        assertEquals(
            SettingsReadOnlyNotice.DIRECT,
            SettingsDisplay.readOnlyNotice(false, SettingsAccessMode.DIRECT, true)
        )
        assertEquals(
            SettingsReadOnlyNotice.BLOCKED,
            SettingsDisplay.readOnlyNotice(false, SettingsAccessMode.BLOCKED, false)
        )
        assertNull(SettingsDisplay.readOnlyNotice(false, SettingsAccessMode.FACTORY, true))
    }

    @Test
    fun rangeValidationMapsToLocalizedMessageWithDisplayRange() {
        val validation = validateField(SettingField.COVP, 4_501)
        val message = SettingsDisplay.validationMessage(
            SettingField.COVP,
            validation,
            ::unit
        )!!

        assertEquals(R.string.settings_validation_range, message.stringResId)
        assertEquals(listOf("2,000 mV – 4,500 mV"), message.formatArgs)
    }

    @Test
    fun relationalValidationMapsToClearLocalizedMessage() {
        val validation = validateField(
            SettingField.COVP_RELEASE,
            4_300,
            mapOf(SettingField.COVP to 4_250)
        )
        val message = SettingsDisplay.validationMessage(
            SettingField.COVP_RELEASE,
            validation,
            ::unit
        )!!

        assertEquals(R.string.settings_validation_covp_relation, message.stringResId)
        assertTrue(message.formatArgs.isEmpty())
    }

    @Test
    fun validValidationNeedsNoMessageAndFallbackPreservesReason() {
        assertNull(
            SettingsDisplay.validationMessage(
                SettingField.COVP,
                ValidationResult.Valid,
                ::unit
            )
        )
        val fallback = SettingsDisplay.validationMessage(
            SettingField.COVP,
            ValidationResult.Invalid("unexpected validation"),
            ::unit
        )!!
        assertEquals(R.string.settings_validation_fallback, fallback.stringResId)
        assertEquals(listOf("unexpected validation"), fallback.formatArgs)
    }

    @Test
    fun allTwentyFiveConfigurationFieldsHaveDistinctResourceBackedLabels() {
        val labels = SettingField.values()
            .filter { it.group != SettingsGroup.CALIBRATION }
            .map(SettingsDisplay::fieldLabelResId)

        assertEquals(25, labels.size)
        assertEquals(25, labels.toSet().size)
    }

    @Test
    fun scalarPrecisionMustAlignToRawRegisterUnit() {
        val result = SettingsDisplay.parseInput(SettingField.POVP, "58.805")

        assertEquals(
            R.string.settings_validation_precision,
            (result as SettingsInputResult.Invalid).message.stringResId
        )
    }

    private fun literal(field: SettingField, raw: Int): String {
        val text = SettingsDisplay.displayValue(field, raw).text as
            SettingsDisplayText.NumberWithUnit
        return "${text.number} ${unit(text.unitStringResId)}"
    }

    private fun unit(stringResId: Int): String = when (stringResId) {
        R.string.settings_unit_mv -> "mV"
        R.string.settings_unit_volt -> "V"
        R.string.settings_unit_celsius -> "°C"
        R.string.settings_unit_amp -> "A"
        R.string.settings_unit_amp_hour -> "Ah"
        else -> error("Unexpected unit resource: $stringResId")
    }
}
