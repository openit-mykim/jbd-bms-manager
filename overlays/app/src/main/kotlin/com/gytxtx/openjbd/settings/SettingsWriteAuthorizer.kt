package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.MaintenanceAuditOutcome
import com.gytxtx.openjbd.maintenance.MaintenanceAuditRecord
import com.gytxtx.openjbd.maintenance.MaintenanceDeviceIdentifier
import com.gytxtx.openjbd.maintenance.WriteDecision

/** Decides, at the moment of each write session, whether BMS writes are permitted. */
interface SettingsWriteAuthorizer {
    fun authorize(): WriteDecision

    /** Called after every permitted session so the outcome lands in the audit log. */
    fun recordSession(decision: WriteDecision.Allowed, result: WriteSessionResult)
}

/** Maps one write session to audit records: one per attempted field, or one session record. */
object WriteSessionAudit {
    fun records(decision: WriteDecision.Allowed, result: WriteSessionResult): List<MaintenanceAuditRecord> {
        val device = MaintenanceDeviceIdentifier(
            address = decision.identity.address,
            fingerprint = decision.identity.fingerprint
        )
        val verification = "session=${outcomeKey(result.outcome)}; basis=${decision.basis.name}"
        val fieldRecords = result.changes.mapNotNull { change ->
            val outcome = outcomeFor(change.detail) ?: return@mapNotNull null
            MaintenanceAuditRecord(
                deviceIdentifier = device,
                bmsModel = decision.identity.bmsModel,
                firmwareVersion = decision.identity.firmwareVersion,
                fieldKey = change.change.field.key,
                previousValue = (change.detail as? ChangeDetail.Verified)
                    ?.previousRegisterRaw?.toString().orEmpty(),
                newValue = change.change.rawValue.toString(),
                outcome = outcome,
                verificationResult = verification
            )
        }
        if (fieldRecords.isNotEmpty()) return fieldRecords
        return listOf(
            MaintenanceAuditRecord(
                deviceIdentifier = device,
                bmsModel = decision.identity.bmsModel,
                firmwareVersion = decision.identity.firmwareVersion,
                fieldKey = SESSION_FIELD_KEY,
                previousValue = "",
                newValue = "",
                outcome = when (result.outcome) {
                    is WriteSessionOutcome.ValidationFailed -> MaintenanceAuditOutcome.UNSUPPORTED
                    is WriteSessionOutcome.EntryFailed -> MaintenanceAuditOutcome.TRANSPORT_DISCONNECTED
                    else -> MaintenanceAuditOutcome.PARTIAL_APPLY
                },
                verificationResult = verification
            )
        )
    }

    fun outcomeFor(detail: ChangeDetail): MaintenanceAuditOutcome? = when (detail) {
        is ChangeDetail.Verified, is ChangeDetail.Acknowledged -> MaintenanceAuditOutcome.SUCCESS_VERIFIED
        is ChangeDetail.ReadBackMismatch -> MaintenanceAuditOutcome.READ_BACK_MISMATCH
        is ChangeDetail.NoResponse -> MaintenanceAuditOutcome.TIMEOUT
        is ChangeDetail.ErrorStatus -> MaintenanceAuditOutcome.WRITE_REJECTED
        is ChangeDetail.TransportFailure -> MaintenanceAuditOutcome.TRANSPORT_DISCONNECTED
        is ChangeDetail.MalformedResponse -> MaintenanceAuditOutcome.MALFORMED_RESPONSE
        ChangeDetail.SkippedNoChange -> null
    }

    private fun outcomeKey(outcome: WriteSessionOutcome): String = when (outcome) {
        WriteSessionOutcome.Committed -> "COMMITTED"
        WriteSessionOutcome.NotCommitted -> "NOT_COMMITTED"
        WriteSessionOutcome.Applied -> "APPLIED"
        is WriteSessionOutcome.CommitUnconfirmed -> "COMMIT_UNCONFIRMED"
        is WriteSessionOutcome.ValidationFailed -> "VALIDATION_FAILED"
        is WriteSessionOutcome.EntryFailed -> "ENTRY_FAILED"
        is WriteSessionOutcome.NotPermitted -> "NOT_PERMITTED"
    }

    const val SESSION_FIELD_KEY = "session"
}
