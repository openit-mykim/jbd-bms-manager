package com.gytxtx.openjbd.maintenance

import java.io.File

/**
 * Append-only local audit log (one tab-separated line per entry). Local-first: never uploaded.
 * Kept bounded so a long-lived installation cannot grow it without limit.
 */
class FileMaintenanceAuditLog(
    private val file: File,
    private val clock: MaintenanceClock = MaintenanceClock { System.currentTimeMillis() },
    private val maxEntries: Int = 2_000
) : MaintenanceAuditLog {
    @Synchronized
    override fun append(record: MaintenanceAuditRecord): MaintenanceAuditEntry {
        val entry = MaintenanceAuditEntry(
            timestampEpochMillis = clock.nowEpochMillis(),
            deviceIdentifier = record.deviceIdentifier,
            bmsModel = record.bmsModel,
            firmwareVersion = record.firmwareVersion,
            fieldKey = record.fieldKey,
            previousValue = record.previousValue,
            newValue = record.newValue,
            outcome = record.outcome,
            verificationResult = record.verificationResult
        )
        file.parentFile?.mkdirs()
        file.appendText(encode(entry) + "\n")
        trimIfNeeded()
        return entry
    }

    @Synchronized
    override fun entries(): List<MaintenanceAuditEntry> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull(::decode)

    private fun trimIfNeeded() {
        val lines = file.readLines()
        if (lines.size > maxEntries) file.writeText(lines.takeLast(maxEntries).joinToString("\n", postfix = "\n"))
    }

    private fun encode(entry: MaintenanceAuditEntry): String = listOf(
        entry.timestampEpochMillis.toString(),
        entry.deviceIdentifier.address.orEmpty(),
        entry.deviceIdentifier.fingerprint.orEmpty(),
        entry.bmsModel.orEmpty(),
        entry.firmwareVersion.orEmpty(),
        entry.fieldKey,
        entry.previousValue,
        entry.newValue,
        entry.outcome.name,
        entry.verificationResult.orEmpty()
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    private fun decode(line: String): MaintenanceAuditEntry? {
        val parts = line.split('\t')
        if (parts.size != FIELD_COUNT) return null
        return MaintenanceAuditEntry(
            timestampEpochMillis = parts[0].toLongOrNull() ?: return null,
            deviceIdentifier = MaintenanceDeviceIdentifier(
                address = parts[1].ifEmpty { null },
                fingerprint = parts[2].ifEmpty { null }
            ),
            bmsModel = parts[3].ifEmpty { null },
            firmwareVersion = parts[4].ifEmpty { null },
            fieldKey = parts[5],
            previousValue = parts[6],
            newValue = parts[7],
            outcome = runCatching { MaintenanceAuditOutcome.valueOf(parts[8]) }.getOrNull() ?: return null,
            verificationResult = parts[9].ifEmpty { null }
        )
    }

    private companion object {
        const val FIELD_COUNT = 10
    }
}
