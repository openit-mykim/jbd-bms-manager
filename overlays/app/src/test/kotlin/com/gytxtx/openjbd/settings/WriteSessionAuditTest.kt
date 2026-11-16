package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.DeviceIdentity
import com.gytxtx.openjbd.maintenance.MaintenanceAuditOutcome
import com.gytxtx.openjbd.maintenance.WriteDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class WriteSessionAuditTest {
    private val decision = WriteDecision.Allowed(
        WriteDecision.Basis.EXPERIMENTAL_CONSENT,
        DeviceIdentity("AA", "m", "1")
    )

    @Test
    fun eachAttemptedFieldIsRecordedAndSkipsAreNot() {
        val result = WriteSessionResult(
            outcome = WriteSessionOutcome.NotCommitted,
            changes = listOf(
                FieldChangeResult(FieldChange(SettingField.COVP, 4_100), ChangeDetail.Verified(4_200, 4_100)),
                FieldChangeResult(FieldChange(SettingField.CUVP, 2_800), ChangeDetail.ReadBackMismatch(2_800, 2_700)),
                FieldChangeResult(FieldChange(SettingField.BAL_WINDOW, 10), ChangeDetail.SkippedNoChange)
            )
        )

        val records = WriteSessionAudit.records(decision, result)

        assertEquals(listOf("covp", "cuvp"), records.map { it.fieldKey })
        assertEquals("4200", records[0].previousValue)
        assertEquals(MaintenanceAuditOutcome.READ_BACK_MISMATCH, records[1].outcome)
        assertEquals("session=NOT_COMMITTED; basis=EXPERIMENTAL_CONSENT", records[0].verificationResult)
    }

    @Test
    fun sessionWithoutFieldResultsIsStillRecorded() {
        val records = WriteSessionAudit.records(
            decision,
            WriteSessionResult(outcome = WriteSessionOutcome.ValidationFailed(emptyList()))
        )
        assertEquals(WriteSessionAudit.SESSION_FIELD_KEY, records.single().fieldKey)
    }
}
