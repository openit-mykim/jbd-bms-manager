package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MosStateMappingTest {
    @Test
    fun mosFieldsUseRegisterE1DisableBits() {
        assertEquals(0xE1, SettingField.MOS_CHARGE_DISABLE.register.address)
        assertEquals(0, SettingField.MOS_CHARGE_DISABLE.bitIndex)
        assertEquals(0xE1, SettingField.MOS_DISCHARGE_DISABLE.register.address)
        assertEquals(1, SettingField.MOS_DISCHARGE_DISABLE.bitIndex)
    }

    @Test
    fun disableRawOneMeansBlockedAndZeroMeansAllowed() {
        assertTrue(MosStateMapping.isDisabled(1))
        assertFalse(MosStateMapping.isDisabled(0))
        assertEquals(0, MosStateMapping.disableRawForAllowed(true))
        assertEquals(1, MosStateMapping.disableRawForAllowed(false))
    }

    @Test
    fun basicInfoFetBitsSetMeanConducting() {
        assertTrue(MosStateMapping.isConducting(0b01, 0))
        assertFalse(MosStateMapping.isConducting(0b01, 1))
        assertFalse(MosStateMapping.isConducting(0b10, 0))
        assertTrue(MosStateMapping.isConducting(0b10, 1))
    }

    @Test
    fun mosDisplayUsesBlockedAllowedResources() {
        assertEquals(
            SettingsDisplayText.Resource(R.string.settings_value_blocked),
            SettingsDisplay.displayValue(SettingField.MOS_CHARGE_DISABLE, 1).text
        )
        assertEquals(
            SettingsDisplayText.Resource(R.string.settings_value_allowed),
            SettingsDisplay.displayValue(SettingField.MOS_DISCHARGE_DISABLE, 0).text
        )
    }

    @Test
    fun mosFieldsAndGroupHaveLocalizedLabelMappings() {
        assertEquals(
            R.string.settings_field_mos_charge_disable,
            SettingsDisplay.fieldLabelResId(SettingField.MOS_CHARGE_DISABLE)
        )
        assertEquals(
            R.string.settings_field_mos_discharge_disable,
            SettingsDisplay.fieldLabelResId(SettingField.MOS_DISCHARGE_DISABLE)
        )
        assertEquals(R.string.control_section_mos, SettingsDisplay.groupTitleResId(SettingsGroup.MOS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDisableRawIsRejected() {
        MosStateMapping.isDisabled(2)
    }
}
