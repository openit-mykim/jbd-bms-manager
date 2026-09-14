package com.gytxtx.openjbd.balance

import com.gytxtx.openjbd.settings.SettingField
import com.gytxtx.openjbd.settings.SettingsGroup
import com.gytxtx.openjbd.settings.SettingsState
import kotlin.math.roundToInt

enum class CellSafetyBand {
    NORMAL,
    CAUTION,
    DANGER
}

enum class ThresholdSource {
    MEASURED,
    DEFAULT
}

data class CellThresholds(
    val overVoltageMv: Int,
    val underVoltageMv: Int,
    val source: ThresholdSource
) {
    companion object {
        /**
         * Conservative NMC display reference documented in `docs/ui-feature-proposal.md` and
         * the protection example in `docs/maintenance-mode.md`. This fallback is diagnostic
         * guidance only; it does not describe or guarantee the connected BMS protection action.
         */
        fun nmcDefault() = CellThresholds(
            overVoltageMv = 4_250,
            underVoltageMv = 2_800,
            source = ThresholdSource.DEFAULT
        )
    }
}

fun bandOf(voltageMv: Int, thresholds: CellThresholds): CellSafetyBand = when {
    voltageMv >= thresholds.overVoltageMv || voltageMv <= thresholds.underVoltageMv ->
        CellSafetyBand.DANGER
    thresholds.overVoltageMv - voltageMv <= CAUTION_MARGIN_MV ||
        voltageMv - thresholds.underVoltageMv <= CAUTION_MARGIN_MV -> CellSafetyBand.CAUTION
    else -> CellSafetyBand.NORMAL
}

/** Converts volts to the nearest whole millivolt, with half values rounded toward positive infinity. */
fun voltsToMillivolts(voltage: Float): Int = (voltage * MILLIVOLTS_PER_VOLT).roundToInt()

object CellThresholdResolver {
    fun from(state: SettingsState): CellThresholds {
        if (state.notConnected) return CellThresholds.nmcDefault()

        val protection = state.sections[SettingsGroup.PROTECTION]
            ?: return CellThresholds.nmcDefault()
        if (protection.loading) return CellThresholds.nmcDefault()

        val overVoltageMv = protection.values[SettingField.COVP]?.raw
            ?: return CellThresholds.nmcDefault()
        val underVoltageMv = protection.values[SettingField.CUVP]?.raw
            ?: return CellThresholds.nmcDefault()
        if (overVoltageMv !in SettingField.COVP.range ||
            underVoltageMv !in SettingField.CUVP.range ||
            underVoltageMv >= overVoltageMv
        ) {
            return CellThresholds.nmcDefault()
        }

        return CellThresholds(
            overVoltageMv = overVoltageMv,
            underVoltageMv = underVoltageMv,
            source = ThresholdSource.MEASURED
        )
    }
}

private const val CAUTION_MARGIN_MV = 50
private const val MILLIVOLTS_PER_VOLT = 1_000f
