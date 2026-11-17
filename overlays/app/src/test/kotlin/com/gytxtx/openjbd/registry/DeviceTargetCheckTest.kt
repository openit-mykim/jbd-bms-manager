package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.maintenance.DeviceIdentity
import com.gytxtx.openjbd.registry.DeviceTargetCheck.BackupResult
import com.gytxtx.openjbd.registry.DeviceTargetCheck.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTargetCheckTest {
    private val selected = RegisteredBms("A4:C1:38:00:00:01", "Commuter", "SP14S004", 1L)
    private val learned = selected.copy(bmsModel = "SP14S004", firmwareVersion = "4.2")

    private fun identity(address: String = "A4:C1:38:00:00:01", model: String? = "SP14S004", fw: String = "4.2") =
        DeviceIdentity.from(address, model, fw)

    @Test
    fun noSelectionFailsClosed() = assertEquals(Result.NO_SELECTION, DeviceTargetCheck.check(null, identity()))

    @Test
    fun unreadIdentityFailsClosed() = assertEquals(Result.IDENTITY_UNREAD, DeviceTargetCheck.check(selected, null))

    @Test
    fun otherAddressFailsClosed() =
        assertEquals(Result.ADDRESS_MISMATCH, DeviceTargetCheck.check(learned, identity(address = "A4:C1:38:00:00:B1")))

    @Test
    fun addressMatchIsCaseInsensitive() =
        assertEquals(Result.MATCH, DeviceTargetCheck.check(learned, identity(address = "a4:c1:38:00:00:01")))

    @Test
    fun addressOnlyMatchesWhileModelNotLearned() =
        assertEquals(Result.MATCH, DeviceTargetCheck.check(selected, identity(model = null)))

    @Test
    fun learnedModelMustBeReadAndEqual() {
        assertEquals(Result.MODEL_UNREAD, DeviceTargetCheck.check(learned, identity(model = null)))
        assertEquals(Result.MODEL_MISMATCH, DeviceTargetCheck.check(learned, identity(model = "OTHER")))
        assertEquals(Result.MATCH, DeviceTargetCheck.check(learned, identity()))
    }

    @Test
    fun firmwareChangeAloneDoesNotBreakTargetMatch() =
        // Firmware-bound write consent is D020's job; the target identity is address + model.
        assertEquals(Result.MATCH, DeviceTargetCheck.check(learned, identity(fw = "5.0")))

    @Test
    fun backupMustBelongToSelectedDevice() {
        assertEquals(BackupResult.MATCH, DeviceTargetCheck.checkBackup(learned, "a4:c1:38:00:00:01", "SP14S004"))
        assertEquals(BackupResult.MATCH, DeviceTargetCheck.checkBackup(learned, "A4:C1:38:00:00:01", null))
        assertEquals(BackupResult.NO_SELECTION, DeviceTargetCheck.checkBackup(null, "A4:C1:38:00:00:01", null))
        assertEquals(BackupResult.NO_BACKUP_ADDRESS, DeviceTargetCheck.checkBackup(learned, "", null))
        assertEquals(BackupResult.ADDRESS_MISMATCH, DeviceTargetCheck.checkBackup(learned, "A4:C1:38:00:00:B1", "SP14S004"))
        assertEquals(BackupResult.MODEL_MISMATCH, DeviceTargetCheck.checkBackup(learned, "A4:C1:38:00:00:01", "OTHER"))
        assertEquals(BackupResult.MATCH, DeviceTargetCheck.checkBackup(selected, "A4:C1:38:00:00:01", "OTHER"))
    }
}
