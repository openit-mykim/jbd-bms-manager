package com.gytxtx.openjbd.balance

import com.gytxtx.openjbd.settings.FieldValue
import com.gytxtx.openjbd.settings.SettingField
import com.gytxtx.openjbd.settings.SettingsGroup
import com.gytxtx.openjbd.settings.SettingsSectionState
import com.gytxtx.openjbd.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Test

class CellThresholdResolverTest {
    @Test
    fun bothProtectionValuesResolveMeasuredThresholds() {
        val result = CellThresholdResolver.from(stateWith(covp = 4_180, cuvp = 2_950))

        assertEquals(CellThresholds(4_180, 2_950, ThresholdSource.MEASURED), result)
    }

    @Test
    fun missingOneProtectionValueUsesDefaultThresholds() {
        assertEquals(CellThresholds.nmcDefault(), CellThresholdResolver.from(stateWith(covp = 4_180)))
        assertEquals(CellThresholds.nmcDefault(), CellThresholdResolver.from(stateWith(cuvp = 2_950)))
    }

    @Test
    fun disconnectedStateUsesDefaultThresholds() {
        val state = stateWith(covp = 4_180, cuvp = 2_950).copy(notConnected = true)

        assertEquals(CellThresholds.nmcDefault(), CellThresholdResolver.from(state))
    }

    @Test
    fun loadingProtectionSectionUsesDefaultThresholds() {
        val state = stateWith(covp = 4_180, cuvp = 2_950, loading = true)

        assertEquals(CellThresholds.nmcDefault(), CellThresholdResolver.from(state))
    }

    @Test
    fun zeroProtectionValueUsesDefaultThresholds() {
        assertEquals(
            CellThresholds.nmcDefault(),
            CellThresholdResolver.from(stateWith(covp = 0, cuvp = 2_950))
        )
    }

    @Test
    fun invertedProtectionValuesUseDefaultThresholds() {
        assertEquals(
            CellThresholds.nmcDefault(),
            CellThresholdResolver.from(stateWith(covp = 2_900, cuvp = 3_000))
        )
    }

    private fun stateWith(
        covp: Int? = null,
        cuvp: Int? = null,
        loading: Boolean = false
    ): SettingsState {
        val values = buildMap {
            covp?.let { put(SettingField.COVP, FieldValue(raw = it, physicalValue = it.toDouble())) }
            cuvp?.let { put(SettingField.CUVP, FieldValue(raw = it, physicalValue = it.toDouble())) }
        }
        return SettingsState(
            sections = SettingsGroup.values().associateWith { group ->
                if (group == SettingsGroup.PROTECTION) {
                    SettingsSectionState(values = values, loading = loading)
                } else {
                    SettingsSectionState()
                }
            },
            notConnected = false
        )
    }
}
