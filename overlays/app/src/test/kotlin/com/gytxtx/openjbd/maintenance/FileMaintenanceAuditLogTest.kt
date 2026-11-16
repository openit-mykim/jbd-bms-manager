package com.gytxtx.openjbd.maintenance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FileMaintenanceAuditLogTest {
    @Test
    fun entriesRoundTripAndAreBounded() {
        val file = File.createTempFile("audit", ".log").apply { delete(); deleteOnExit() }
        var now = 0L
        val log = FileMaintenanceAuditLog(file, { now }, maxEntries = 3)
        repeat(5) { index ->
            now = index.toLong()
            log.append(
                MaintenanceAuditRecord(
                    deviceIdentifier = MaintenanceDeviceIdentifier(address = "AA", fingerprint = "AA|m|1"),
                    bmsModel = "m",
                    firmwareVersion = "1",
                    fieldKey = "covp",
                    previousValue = "4200",
                    newValue = "41\t00",
                    outcome = MaintenanceAuditOutcome.SUCCESS_VERIFIED,
                    verificationResult = "session=COMMITTED"
                )
            )
        }

        val reopened = FileMaintenanceAuditLog(file).entries()
        assertEquals(listOf(2L, 3L, 4L), reopened.map { it.timestampEpochMillis })
        assertEquals("41 00", reopened.last().newValue)
        assertEquals("AA|m|1", reopened.last().deviceIdentifier.fingerprint)
    }
}
