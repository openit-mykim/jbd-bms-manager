package com.gytxtx.openjbd.maintenance

enum class TransactionPhase {
    READ_CURRENT,
    VALIDATE,
    APPLY,
    READ_BACK,
    VERIFY
}

enum class WriteRejectionSource {
    LOCAL_VALIDATION,
    DEVICE
}

sealed interface GatewayReadResult<out Value> {
    data class ValueRead<Value>(val value: Value) : GatewayReadResult<Value>
    data class TransportDisconnected(val detail: String? = null) : GatewayReadResult<Nothing>
    data class Timeout(val detail: String? = null) : GatewayReadResult<Nothing>
    data class MalformedResponse(val detail: String? = null) : GatewayReadResult<Nothing>
    data class Unsupported(val reason: String? = null) : GatewayReadResult<Nothing>
}

sealed interface GatewayWriteResult {
    object Acknowledged : GatewayWriteResult
    data class Rejected(val reason: String? = null) : GatewayWriteResult
    data class TransportDisconnected(val detail: String? = null) : GatewayWriteResult
    data class Timeout(val detail: String? = null) : GatewayWriteResult
    data class MalformedResponse(val detail: String? = null) : GatewayWriteResult
    data class Unsupported(val reason: String? = null) : GatewayWriteResult
    data class PartiallyApplied(
        val appliedCount: Int,
        val requestedCount: Int,
        val detail: String? = null
    ) : GatewayWriteResult
}

/** Protocol/transport boundary. Implementations convert expected BLE failures into result values. */
interface MaintenanceTransactionGateway<Value, Diff> {
    suspend fun readCurrent(): GatewayReadResult<Value>
    suspend fun apply(diff: Diff): GatewayWriteResult
    suspend fun readBack(): GatewayReadResult<Value>
}

fun interface ReadBackComparer<Value> {
    fun equivalent(expected: Value, actual: Value): Boolean

    companion object {
        /** Default comparison: Kotlin equality, with no tolerance or implicit unit conversion. */
        fun <Value> exact(): ReadBackComparer<Value> =
            ReadBackComparer { expected, actual -> expected == actual }

        /**
         * Explicit normalized comparison for domains that store equivalent values differently.
         * Callers own the normalization rule (for example, conversion to protocol integer units).
         */
        fun <Value, Normalized> normalized(
            normalize: (Value) -> Normalized
        ): ReadBackComparer<Value> = ReadBackComparer { expected, actual ->
            normalize(expected) == normalize(actual)
        }
    }
}

/** Domain-owned validation, diff, and verification behavior for one writable operation. */
interface MaintenanceOperation<Value, Diff> {
    fun validate(candidate: Value): ValidationResult

    /** Returns null only when current and candidate require no device write. */
    fun diff(current: Value, candidate: Value): Diff?

    fun verify(
        candidate: Value,
        readBack: Value,
        comparer: ReadBackComparer<Value>
    ): Boolean = comparer.equivalent(candidate, readBack)
}

/**
 * Complete expected outcome model for the maintenance failure list.
 *
 * Mapping to `docs/maintenance-mode.md`:
 * - [WriteRejected] = local validation rejection or a rejected write frame.
 * - [TransportDisconnected] = BLE disconnected before/during the transaction.
 * - [MalformedResponse] = checksum or malformed protocol response.
 * - [Timeout] = protocol/transport deadline elapsed.
 * - [ReadBackMismatch] = write acknowledged but the stored value differs.
 * - [Unsupported] = connected firmware does not support the operation.
 * - [PartialApply] = only part of a multi-field diff was accepted.
 * - [SuccessVerified] = no change was needed, or an applied write passed read-back verification.
 */
sealed interface MaintenanceTransactionResult<out Value> {
    data class SuccessVerified<Value>(
        val previousValue: Value,
        val requestedValue: Value,
        val verifiedValue: Value,
        val writeApplied: Boolean
    ) : MaintenanceTransactionResult<Value>

    data class WriteRejected(
        val reason: String? = null,
        val source: WriteRejectionSource = WriteRejectionSource.DEVICE
    ) : MaintenanceTransactionResult<Nothing>

    data class TransportDisconnected(
        val phase: TransactionPhase,
        val detail: String? = null
    ) : MaintenanceTransactionResult<Nothing>

    data class Timeout(
        val phase: TransactionPhase,
        val detail: String? = null
    ) : MaintenanceTransactionResult<Nothing>

    data class MalformedResponse(
        val phase: TransactionPhase,
        val detail: String? = null
    ) : MaintenanceTransactionResult<Nothing>

    data class Unsupported(
        val phase: TransactionPhase,
        val reason: String? = null
    ) : MaintenanceTransactionResult<Nothing>

    data class ReadBackMismatch<Value>(
        val previousValue: Value,
        val requestedValue: Value,
        val actualValue: Value
    ) : MaintenanceTransactionResult<Value>

    data class PartialApply(
        val appliedCount: Int,
        val requestedCount: Int,
        val detail: String? = null
    ) : MaintenanceTransactionResult<Nothing>
}

interface MaintenanceTransaction<Value> {
    suspend fun execute(candidate: Value): MaintenanceTransactionResult<Value>
}

/**
 * Runs D004 in order: read current → validate → diff → apply → read back → verify.
 * Expected protocol and transport failures remain values. Implementations must not translate
 * coroutine cancellation or programming defects into an apparent device outcome.
 */
class MaintenanceTransactionCoordinator<Value, Diff>(
    private val gateway: MaintenanceTransactionGateway<Value, Diff>,
    private val operation: MaintenanceOperation<Value, Diff>,
    private val comparer: ReadBackComparer<Value> = ReadBackComparer.exact()
) : MaintenanceTransaction<Value> {
    override suspend fun execute(candidate: Value): MaintenanceTransactionResult<Value> {
        val current = when (val read = gateway.readCurrent()) {
            is GatewayReadResult.ValueRead -> read.value
            is GatewayReadResult.TransportDisconnected ->
                return MaintenanceTransactionResult.TransportDisconnected(
                    TransactionPhase.READ_CURRENT,
                    read.detail
                )
            is GatewayReadResult.Timeout ->
                return MaintenanceTransactionResult.Timeout(TransactionPhase.READ_CURRENT, read.detail)
            is GatewayReadResult.MalformedResponse ->
                return MaintenanceTransactionResult.MalformedResponse(
                    TransactionPhase.READ_CURRENT,
                    read.detail
                )
            is GatewayReadResult.Unsupported ->
                return MaintenanceTransactionResult.Unsupported(
                    TransactionPhase.READ_CURRENT,
                    read.reason
                )
        }

        when (val validation = operation.validate(candidate)) {
            ValidationResult.Valid -> Unit
            is ValidationResult.Invalid -> {
                return MaintenanceTransactionResult.WriteRejected(
                    reason = validation.reason,
                    source = WriteRejectionSource.LOCAL_VALIDATION
                )
            }
        }

        val diff = operation.diff(current, candidate)
            ?: return MaintenanceTransactionResult.SuccessVerified(
                previousValue = current,
                requestedValue = candidate,
                verifiedValue = current,
                writeApplied = false
            )

        when (val write = gateway.apply(diff)) {
            GatewayWriteResult.Acknowledged -> Unit
            is GatewayWriteResult.Rejected ->
                return MaintenanceTransactionResult.WriteRejected(write.reason)
            is GatewayWriteResult.TransportDisconnected ->
                return MaintenanceTransactionResult.TransportDisconnected(
                    TransactionPhase.APPLY,
                    write.detail
                )
            is GatewayWriteResult.Timeout ->
                return MaintenanceTransactionResult.Timeout(TransactionPhase.APPLY, write.detail)
            is GatewayWriteResult.MalformedResponse ->
                return MaintenanceTransactionResult.MalformedResponse(
                    TransactionPhase.APPLY,
                    write.detail
                )
            is GatewayWriteResult.Unsupported ->
                return MaintenanceTransactionResult.Unsupported(
                    TransactionPhase.APPLY,
                    write.reason
                )
            is GatewayWriteResult.PartiallyApplied ->
                return MaintenanceTransactionResult.PartialApply(
                    write.appliedCount,
                    write.requestedCount,
                    write.detail
                )
        }

        val readBack = when (val read = gateway.readBack()) {
            is GatewayReadResult.ValueRead -> read.value
            is GatewayReadResult.TransportDisconnected ->
                return MaintenanceTransactionResult.TransportDisconnected(
                    TransactionPhase.READ_BACK,
                    read.detail
                )
            is GatewayReadResult.Timeout ->
                return MaintenanceTransactionResult.Timeout(TransactionPhase.READ_BACK, read.detail)
            is GatewayReadResult.MalformedResponse ->
                return MaintenanceTransactionResult.MalformedResponse(
                    TransactionPhase.READ_BACK,
                    read.detail
                )
            is GatewayReadResult.Unsupported ->
                return MaintenanceTransactionResult.Unsupported(
                    TransactionPhase.READ_BACK,
                    read.reason
                )
        }

        return if (operation.verify(candidate, readBack, comparer)) {
            MaintenanceTransactionResult.SuccessVerified(
                previousValue = current,
                requestedValue = candidate,
                verifiedValue = readBack,
                writeApplied = true
            )
        } else {
            MaintenanceTransactionResult.ReadBackMismatch(
                previousValue = current,
                requestedValue = candidate,
                actualValue = readBack
            )
        }
    }
}
