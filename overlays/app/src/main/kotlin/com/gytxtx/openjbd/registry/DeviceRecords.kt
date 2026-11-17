package com.gytxtx.openjbd.registry

import com.gytxtx.openjbd.maintenance.MaintenanceAuditEntry
import java.io.File

/**
 * Phase D: configuration backups kept on the phone, one folder per registered BMS
 * (`files/backups/<device key>/`). The exported user file is unchanged; this copy is what
 * "the backups of this BMS" means inside the app. Bounded per device; never uploaded.
 */
class DeviceBackupStore(
    private val root: File,
    private val maxPerDevice: Int = MAX_PER_DEVICE
) {
    data class SavedBackup(val file: File, val savedAtMillis: Long)

    @Synchronized
    fun save(deviceKey: String, json: String, nowMillis: Long): SavedBackup {
        val dir = folder(deviceKey)
        dir.mkdirs()
        var stamp = nowMillis
        var file = File(dir, "$stamp$SUFFIX")
        while (file.exists()) { stamp += 1; file = File(dir, "$stamp$SUFFIX") }
        val temp = File(dir, "$stamp$SUFFIX.tmp")
        temp.writeText(json, Charsets.UTF_8)
        check(temp.renameTo(file)) { "Could not store backup" }
        list(deviceKey).drop(maxPerDevice).forEach { it.file.delete() }
        return SavedBackup(file, stamp)
    }

    /** Newest first. Files that do not follow the naming scheme are ignored. */
    @Synchronized
    fun list(deviceKey: String): List<SavedBackup> =
        folder(deviceKey).listFiles().orEmpty()
            .mapNotNull { file ->
                if (!file.isFile || !file.name.endsWith(SUFFIX)) return@mapNotNull null
                file.name.removeSuffix(SUFFIX).toLongOrNull()?.let { SavedBackup(file, it) }
            }
            .sortedByDescending { it.savedAtMillis }

    fun latest(deviceKey: String): SavedBackup? = list(deviceKey).firstOrNull()

    @Synchronized
    fun deleteDevice(deviceKey: String) {
        folder(deviceKey).deleteRecursively()
    }

    private fun folder(deviceKey: String): File {
        val key = requireNotNull(DeviceKeys.normalize(deviceKey)) { "Invalid device key" }
        return File(root, key.replace(':', '-'))
    }

    companion object {
        const val MAX_PER_DEVICE = 20
        private const val SUFFIX = ".json"
    }
}

/**
 * Phase D: calibration / service records are the maintenance audit entries (D020), which are
 * already bound to the device address and fingerprint. This selects the records of one BMS.
 */
object DeviceServiceRecords {
    fun forDevice(entries: List<MaintenanceAuditEntry>, deviceKey: String): List<MaintenanceAuditEntry> {
        val key = DeviceKeys.normalize(deviceKey) ?: return emptyList()
        return entries.filter { DeviceKeys.normalize(it.deviceIdentifier.address) == key }
    }
}
