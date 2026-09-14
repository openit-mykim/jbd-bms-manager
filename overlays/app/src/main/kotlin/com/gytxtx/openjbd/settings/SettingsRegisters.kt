package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdConfigCodec
import com.gytxtx.openjbd.protocol.ScaledInt16Codec

enum class SettingsGroup {
    BALANCE,
    PROTECTION,
    TEMPERATURE,
    CAPACITY,
    MOS
}

data class RegisterSpec(
    val address: Int,
    val signed: Boolean,
    val scale: Double,
    val unit: String
) {
    init {
        require(address in 0x00..0xFF) { "Register address must fit in one byte" }
    }

    val codec: ScaledInt16Codec = if (signed) {
        JbdConfigCodec.s16(scale, unit)
    } else {
        JbdConfigCodec.u16(scale, unit)
    }
}

/**
 * Conservative typo/unit guard in raw register units. These bounds are application safety
 * checks, not claims about the limits of any JBD hardware variant.
 */
data class PhysicalRange(val minRaw: Int, val maxRaw: Int) {
    init {
        require(minRaw <= maxRaw) { "Physical range minimum must not exceed maximum" }
    }

    operator fun contains(rawValue: Int): Boolean = rawValue in minRaw..maxRaw
}

object SettingsGuardRanges {
    /** Candidate cell protection threshold guard: 2,000..4,500 mV. */
    val CELL_THRESHOLD_MV = PhysicalRange(2_000, 4_500)

    /** Candidate balancing-start guard: 2,000..4,500 mV. */
    val BALANCE_START_MV = PhysicalRange(2_000, 4_500)

    /** Candidate balancing-window guard: 5..1,000 mV. */
    val BALANCE_WINDOW_MV = PhysicalRange(5, 1_000)

    /** Candidate pack-threshold guard: raw 1,000..9,000 in 10 mV units (10..90 V). */
    val PACK_THRESHOLD_10_MV = PhysicalRange(1_000, 9_000)

    /** Candidate temperature guard: raw 2,200..3,760 in 0.1 K units. */
    val TEMPERATURE_TENTH_K = PhysicalRange(2_200, 3_760)

    /** Candidate charge-current guard: raw 100..30,000 in positive 10 mA units. */
    val CHARGE_CURRENT_10_MA = PhysicalRange(100, 30_000)

    /** Candidate discharge-current guard: raw -30,000..-100 in signed 10 mA units. */
    val DISCHARGE_CURRENT_10_MA = PhysicalRange(-30_000, -100)

    /**
     * Candidate design-capacity guard in 10 mAh units. The community range is wider, but this
     * layer caps it at U16's representable maximum so every locally valid value is encodable.
     */
    val DESIGN_CAPACITY_10_MAH = PhysicalRange(100, 0xFFFF)

    /** Boolean bitfield value guard. */
    val BOOLEAN_BIT = PhysicalRange(0, 1)
}

private val FUNCTION_CONFIG = RegisterSpec(0x2D, signed = false, scale = 1.0, unit = "bit")

/**
 * Candidate EEPROM fields from `docs/protocol-design.md`, accessed 2026-09-14. The mappings are
 * community-sourced and remain capability-gated until exact hardware/firmware validation.
 * Community sources disagree on the signedness of 0x2A/0x2B (bms-tools uses S16/U16, while
 * jbdtool uses unsigned/signed). Values are positive within the guard ranges in practice and
 * verification compares raw bytes, so this affects only display/validation; the current mapping
 * is retained deliberately.
 */
enum class SettingField(
    val key: String,
    val group: SettingsGroup,
    val register: RegisterSpec,
    val range: PhysicalRange,
    val bitIndex: Int? = null,
    val labelKey: String = "settings.field.$key"
) {
    BAL_START(
        "balStart", SettingsGroup.BALANCE,
        RegisterSpec(0x2A, true, 1.0, "mV"), SettingsGuardRanges.BALANCE_START_MV
    ),
    BAL_WINDOW(
        "balWindow", SettingsGroup.BALANCE,
        RegisterSpec(0x2B, false, 1.0, "mV"), SettingsGuardRanges.BALANCE_WINDOW_MV
    ),
    BALANCE_ENABLE(
        "balanceEnable", SettingsGroup.BALANCE, FUNCTION_CONFIG,
        SettingsGuardRanges.BOOLEAN_BIT, bitIndex = 2
    ),
    CHARGE_BALANCE_ENABLE(
        "chargeBalanceEnable", SettingsGroup.BALANCE, FUNCTION_CONFIG,
        SettingsGuardRanges.BOOLEAN_BIT, bitIndex = 3
    ),

    COVP(
        "covp", SettingsGroup.PROTECTION,
        RegisterSpec(0x24, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV
    ),
    COVP_RELEASE(
        "covpRelease", SettingsGroup.PROTECTION,
        RegisterSpec(0x25, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV
    ),
    CUVP(
        "cuvp", SettingsGroup.PROTECTION,
        RegisterSpec(0x26, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV
    ),
    CUVP_RELEASE(
        "cuvpRelease", SettingsGroup.PROTECTION,
        RegisterSpec(0x27, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV
    ),
    POVP(
        "povp", SettingsGroup.PROTECTION,
        RegisterSpec(0x20, false, 10.0, "mV"), SettingsGuardRanges.PACK_THRESHOLD_10_MV
    ),
    POVP_RELEASE(
        "povpRelease", SettingsGroup.PROTECTION,
        RegisterSpec(0x21, false, 10.0, "mV"), SettingsGuardRanges.PACK_THRESHOLD_10_MV
    ),
    PUVP(
        "puvp", SettingsGroup.PROTECTION,
        RegisterSpec(0x22, false, 10.0, "mV"), SettingsGuardRanges.PACK_THRESHOLD_10_MV
    ),
    PUVP_RELEASE(
        "puvpRelease", SettingsGroup.PROTECTION,
        RegisterSpec(0x23, false, 10.0, "mV"), SettingsGuardRanges.PACK_THRESHOLD_10_MV
    ),
    CHG_OVERCURRENT(
        "chgOvercurrent", SettingsGroup.PROTECTION,
        RegisterSpec(0x28, true, 10.0, "mA"), SettingsGuardRanges.CHARGE_CURRENT_10_MA
    ),
    DSG_OVERCURRENT(
        "dsgOvercurrent", SettingsGroup.PROTECTION,
        RegisterSpec(0x29, true, 10.0, "mA"), SettingsGuardRanges.DISCHARGE_CURRENT_10_MA
    ),

    CHG_OVER_TEMP(
        "chgOverTemp", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x18, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    CHG_OVER_TEMP_RELEASE(
        "chgOverTempRelease", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x19, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    CHG_UNDER_TEMP(
        "chgUnderTemp", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1A, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    CHG_UNDER_TEMP_RELEASE(
        "chgUnderTempRelease", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1B, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    DSG_OVER_TEMP(
        "dsgOverTemp", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1C, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    DSG_OVER_TEMP_RELEASE(
        "dsgOverTempRelease", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1D, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    DSG_UNDER_TEMP(
        "dsgUnderTemp", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1E, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),
    DSG_UNDER_TEMP_RELEASE(
        "dsgUnderTempRelease", SettingsGroup.TEMPERATURE,
        RegisterSpec(0x1F, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K
    ),

    DESIGN_CAPACITY(
        "designCapacity", SettingsGroup.CAPACITY,
        RegisterSpec(0x10, false, 10.0, "mAh"), SettingsGuardRanges.DESIGN_CAPACITY_10_MAH
    ),

    /**
     * MOSFET control register 0xE1 per the community register map, independently matched by
     * jbdtool and SmartBMSUtility: bit 0 disables charge and bit 1 disables discharge. These
     * disable bits are inverted from basic-info FET status, where a set bit means conducting.
     */
    MOS_CHARGE_DISABLE(
        "mosChargeDisable", SettingsGroup.MOS,
        RegisterSpec(0xE1, false, 1.0, "bit"), SettingsGuardRanges.BOOLEAN_BIT, bitIndex = 0
    ),
    MOS_DISCHARGE_DISABLE(
        "mosDischargeDisable", SettingsGroup.MOS,
        RegisterSpec(0xE1, false, 1.0, "bit"), SettingsGuardRanges.BOOLEAN_BIT, bitIndex = 1
    );

    init {
        require(bitIndex == null || bitIndex in 0..15) { "Bit index must be in 0..15" }
    }
}
