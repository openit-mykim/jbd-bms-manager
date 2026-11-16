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

/** `lower` must be strictly below `upper` (raw units; temperatures are 0.1 K, so order holds). */
private data class OrderRelation(val lower: SettingField, val upper: SettingField, val reason: String)

private val ORDER_RELATIONS = listOf(
    OrderRelation(SettingField.COVP_RELEASE, SettingField.COVP, "covpRelease must be lower than covp"),
    OrderRelation(SettingField.CUVP, SettingField.CUVP_RELEASE, "cuvpRelease must be higher than cuvp"),
    OrderRelation(SettingField.CUVP, SettingField.COVP, "cuvp must be lower than covp"),
    OrderRelation(SettingField.POVP_RELEASE, SettingField.POVP, "povpRelease must be lower than povp"),
    OrderRelation(SettingField.PUVP, SettingField.PUVP_RELEASE, "puvpRelease must be higher than puvp"),
    OrderRelation(SettingField.PUVP, SettingField.POVP, "puvp must be lower than povp"),
    OrderRelation(
        SettingField.CHG_OVER_TEMP_RELEASE, SettingField.CHG_OVER_TEMP,
        "chgOverTempRelease must be lower than chgOverTemp"
    ),
    OrderRelation(
        SettingField.CHG_UNDER_TEMP, SettingField.CHG_UNDER_TEMP_RELEASE,
        "chgUnderTempRelease must be higher than chgUnderTemp"
    ),
    OrderRelation(
        SettingField.CHG_UNDER_TEMP, SettingField.CHG_OVER_TEMP,
        "chgUnderTemp must be lower than chgOverTemp"
    ),
    OrderRelation(
        SettingField.DSG_OVER_TEMP_RELEASE, SettingField.DSG_OVER_TEMP,
        "dsgOverTempRelease must be lower than dsgOverTemp"
    ),
    OrderRelation(
        SettingField.DSG_UNDER_TEMP, SettingField.DSG_UNDER_TEMP_RELEASE,
        "dsgUnderTempRelease must be higher than dsgUnderTemp"
    ),
    OrderRelation(
        SettingField.DSG_UNDER_TEMP, SettingField.DSG_OVER_TEMP,
        "dsgUnderTemp must be lower than dsgOverTemp"
    )
)

/** Fields whose current device value is needed to validate a change to [field]. */
fun relationCounterparts(field: SettingField): Set<SettingField> =
    ORDER_RELATIONS.mapNotNullTo(linkedSetOf()) { relation ->
        when (field) {
            relation.lower -> relation.upper
            relation.upper -> relation.lower
            else -> null
        }
    }

private fun firstRelationalFailure(
    field: SettingField,
    values: Map<SettingField, Int>
): String? {
    ORDER_RELATIONS.forEach { relation ->
        if (field != relation.lower && field != relation.upper) return@forEach
        val lowerValue = values[relation.lower] ?: return@forEach
        val upperValue = values[relation.upper] ?: return@forEach
        if (lowerValue >= upperValue) return relation.reason
    }
    return when (field) {
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
