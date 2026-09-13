package com.gytxtx.openjbd.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class MaintenanceAuditLogTest {
    @Test
    fun appendUsesInjectedTimestampAndRecordsOutcome() {
        val log = InMemoryMaintenanceAuditLog(MaintenanceClock { 1_234L })

        val entry = log.append(record("cell-ovp", MaintenanceAuditOutcome.READ_BACK_MISMATCH))

        assertEquals(1_234L, entry.timestampEpochMillis)
        assertEquals(MaintenanceAuditOutcome.READ_BACK_MISMATCH, entry.outcome)
        assertEquals("AA:BB:CC:DD:EE:FF", entry.deviceIdentifier.address)
        assertEquals("4.250 V", entry.previousValue)
        assertEquals("4.200 V", entry.newValue)
        assertEquals("device returned 4.210 V", entry.verificationResult)
    }

    @Test
    fun entriesRemainInAppendOrderWithClockSequence() {
        val timestamps = ArrayDeque(listOf(20L, 10L))
        val log = InMemoryMaintenanceAuditLog(MaintenanceClock { timestamps.removeFirst() })

        log.append(record("first", MaintenanceAuditOutcome.SUCCESS_VERIFIED))
        log.append(record("second", MaintenanceAuditOutcome.WRITE_REJECTED))

        assertEquals(listOf("first", "second"), log.entries().map { it.fieldKey })
        assertEquals(listOf(20L, 10L), log.entries().map { it.timestampEpochMillis })
    }

    @Test
    fun entriesReturnsSnapshotAndTransactionOutcomeMapsForAudit() {
        val log = InMemoryMaintenanceAuditLog(MaintenanceClock { 9L })
        log.append(record("one", MaintenanceAuditOutcome.SUCCESS_VERIFIED))
        val firstSnapshot = log.entries()

        log.append(record("two", MaintenanceAuditOutcome.PARTIAL_APPLY))

        assertEquals(1, firstSnapshot.size)
        assertEquals(2, log.entries().size)
        assertNotSame(firstSnapshot, log.entries())
        assertEquals(
            MaintenanceAuditOutcome.PARTIAL_APPLY,
            MaintenanceTransactionResult.PartialApply(1, 2).auditOutcome()
        )
    }

    private fun record(fieldKey: String, outcome: MaintenanceAuditOutcome) =
        MaintenanceAuditRecord(
            deviceIdentifier = MaintenanceDeviceIdentifier(
                name = "Service BMS",
                address = "AA:BB:CC:DD:EE:FF",
                fingerprint = "fingerprint-placeholder"
            ),
            bmsModel = "SP14S004",
            firmwareVersion = "FW-2.1",
            fieldKey = fieldKey,
            previousValue = "4.250 V",
            newValue = "4.200 V",
            outcome = outcome,
            verificationResult = "device returned 4.210 V"
        )
}
