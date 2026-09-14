package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.R
import com.gytxtx.openjbd.maintenance.ValidationResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

sealed interface SettingsDisplayText {
    data class NumberWithUnit(
        val number: String,
        val unitStringResId: Int
    ) : SettingsDisplayText

    data class Resource(val stringResId: Int) : SettingsDisplayText
}

data class SettingsDisplayValue(
    val text: SettingsDisplayText,
    val inputText: String,
    val unitStringResId: Int?
)

data class SettingsUiMessage(
    val stringResId: Int,
    val formatArgs: List<Any> = emptyList()
)

sealed interface SettingsInputResult {
    data class Valid(val rawValue: Int) : SettingsInputResult
    data class Invalid(val message: SettingsUiMessage) : SettingsInputResult
}

enum class SettingsReadOnlyNotice {
    NOT_CONNECTED,
    NO_VALUES,
    DIRECT,
    BLOCKED
}

/** Pure formatting, conversion and label decisions used by the settings UI. */
object SettingsDisplay {
    private val cellVoltageFields = setOf(
        SettingField.BAL_START,
        SettingField.BAL_WINDOW,
        SettingField.COVP,
        SettingField.COVP_RELEASE,
        SettingField.CUVP,
        SettingField.CUVP_RELEASE
    )
    private val packVoltageFields = setOf(
        SettingField.POVP,
        SettingField.POVP_RELEASE,
        SettingField.PUVP,
        SettingField.PUVP_RELEASE
    )
    private val temperatureFields = setOf(
        SettingField.CHG_OVER_TEMP,
        SettingField.CHG_OVER_TEMP_RELEASE,
        SettingField.CHG_UNDER_TEMP,
        SettingField.CHG_UNDER_TEMP_RELEASE,
        SettingField.DSG_OVER_TEMP,
        SettingField.DSG_OVER_TEMP_RELEASE,
        SettingField.DSG_UNDER_TEMP,
        SettingField.DSG_UNDER_TEMP_RELEASE
    )
    private val currentFields = setOf(
        SettingField.CHG_OVERCURRENT,
        SettingField.DSG_OVERCURRENT
    )
    private val bitFields = setOf(
        SettingField.BALANCE_ENABLE,
        SettingField.CHARGE_BALANCE_ENABLE
    )

    fun fieldLabelResId(field: SettingField): Int = when (field) {
        SettingField.BAL_START -> R.string.settings_field_bal_start
        SettingField.BAL_WINDOW -> R.string.settings_field_bal_window
        SettingField.BALANCE_ENABLE -> R.string.settings_field_balance_enable
        SettingField.CHARGE_BALANCE_ENABLE -> R.string.settings_field_charge_balance_enable
        SettingField.COVP -> R.string.settings_field_covp
        SettingField.COVP_RELEASE -> R.string.settings_field_covp_release
        SettingField.CUVP -> R.string.settings_field_cuvp
        SettingField.CUVP_RELEASE -> R.string.settings_field_cuvp_release
        SettingField.POVP -> R.string.settings_field_povp
        SettingField.POVP_RELEASE -> R.string.settings_field_povp_release
        SettingField.PUVP -> R.string.settings_field_puvp
        SettingField.PUVP_RELEASE -> R.string.settings_field_puvp_release
        SettingField.CHG_OVERCURRENT -> R.string.settings_field_chg_overcurrent
        SettingField.DSG_OVERCURRENT -> R.string.settings_field_dsg_overcurrent
        SettingField.CHG_OVER_TEMP -> R.string.settings_field_chg_over_temp
        SettingField.CHG_OVER_TEMP_RELEASE -> R.string.settings_field_chg_over_temp_release
        SettingField.CHG_UNDER_TEMP -> R.string.settings_field_chg_under_temp
        SettingField.CHG_UNDER_TEMP_RELEASE -> R.string.settings_field_chg_under_temp_release
        SettingField.DSG_OVER_TEMP -> R.string.settings_field_dsg_over_temp
        SettingField.DSG_OVER_TEMP_RELEASE -> R.string.settings_field_dsg_over_temp_release
        SettingField.DSG_UNDER_TEMP -> R.string.settings_field_dsg_under_temp
        SettingField.DSG_UNDER_TEMP_RELEASE -> R.string.settings_field_dsg_under_temp_release
        SettingField.DESIGN_CAPACITY -> R.string.settings_field_design_capacity
    }

    fun groupTitleResId(group: SettingsGroup): Int = when (group) {
        SettingsGroup.BALANCE -> R.string.control_section_balance
        SettingsGroup.PROTECTION -> R.string.control_section_protection
        SettingsGroup.TEMPERATURE -> R.string.control_section_temperature
        SettingsGroup.CAPACITY -> R.string.control_section_capacity
    }

    fun displayValue(field: SettingField, rawValue: Int): SettingsDisplayValue = when {
        field in cellVoltageFields -> numericValue(rawValue, 0, R.string.settings_unit_mv)
        field in packVoltageFields -> numericValue(
            rawValue / 100.0,
            2,
            R.string.settings_unit_volt
        )
        field in temperatureFields -> {
            val celsius = rawValue / 10.0 - 273.15
            numericValue(normalizeNearZero(celsius), 1, R.string.settings_unit_celsius)
        }
        field in currentFields -> numericValue(rawValue / 100.0, 1, R.string.settings_unit_amp)
        field == SettingField.DESIGN_CAPACITY -> numericValue(
            rawValue / 100.0,
            1,
            R.string.settings_unit_amp_hour
        )
        field in bitFields -> SettingsDisplayValue(
            text = SettingsDisplayText.Resource(
                if (rawValue == 1) R.string.settings_value_on else R.string.settings_value_off
            ),
            inputText = rawValue.toString(),
            unitStringResId = null
        )
        else -> error("Unsupported settings field: $field")
    }

    fun parseInput(field: SettingField, input: String): SettingsInputResult {
        if (field in bitFields) {
            return SettingsInputResult.Invalid(SettingsUiMessage(R.string.settings_validation_use_toggle))
        }
        val decimal = input.trim().toBigDecimalOrNull()
            ?: return SettingsInputResult.Invalid(
                SettingsUiMessage(R.string.settings_validation_number_required)
            )

        val rawDecimal = when {
            field in cellVoltageFields -> decimal
            field in packVoltageFields -> decimal.multiply(BigDecimal("100"))
            field in temperatureFields -> {
                if (decimal.stripTrailingZeros().scale() > 1) {
                    return SettingsInputResult.Invalid(
                        SettingsUiMessage(R.string.settings_validation_temperature_precision)
                    )
                }
                decimal.add(BigDecimal("273.15"))
                    .multiply(BigDecimal.TEN)
                    .setScale(0, RoundingMode.HALF_UP)
            }
            field in currentFields -> decimal.multiply(BigDecimal("100"))
            field == SettingField.DESIGN_CAPACITY -> decimal.multiply(BigDecimal("100"))
            else -> error("Unsupported settings field: $field")
        }

        val integral = try {
            rawDecimal.setScale(0, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            return SettingsInputResult.Invalid(
                SettingsUiMessage(R.string.settings_validation_precision)
            )
        }
        val raw = try {
            integral.intValueExact()
        } catch (_: ArithmeticException) {
            return SettingsInputResult.Invalid(
                SettingsUiMessage(R.string.settings_validation_number_too_large)
            )
        }
        return SettingsInputResult.Valid(raw)
    }

    fun rawFromToggle(enabled: Boolean): Int = if (enabled) 1 else 0

    fun buildChangeSummary(
        field: SettingField,
        oldRaw: Int,
        newRaw: Int,
        stringResolver: (Int) -> String
    ): String = stringResolver(fieldLabelResId(field)) + " " +
        resolvedValue(field, oldRaw, stringResolver) + " → " +
        resolvedValue(field, newRaw, stringResolver)

    fun isEditable(
        field: SettingField,
        accessMode: SettingsAccessMode,
        values: Map<SettingField, FieldValue>,
        registerErrors: Map<Int, RegisterAccessError>
    ): Boolean = accessMode == SettingsAccessMode.FACTORY &&
        values.containsKey(field) &&
        !registerErrors.containsKey(field.register.address)

    fun readOnlyNotice(
        notConnected: Boolean,
        accessMode: SettingsAccessMode,
        hasValues: Boolean
    ): SettingsReadOnlyNotice? = when {
        notConnected -> SettingsReadOnlyNotice.NOT_CONNECTED
        accessMode == SettingsAccessMode.BLOCKED -> SettingsReadOnlyNotice.BLOCKED
        !hasValues -> SettingsReadOnlyNotice.NO_VALUES
        accessMode == SettingsAccessMode.DIRECT -> SettingsReadOnlyNotice.DIRECT
        else -> null
    }

    fun readOnlyNoticeResId(notice: SettingsReadOnlyNotice): Int = when (notice) {
        SettingsReadOnlyNotice.NOT_CONNECTED -> R.string.settings_notice_not_connected
        SettingsReadOnlyNotice.NO_VALUES -> R.string.settings_notice_no_values
        SettingsReadOnlyNotice.DIRECT -> R.string.settings_notice_direct_read_only
        SettingsReadOnlyNotice.BLOCKED -> R.string.settings_notice_blocked
    }

    fun validationMessage(
        field: SettingField,
        result: ValidationResult,
        stringResolver: (Int) -> String
    ): SettingsUiMessage? {
        if (result is ValidationResult.Valid) return null
        val reason = (result as ValidationResult.Invalid).reason
        val relationString = when (reason) {
            "covpRelease must be lower than covp" -> R.string.settings_validation_covp_relation
            "cuvpRelease must be higher than cuvp" -> R.string.settings_validation_cuvp_relation
            "povpRelease must be lower than povp" -> R.string.settings_validation_povp_relation
            "puvpRelease must be higher than puvp" -> R.string.settings_validation_puvp_relation
            "chgOvercurrent must be positive" -> R.string.settings_validation_charge_current_positive
            "dsgOvercurrent must be negative" -> R.string.settings_validation_discharge_current_negative
            else -> null
        }
        if (relationString != null) return SettingsUiMessage(relationString)
        if (reason.contains(" is outside ")) {
            return SettingsUiMessage(
                R.string.settings_validation_range,
                listOf(displayRange(field, stringResolver))
            )
        }
        return SettingsUiMessage(R.string.settings_validation_fallback, listOf(reason))
    }

    fun accessModeLabelResId(accessMode: SettingsAccessMode): Int = when (accessMode) {
        SettingsAccessMode.FACTORY -> R.string.settings_access_factory
        SettingsAccessMode.DIRECT -> R.string.settings_access_direct
        SettingsAccessMode.BLOCKED -> R.string.settings_access_blocked
    }

    fun outcomeLabelResId(outcome: WriteSessionOutcome): Int = when (outcome) {
        WriteSessionOutcome.Committed -> R.string.settings_outcome_committed
        WriteSessionOutcome.NotCommitted -> R.string.settings_outcome_not_committed
        is WriteSessionOutcome.ValidationFailed -> R.string.settings_outcome_validation_failed
        is WriteSessionOutcome.EntryFailed -> R.string.settings_outcome_entry_failed
    }

    fun detailLabelResId(detail: ChangeDetail): Int = when (detail) {
        is ChangeDetail.Verified -> R.string.settings_detail_verified
        is ChangeDetail.ReadBackMismatch -> R.string.settings_detail_read_back_mismatch
        is ChangeDetail.NoResponse -> R.string.settings_detail_no_response
        is ChangeDetail.ErrorStatus -> R.string.settings_detail_error_status
        is ChangeDetail.TransportFailure -> R.string.settings_detail_transport_failure
        is ChangeDetail.MalformedResponse -> R.string.settings_detail_malformed_response
        ChangeDetail.SkippedNoChange -> R.string.settings_detail_skipped_no_change
    }

    fun warningLabelResId(warning: SettingsSessionWarning): Int = when (warning) {
        SettingsSessionWarning.EXIT_UNCONFIRMED -> R.string.settings_warning_exit_unconfirmed
        SettingsSessionWarning.DEVICE_MAY_REMAIN_IN_FACTORY_MODE ->
            R.string.settings_warning_factory_mode_may_remain
        SettingsSessionWarning.COUNTER_SNAPSHOT_UNAVAILABLE ->
            R.string.settings_warning_counter_snapshot_unavailable
        SettingsSessionWarning.POST_COMMIT_CONFIRMATION_UNAVAILABLE ->
            R.string.settings_warning_post_commit_unavailable
        SettingsSessionWarning.POST_COMMIT_MISMATCH ->
            R.string.settings_warning_post_commit_mismatch
        SettingsSessionWarning.FACTORY_ENTRY_FAILED_DIRECT_FALLBACK ->
            R.string.settings_warning_direct_fallback
    }

    fun registerErrorLabelResId(error: RegisterAccessError): Int = when (error) {
        RegisterAccessError.NoResponse -> R.string.settings_detail_no_response
        is RegisterAccessError.ErrorStatus -> R.string.settings_detail_error_status
        is RegisterAccessError.TransportFailure -> R.string.settings_detail_transport_failure
        is RegisterAccessError.Malformed -> R.string.settings_detail_malformed_response
    }

    private fun numericValue(
        value: Number,
        decimals: Int,
        unitStringResId: Int
    ): SettingsDisplayValue {
        val formatted = formatNumber(value.toDouble(), decimals)
        return SettingsDisplayValue(
            SettingsDisplayText.NumberWithUnit(formatted, unitStringResId),
            formatted.replace(",", ""),
            unitStringResId
        )
    }

    private fun resolvedValue(
        field: SettingField,
        rawValue: Int,
        stringResolver: (Int) -> String
    ): String = when (val text = displayValue(field, rawValue).text) {
        is SettingsDisplayText.NumberWithUnit ->
            text.number + " " + stringResolver(text.unitStringResId)
        is SettingsDisplayText.Resource -> stringResolver(text.stringResId)
    }

    private fun displayRange(field: SettingField, stringResolver: (Int) -> String): String {
        if (field in bitFields) return stringResolver(R.string.settings_boolean_raw_range)
        val min = resolvedValue(field, field.range.minRaw, stringResolver)
        val max = resolvedValue(field, field.range.maxRaw, stringResolver)
        return "$min – $max"
    }

    private fun formatNumber(value: Double, decimals: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = true
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(value)
    }

    private fun normalizeNearZero(value: Double): Double =
        if (abs(value) <= 0.0500001) 0.0 else value
}
