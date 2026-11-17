package com.gytxtx.openjbd.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsRegistryTest {
    private val a = "A4:C1:38:00:00:01"
    private val b = "A4:C1:38:00:00:B1"

    private fun RegistryEdit.registry(): BmsRegistry = (this as RegistryEdit.Changed).registry

    private fun two(): BmsRegistry = BmsRegistry()
        .register(a, "SP14S004", 1L).registry()
        .register(b, "SP14S005", 2L).registry()

    @Test
    fun registerNormalizesAddressAndDefaultsAliasToAdvertisedName() {
        val registry = BmsRegistry().register(" a4:c1:38:00:00:01 ", "SP14S004", 5L).registry()
        val device = registry.devices.single()
        assertEquals(a, device.address)
        assertEquals("SP14S004", device.alias)
        assertEquals(5L, device.registeredAtMillis)
        assertNull(registry.selectedAddress)
    }

    @Test
    fun registerWithoutNameUsesAddressAsDisplayName() {
        val device = BmsRegistry().register(a, null, 1L).registry().devices.single()
        assertEquals(a, device.displayName)
    }

    @Test
    fun registerRejectsInvalidAddress() {
        assertEquals(
            RegistryEdit.Rejected(RegistryEdit.Reason.INVALID_ADDRESS),
            BmsRegistry().register("not-a-mac", "x", 1L)
        )
    }

    @Test
    fun registeringAgainKeepsAliasAndRegistrationTime() {
        val renamed = two().rename(a, "Commuter 48V").registry()
        val again = renamed.register(a.lowercase(), "SP14S004-new", 99L).registry()
        assertEquals(2, again.devices.size)
        val device = again.find(a)!!
        assertEquals("Commuter 48V", device.alias)
        assertEquals("SP14S004-new", device.bleName)
        assertEquals(1L, device.registeredAtMillis)
    }

    @Test
    fun selectOnlyRegisteredDevices() {
        val registry = two()
        assertEquals(b, registry.select(b.lowercase()).registry().selectedAddress)
        assertEquals(
            RegistryEdit.Rejected(RegistryEdit.Reason.UNKNOWN_DEVICE),
            registry.select("00:00:00:00:00:00")
        )
    }

    @Test
    fun renameTrimsAndBlankFallsBackToAdvertisedName() {
        val registry = two()
        assertEquals("Spare", registry.rename(b, "  Spare ").registry().find(b)!!.alias)
        assertEquals("SP14S005", registry.rename(b, "   ").registry().find(b)!!.alias)
    }

    @Test
    fun renameRejectsTooLongAliasInsteadOfTruncating() {
        assertEquals(
            RegistryEdit.Rejected(RegistryEdit.Reason.ALIAS_TOO_LONG),
            two().rename(a, "x".repeat(BmsRegistry.MAX_ALIAS_LENGTH + 1))
        )
        assertEquals(32, two().rename(a, "x".repeat(32)).registry().find(a)!!.alias.length)
    }

    @Test
    fun removingSelectedDeviceLeavesNothingSelected() {
        val registry = two().select(a).registry().remove(a).registry()
        assertEquals(listOf(b), registry.devices.map { it.address })
        assertNull(registry.selectedAddress)
    }

    @Test
    fun removingOtherDeviceKeepsSelection() {
        val registry = two().select(a).registry().remove(b).registry()
        assertEquals(a, registry.selectedAddress)
    }

    @Test
    fun identityModelIsLearnedOnceAndNeverOverwritten() {
        val learned = two().learnIdentity(a, "SP14S004", "4.2").registry()
        assertEquals("SP14S004", learned.find(a)!!.bmsModel)
        assertEquals("4.2", learned.find(a)!!.firmwareVersion)
        val firmwareUpdate = learned.learnIdentity(a, "SP14S004", "4.3").registry()
        assertEquals("4.3", firmwareUpdate.find(a)!!.firmwareVersion)
        val otherBoard = firmwareUpdate.learnIdentity(a, "OTHER", "1.0").registry()
        assertEquals("SP14S004", otherBoard.find(a)!!.bmsModel)
        assertEquals("4.3", otherBoard.find(a)!!.firmwareVersion)
    }

    @Test
    fun firmwareAloneIsRecordedBeforeModelIsRead() {
        val partial = two().learnIdentity(a, null, "4.2").registry()
        assertNull(partial.find(a)!!.bmsModel)
        assertEquals("4.2", partial.find(a)!!.firmwareVersion)
        assertEquals("SP14S004", partial.learnIdentity(a, "SP14S004", "4.2").registry().find(a)!!.bmsModel)
    }

    @Test
    fun recordConnectedStoresTime() {
        assertEquals(77L, two().recordConnected(b, 77L).registry().find(b)!!.lastConnectedAtMillis)
    }

    @Test
    fun codecRoundTripsAllFields() {
        val registry = two().select(b).registry()
            .rename(b, "예비 배터리 \"B\"").registry()
            .recordConnected(b, 123L).registry()
            .learnIdentity(b, "SP14S005", "4.1").registry()
        assertEquals(registry, BmsRegistryCodec.decode(BmsRegistryCodec.encode(registry)))
    }

    @Test
    fun codecReturnsNullForCorruptOrUnknownVersion() {
        assertNull(BmsRegistryCodec.decode(null))
        assertNull(BmsRegistryCodec.decode("{not json"))
        assertNull(BmsRegistryCodec.decode("""{"v":99,"devices":[]}"""))
        assertNull(BmsRegistryCodec.decode("[]"))
    }

    @Test
    fun codecSkipsInvalidDevicesAndDropsDanglingSelection() {
        val decoded = BmsRegistryCodec.decode(
            """{"v":1,"selected":"11:22:33:44:55:66","devices":[{"address":"bad"},{"address":"$a","alias":"x"},{"address":"$a"}],"extra":1}"""
        )!!
        assertEquals(listOf(a), decoded.devices.map { it.address })
        assertNull(decoded.selectedAddress)
    }

    @Test
    fun legacySingleDeviceMigratesToSelectedRegisteredDevice() {
        val migrated = LegacyDeviceMigration.migrate(null, "a4:c1:38:00:00:01", "SP14S004", 10L)
        assertEquals(a, migrated.selectedAddress)
        assertEquals("SP14S004", migrated.selected!!.alias)
    }

    @Test
    fun legacyNameEqualToAddressIsNotUsedAsAlias() {
        val migrated = LegacyDeviceMigration.migrate(null, a, a, 10L)
        assertEquals("", migrated.selected!!.bleName)
        assertEquals(a, migrated.selected!!.displayName)
    }

    @Test
    fun migrationWithoutLegacyDeviceIsEmptyAndStoredRegistryWins() {
        assertTrue(LegacyDeviceMigration.migrate(null, "", null, 1L).devices.isEmpty())
        assertTrue(LegacyDeviceMigration.migrate(null, "garbage", "x", 1L).devices.isEmpty())
        val stored = two()
        assertEquals(stored, LegacyDeviceMigration.migrate(stored, a, "ignored", 1L))
    }
}
