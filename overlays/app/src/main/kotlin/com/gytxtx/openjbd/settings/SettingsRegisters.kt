package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.protocol.JbdConfigCodec
import com.gytxtx.openjbd.protocol.ScaledInt16Codec

enum class SettingsGroup {
    BALANCE,
    PROTECTION,
    TEMPERATURE,
    CAPACITY,
    CALIBRATION
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

    /** Idle-current calibration accepts only an explicit zero-current raw value. */
    val CALIBRATION_IDLE_CURRENT = PhysicalRange(0, 0)

    /** Candidate current-calibration guard: 0.01..300 A in positive 10 mA units. */
    val CALIBRATION_CURRENT_10_MA = PhysicalRange(1, 30_000)
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
     * Candidate calibration registers from the community map cited by
     * `docs/protocol-design.md`; all remain hardware-unverified and capability-gated.
     */
    CAL_IDLE_CURRENT(
        "calIdleCurrent", SettingsGroup.CALIBRATION,
        RegisterSpec(0xAD, false, 1.0, "raw"), SettingsGuardRanges.CALIBRATION_IDLE_CURRENT
    ),
    CAL_CHARGE_CURRENT(
        "calChargeCurrent", SettingsGroup.CALIBRATION,
        RegisterSpec(0xAE, false, 10.0, "mA"), SettingsGuardRanges.CALIBRATION_CURRENT_10_MA
    ),
    CAL_DISCHARGE_CURRENT(
        "calDischargeCurrent", SettingsGroup.CALIBRATION,
        RegisterSpec(0xAF, false, 10.0, "mA"), SettingsGuardRanges.CALIBRATION_CURRENT_10_MA
    ),
    CAL_CELL_01("calCell01", SettingsGroup.CALIBRATION, RegisterSpec(0xB0, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_02("calCell02", SettingsGroup.CALIBRATION, RegisterSpec(0xB1, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_03("calCell03", SettingsGroup.CALIBRATION, RegisterSpec(0xB2, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_04("calCell04", SettingsGroup.CALIBRATION, RegisterSpec(0xB3, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_05("calCell05", SettingsGroup.CALIBRATION, RegisterSpec(0xB4, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_06("calCell06", SettingsGroup.CALIBRATION, RegisterSpec(0xB5, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_07("calCell07", SettingsGroup.CALIBRATION, RegisterSpec(0xB6, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_08("calCell08", SettingsGroup.CALIBRATION, RegisterSpec(0xB7, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_09("calCell09", SettingsGroup.CALIBRATION, RegisterSpec(0xB8, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_10("calCell10", SettingsGroup.CALIBRATION, RegisterSpec(0xB9, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_11("calCell11", SettingsGroup.CALIBRATION, RegisterSpec(0xBA, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_12("calCell12", SettingsGroup.CALIBRATION, RegisterSpec(0xBB, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_13("calCell13", SettingsGroup.CALIBRATION, RegisterSpec(0xBC, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_14("calCell14", SettingsGroup.CALIBRATION, RegisterSpec(0xBD, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_15("calCell15", SettingsGroup.CALIBRATION, RegisterSpec(0xBE, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_16("calCell16", SettingsGroup.CALIBRATION, RegisterSpec(0xBF, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_17("calCell17", SettingsGroup.CALIBRATION, RegisterSpec(0xC0, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_18("calCell18", SettingsGroup.CALIBRATION, RegisterSpec(0xC1, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_19("calCell19", SettingsGroup.CALIBRATION, RegisterSpec(0xC2, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_20("calCell20", SettingsGroup.CALIBRATION, RegisterSpec(0xC3, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_21("calCell21", SettingsGroup.CALIBRATION, RegisterSpec(0xC4, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_22("calCell22", SettingsGroup.CALIBRATION, RegisterSpec(0xC5, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_23("calCell23", SettingsGroup.CALIBRATION, RegisterSpec(0xC6, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_24("calCell24", SettingsGroup.CALIBRATION, RegisterSpec(0xC7, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_25("calCell25", SettingsGroup.CALIBRATION, RegisterSpec(0xC8, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_26("calCell26", SettingsGroup.CALIBRATION, RegisterSpec(0xC9, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_27("calCell27", SettingsGroup.CALIBRATION, RegisterSpec(0xCA, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_28("calCell28", SettingsGroup.CALIBRATION, RegisterSpec(0xCB, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_29("calCell29", SettingsGroup.CALIBRATION, RegisterSpec(0xCC, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_30("calCell30", SettingsGroup.CALIBRATION, RegisterSpec(0xCD, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_31("calCell31", SettingsGroup.CALIBRATION, RegisterSpec(0xCE, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_CELL_32("calCell32", SettingsGroup.CALIBRATION, RegisterSpec(0xCF, false, 1.0, "mV"), SettingsGuardRanges.CELL_THRESHOLD_MV),
    CAL_NTC_01("calNtc01", SettingsGroup.CALIBRATION, RegisterSpec(0xD0, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_02("calNtc02", SettingsGroup.CALIBRATION, RegisterSpec(0xD1, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_03("calNtc03", SettingsGroup.CALIBRATION, RegisterSpec(0xD2, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_04("calNtc04", SettingsGroup.CALIBRATION, RegisterSpec(0xD3, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_05("calNtc05", SettingsGroup.CALIBRATION, RegisterSpec(0xD4, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_06("calNtc06", SettingsGroup.CALIBRATION, RegisterSpec(0xD5, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_07("calNtc07", SettingsGroup.CALIBRATION, RegisterSpec(0xD6, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K),
    CAL_NTC_08("calNtc08", SettingsGroup.CALIBRATION, RegisterSpec(0xD7, false, 0.1, "K"), SettingsGuardRanges.TEMPERATURE_TENTH_K);

    init {
        require(bitIndex == null || bitIndex in 0..15) { "Bit index must be in 0..15" }
    }
}
