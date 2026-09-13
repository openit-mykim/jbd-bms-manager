package com.gytxtx.openjbd.maintenance

data class MaintenanceDeviceIdentifier(
    val name: String? = null,
    val address: String? = null,
    val fingerprint: String? = null
)

enum class MaintenanceAuditOutcome {
    SUCCESS_VERIFIED,
    WRITE_REJECTED,
    TRANSPORT_DISCONNECTED,
    TIMEOUT,
    MALFORMED_RESPONSE,
    UNSUPPORTED,
    READ_BACK_MISMATCH,
    PARTIAL_APPLY
}

data class MaintenanceAuditRecord(
    val deviceIdentifier: MaintenanceDeviceIdentifier,
    val bmsModel: String?,
    val firmwareVersion: String?,
    val fieldKey: String,
    val previousValue: String,
    val newValue: String,
    val outcome: MaintenanceAuditOutcome,
    val verificationResult: String? = null
) {
    init {
        require(fieldKey.isNotBlank()) { "fieldKey must not be blank" }
    }
}

data class MaintenanceAuditEntry(
    val timestampEpochMillis: Long,
    val deviceIdentifier: MaintenanceDeviceIdentifier,
    val bmsModel: String?,
    val firmwareVersion: String?,
    val fieldKey: String,
    val previousValue: String,
    val newValue: String,
    val outcome: MaintenanceAuditOutcome,
    val verificationResult: String? = null
)

fun interface MaintenanceClock {
    fun nowEpochMillis(): Long
}

interface MaintenanceAuditLog {
    /** Appends a record using the log's clock and returns the immutable stored entry. */
    fun append(record: MaintenanceAuditRecord): MaintenanceAuditEntry

    /** Entries in append order. Persistent implementations must preserve this ordering contract. */
    fun entries(): List<MaintenanceAuditEntry>
}

/** Local in-memory implementation; persistent local storage can implement the same interface. */
class InMemoryMaintenanceAuditLog(
    private val clock: MaintenanceClock = MaintenanceClock { System.currentTimeMillis() }
) : MaintenanceAuditLog {
    private val storedEntries = mutableListOf<MaintenanceAuditEntry>()

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
        storedEntries += entry
        return entry
    }

    override fun entries(): List<MaintenanceAuditEntry> = storedEntries.toList()
}

fun MaintenanceTransactionResult<*>.auditOutcome(): MaintenanceAuditOutcome = when (this) {
    is MaintenanceTransactionResult.SuccessVerified<*> -> MaintenanceAuditOutcome.SUCCESS_VERIFIED
    is MaintenanceTransactionResult.WriteRejected -> MaintenanceAuditOutcome.WRITE_REJECTED
    is MaintenanceTransactionResult.TransportDisconnected ->
        MaintenanceAuditOutcome.TRANSPORT_DISCONNECTED
    is MaintenanceTransactionResult.Timeout -> MaintenanceAuditOutcome.TIMEOUT
    is MaintenanceTransactionResult.MalformedResponse -> MaintenanceAuditOutcome.MALFORMED_RESPONSE
    is MaintenanceTransactionResult.Unsupported -> MaintenanceAuditOutcome.UNSUPPORTED
    is MaintenanceTransactionResult.ReadBackMismatch<*> ->
        MaintenanceAuditOutcome.READ_BACK_MISMATCH
    is MaintenanceTransactionResult.PartialApply -> MaintenanceAuditOutcome.PARTIAL_APPLY
}
