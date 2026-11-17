package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.maintenance.MaintenanceAuditEntry
import com.gytxtx.openjbd.maintenance.MaintenanceAuditOutcome
import com.gytxtx.openjbd.maintenance.MaintenanceDeviceIdentifier
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRecordsTest {
    private val a = "A4:C1:38:00:00:01"
    private val b = "A4:C1:38:00:00:B1"

    @Test
    fun backupsAreStoredPerDeviceNewestFirst() {
        val root = Files.createTempDirectory("backups").toFile()
        val store = DeviceBackupStore(root)
        store.save(a, "{\"n\":1}", 100L)
        store.save(a, "{\"n\":2}", 200L)
        store.save(b, "{\"n\":3}", 150L)
        assertEquals(listOf(200L, 100L), store.list(a).map { it.savedAtMillis })
        assertEquals("{\"n\":2}", store.latest(a.lowercase())!!.file.readText())
        assertEquals(listOf(150L), store.list(b).map { it.savedAtMillis })
        root.deleteRecursively()
    }

    @Test
    fun sameTimestampDoesNotOverwrite() {
        val root = Files.createTempDirectory("backups").toFile()
        val store = DeviceBackupStore(root)
        store.save(a, "1", 100L)
        store.save(a, "2", 100L)
        assertEquals(2, store.list(a).size)
        root.deleteRecursively()
    }

    @Test
    fun backupsAreBoundedPerDevice() {
        val root = Files.createTempDirectory("backups").toFile()
        val store = DeviceBackupStore(root, maxPerDevice = 3)
        (1..5).forEach { store.save(a, "$it", it * 10L) }
        assertEquals(listOf(50L, 40L, 30L), store.list(a).map { it.savedAtMillis })
        store.deleteDevice(a)
        assertNull(store.latest(a))
        root.deleteRecursively()
    }

    @Test
    fun serviceRecordsAreFilteredByDevice() {
        fun entry(address: String?) = MaintenanceAuditEntry(
            1L, MaintenanceDeviceIdentifier(address = address), null, null, "f", "", "", MaintenanceAuditOutcome.SUCCESS_VERIFIED
        )
        val entries = listOf(entry(a), entry(b), entry(a.lowercase()), entry(null))
        assertEquals(2, DeviceServiceRecords.forDevice(entries, a).size)
        assertEquals(1, DeviceServiceRecords.forDevice(entries, b).size)
        assertTrue(DeviceServiceRecords.forDevice(entries, "bad").isEmpty())
    }
}
