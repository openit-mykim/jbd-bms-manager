package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.ValidationResult

const val BALANCE_WINDOW_STEP_MV = 5
const val BALANCE_WINDOW_STEP_REASON = "balWindow must use 5 mV steps"

/**
 * Validates one raw candidate. Relational checks run only when the counterpart is present in
 * [currentValues]; callers applying a staged set should pass a map containing the current device
 * snapshot overlaid with every staged candidate.
 */
fun validateField(
    field: SettingField,
    rawValue: Int,
    currentValues: Map<SettingField, Int> = emptyMap()
): ValidationResult {
    if (rawValue !in field.range) {
        return ValidationResult.Invalid(
            "${field.key} raw value $rawValue is outside " +
                "${field.range.minRaw}..${field.range.maxRaw}"
        )
    }
    if (rawValue !in field.register.codec.rawRange && field.bitIndex == null) {
        return ValidationResult.Invalid("${field.key} cannot be represented by its 16-bit register")
    }
    if (field == SettingField.BAL_WINDOW && rawValue % BALANCE_WINDOW_STEP_MV != 0) {
        return ValidationResult.Invalid(BALANCE_WINDOW_STEP_REASON)
    }

    val values = currentValues + (field to rawValue)
    return firstRelationalFailure(field, values)?.let(ValidationResult::Invalid)
        ?: ValidationResult.Valid
}

private fun firstRelationalFailure(
    field: SettingField,
    values: Map<SettingField, Int>
): String? {
    fun requireLess(lower: SettingField, upper: SettingField, reason: String): String? {
        if (field != lower && field != upper) return null
        val lowerValue = values[lower] ?: return null
        val upperValue = values[upper] ?: return null
        return if (lowerValue < upperValue) null else reason
    }

    return requireLess(
        SettingField.COVP_RELEASE,
        SettingField.COVP,
        "covpRelease must be lower than covp"
    ) ?: requireLess(
        SettingField.CUVP,
        SettingField.CUVP_RELEASE,
        "cuvpRelease must be higher than cuvp"
    ) ?: requireLess(
        SettingField.POVP_RELEASE,
        SettingField.POVP,
        "povpRelease must be lower than povp"
    ) ?: requireLess(
        SettingField.PUVP,
        SettingField.PUVP_RELEASE,
        "puvpRelease must be higher than puvp"
    ) ?: when (field) {
        SettingField.CHG_OVERCURRENT ->
            if (values.getValue(field) > 0) null else "chgOvercurrent must be positive"
        SettingField.DSG_OVERCURRENT ->
            if (values.getValue(field) < 0) null else "dsgOvercurrent must be negative"
        else -> null
    }
}

fun stepBalanceWindow(rawValue: Int, increase: Boolean): Int {
    val range = SettingField.BAL_WINDOW.range
    val delta = if (increase) BALANCE_WINDOW_STEP_MV else -BALANCE_WINDOW_STEP_MV
    return (rawValue.toLong() + delta)
        .coerceIn(range.minRaw.toLong(), range.maxRaw.toLong())
        .toInt()
}
