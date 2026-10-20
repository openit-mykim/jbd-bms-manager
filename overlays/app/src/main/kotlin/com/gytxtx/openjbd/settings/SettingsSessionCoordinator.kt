package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.ValidationResult
import com.gytxtx.openjbd.protocol.ErrorCountersResult
import com.gytxtx.openjbd.protocol.JbdCommands
import com.gytxtx.openjbd.protocol.JbdConfigCodec
import com.gytxtx.openjbd.protocol.JbdConfigCommands
import com.gytxtx.openjbd.protocol.RegisterResponseResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class SettingsAccessMode {
    FACTORY,
    DIRECT,
    BLOCKED
}

data class FieldValue(
    val raw: Int,
    val physicalValue: Double
)

sealed interface RegisterAccessError {
    object NoResponse : RegisterAccessError
    data class TransportFailure(
        val message: String,
        val reconnectRequired: Boolean = false
    ) : RegisterAccessError
    data class ErrorStatus(val status: Int) : RegisterAccessError
    data class Malformed(val reason: String) : RegisterAccessError
}

enum class SettingsSessionWarning {
    FACTORY_ENTRY_FAILED_DIRECT_FALLBACK,
    EXIT_UNCONFIRMED,
    DEVICE_MAY_REMAIN_IN_FACTORY_MODE,
    COUNTER_SNAPSHOT_UNAVAILABLE,
    POST_COMMIT_CONFIRMATION_UNAVAILABLE,
    POST_COMMIT_MISMATCH,
    RECONNECT_REQUIRED
}

data class SettingsReadResult(
    val values: Map<SettingField, FieldValue>,
    val registerErrors: Map<Int, RegisterAccessError>,
    val accessMode: SettingsAccessMode,
    val warnings: Set<SettingsSessionWarning> = emptySet(),
    val entryFailure: RegisterAccessError? = null
)

data class FieldChange(
    val field: SettingField,
    val rawValue: Int,
    /** Current values used to validate relations whose counterpart is not staged. */
    val relatedValues: Map<SettingField, Int> = emptyMap()
)

data class FieldValidationFailure(
    val change: FieldChange,
    val reason: String
)

enum class ChangePhase {
    READ_CURRENT,
    WRITE,
    READ_BACK
}

sealed interface ChangeDetail {
    data class Verified(
        val previousRegisterRaw: Int,
        val writtenRegisterRaw: Int
    ) : ChangeDetail

    data class ReadBackMismatch(
        val expectedRegisterRaw: Int,
        val actualRegisterRaw: Int
    ) : ChangeDetail

    data class NoResponse(val phase: ChangePhase) : ChangeDetail
    data class ErrorStatus(val phase: ChangePhase, val status: Int) : ChangeDetail
    data class TransportFailure(val phase: ChangePhase, val message: String) : ChangeDetail
    data class MalformedResponse(val phase: ChangePhase, val reason: String) : ChangeDetail
    object SkippedNoChange : ChangeDetail
}

data class FieldChangeResult(
    val change: FieldChange,
    val detail: ChangeDetail
)

sealed interface WriteSessionOutcome {
    object Committed : WriteSessionOutcome
    object NotCommitted : WriteSessionOutcome
    data class ValidationFailed(
        val failures: List<FieldValidationFailure>
    ) : WriteSessionOutcome

    data class EntryFailed(val error: RegisterAccessError) : WriteSessionOutcome
}

data class PostCommitConfirmation(
    val values: Map<SettingField, FieldValue>,
    val mismatched: Set<SettingField>
)

data class WriteSessionResult(
    val outcome: WriteSessionOutcome,
    val changes: List<FieldChangeResult> = emptyList(),
    val warnings: Set<SettingsSessionWarning> = emptySet(),
    val errorCountersBeforeCommit: IntArray? = null,
    val postCommitConfirmation: PostCommitConfirmation? = null
)

/**
 * Coordinates candidate settings-register sessions without depending on Android or BLE APIs.
 *
 * The later BLE integration must pause its periodic poll cycle for the whole session: ordinary
 * polling interleaved with factory-mode traffic can corrupt request/response ordering. A caller
 * may cancel a session; cancellation propagates, while a confirmed factory session attempts a
 * non-cancellable, best-effort no-commit exit before unwinding. Unexpected gateway exceptions
 * follow the same cleanup path and then propagate unchanged to the caller.
 *
 * Each operation retries only [GatewayOutcome.NoResponse], with [retryDelayMs] between attempts.
 * Error statuses, malformed responses, and transport failures are never retried. Retry counts are
 * extra attempts after the first and are independent per operation. The commit exit is deliberately
 * single-attempt because a lost response is ambiguous and committing resets error counters.
 */
class SettingsSessionCoordinator(
    private val gateway: SettingsGateway,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val readRetries: Int = 2,
    private val writeRetries: Int = 1,
    private val enterRetries: Int = 1,
    private val exitRetries: Int = 1,
    private val retryDelayMs: Long = 150
) {
    private var factoryExitConfirmationPending = false

    init {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        require(readRetries >= 0) { "readRetries must not be negative" }
        require(writeRetries >= 0) { "writeRetries must not be negative" }
        require(enterRetries >= 0) { "enterRetries must not be negative" }
        require(exitRetries >= 0) { "exitRetries must not be negative" }
        require(retryDelayMs >= 0) { "retryDelayMs must not be negative" }
    }

    suspend fun readFields(fields: Collection<SettingField>): SettingsReadResult {
        val fieldsInOrder = fields.distinct()
        if (fieldsInOrder.isEmpty()) {
            return SettingsReadResult(emptyMap(), emptyMap(), SettingsAccessMode.DIRECT)
        }

        val registerFields = linkedMapOf<Int, MutableList<SettingField>>()
        fieldsInOrder.forEach { field ->
            registerFields.getOrPut(field.register.address) { mutableListOf() } += field
        }

        val warnings = linkedSetOf<SettingsSessionWarning>()
        val values = linkedMapOf<SettingField, FieldValue>()
        val errors = linkedMapOf<Int, RegisterAccessError>()
        val cachedRaw = mutableMapOf<Int, ByteArray>()
        var factoryEntered = false
        var exitAttempted = false
        var entryFailure: RegisterAccessError? = null
        var accessMode = SettingsAccessMode.FACTORY

        confirmPendingFactoryExit()?.let { recoveryError ->
            addExitFailureWarnings(warnings, recoveryError)
            return SettingsReadResult(
                values = emptyMap(),
                registerErrors = emptyMap(),
                accessMode = SettingsAccessMode.BLOCKED,
                warnings = warnings,
                entryFailure = recoveryError
            )
        }

        try {
            entryFailure = enterFactory()
            if (entryFailure == null) {
                factoryEntered = true
            } else {
                if (entryFailure is RegisterAccessError.TransportFailure) {
                    addReconnectWarning(warnings, entryFailure)
                    return SettingsReadResult(
                        values = emptyMap(),
                        registerErrors = emptyMap(),
                        accessMode = SettingsAccessMode.BLOCKED,
                        warnings = warnings,
                        entryFailure = entryFailure
                    )
                }
                accessMode = SettingsAccessMode.DIRECT
                warnings += SettingsSessionWarning.FACTORY_ENTRY_FAILED_DIRECT_FALLBACK
                val firstAddress = registerFields.keys.first()
                when (val directRead = readRaw(firstAddress)) {
                    is RawRead.Success -> cachedRaw[firstAddress] = directRead.data
                    is RawRead.Failure -> {
                        errors[firstAddress] = directRead.error
                        addReconnectWarning(warnings, directRead.error)
                        return SettingsReadResult(
                            values = emptyMap(),
                            registerErrors = errors,
                            accessMode = SettingsAccessMode.BLOCKED,
                            warnings = warnings,
                            entryFailure = entryFailure
                        )
                    }
                }
            }

            for ((address, fieldsAtAddress) in registerFields) {
                val raw = cachedRaw[address] ?: when (val read = readRaw(address)) {
                    is RawRead.Success -> read.data
                    is RawRead.Failure -> {
                        errors[address] = read.error
                        addReconnectWarning(warnings, read.error)
                        if (shouldAbortRemainingReads(address, read.error)) break
                        continue
                    }
                }
                decodeFields(fieldsAtAddress, raw, values)
            }

            if (factoryEntered) {
                val exitError = exitFactory(commit = false)
                exitAttempted = true
                if (exitError != null) addExitFailureWarnings(warnings, exitError)
            }
        } finally {
            if (factoryEntered && !exitAttempted) {
                bestEffortNoCommitExit()
            }
        }

        return SettingsReadResult(values, errors, accessMode, warnings, entryFailure)
    }

    suspend fun applyChanges(changes: List<FieldChange>): WriteSessionResult {
        val validationFailures = validateChanges(changes)
        if (validationFailures.isNotEmpty()) {
            return WriteSessionResult(
                outcome = WriteSessionOutcome.ValidationFailed(validationFailures)
            )
        }
        if (changes.isEmpty()) {
            return WriteSessionResult(outcome = WriteSessionOutcome.NotCommitted)
        }

        val warnings = linkedSetOf<SettingsSessionWarning>()
        confirmPendingFactoryExit()?.let { recoveryError ->
            addExitFailureWarnings(warnings, recoveryError)
            return WriteSessionResult(
                outcome = WriteSessionOutcome.EntryFailed(recoveryError),
                warnings = warnings
            )
        }

        val entryError = enterFactory()
        if (entryError != null) {
            addReconnectWarning(warnings, entryError)
            return WriteSessionResult(
                outcome = WriteSessionOutcome.EntryFailed(entryError),
                warnings = warnings
            )
        }

        val results = mutableListOf<FieldChangeResult>()
        var factoryEntered = true
        var exitAttempted = false
        var allOperationsSuccessful = true
        var writtenCount = 0
        var fatalTransportFailure = false
        var counterSnapshot: IntArray? = null

        try {
            for (change in changes) {
                val field = change.field
                val address = field.register.address
                val currentRaw = when (val read = readRaw(address)) {
                    is RawRead.Success -> read.data
                    is RawRead.Failure -> {
                        results += FieldChangeResult(
                            change,
                            read.error.toChangeDetail(ChangePhase.READ_CURRENT)
                        )
                        allOperationsSuccessful = false
                        if (read.error is RegisterAccessError.TransportFailure) {
                            addReconnectWarning(warnings, read.error)
                            fatalTransportFailure = true
                            break
                        }
                        continue
                    }
                }

                val previousRegisterRaw = if (field.bitIndex != null) {
                    U16_CODEC.decode(currentRaw).raw
                } else {
                    field.register.codec.decode(currentRaw).raw
                }
                val intendedRegisterRaw = if (field.bitIndex != null) {
                    JbdConfigCodec.setU16Bit(
                        previousRegisterRaw,
                        field.bitIndex,
                        change.rawValue == 1
                    )
                } else {
                    change.rawValue
                }
                val currentFieldRaw = if (field.bitIndex != null) {
                    if (JbdConfigCodec.getU16Bit(previousRegisterRaw, field.bitIndex)) 1 else 0
                } else {
                    field.register.codec.decode(currentRaw).raw
                }

                if (currentFieldRaw == change.rawValue) {
                    results += FieldChangeResult(change, ChangeDetail.SkippedNoChange)
                    continue
                }

                val intendedBytes = if (field.bitIndex != null) {
                    U16_CODEC.encodeRaw(intendedRegisterRaw)
                } else {
                    field.register.codec.encodeRaw(intendedRegisterRaw)
                }
                val writeError = commandError(
                    executeWithNoResponseRetries(
                        JbdConfigCommands.writeRegister(address, intendedBytes),
                        address,
                        writeRetries
                    ),
                    address
                )
                if (writeError != null) {
                    results += FieldChangeResult(
                        change,
                        writeError.toChangeDetail(ChangePhase.WRITE)
                    )
                    allOperationsSuccessful = false
                    if (writeError is RegisterAccessError.TransportFailure) {
                        addReconnectWarning(warnings, writeError)
                        fatalTransportFailure = true
                        break
                    }
                    continue
                }
                writtenCount += 1

                when (val readBack = readRaw(address)) {
                    is RawRead.Success -> {
                        val actualRegisterRaw = if (field.bitIndex != null) {
                            U16_CODEC.decode(readBack.data).raw
                        } else {
                            field.register.codec.decode(readBack.data).raw
                        }
                        val matches = if (field.bitIndex != null) {
                            JbdConfigCodec.getU16Bit(actualRegisterRaw, field.bitIndex) ==
                                (change.rawValue == 1)
                        } else {
                            readBack.data.contentEquals(intendedBytes)
                        }
                        if (matches) {
                            results += FieldChangeResult(
                                change,
                                ChangeDetail.Verified(previousRegisterRaw, intendedRegisterRaw)
                            )
                        } else {
                            results += FieldChangeResult(
                                change,
                                ChangeDetail.ReadBackMismatch(
                                    intendedRegisterRaw,
                                    actualRegisterRaw
                                )
                            )
                            allOperationsSuccessful = false
                        }
                    }
                    is RawRead.Failure -> {
                        results += FieldChangeResult(
                            change,
                            readBack.error.toChangeDetail(ChangePhase.READ_BACK)
                        )
                        allOperationsSuccessful = false
                        if (readBack.error is RegisterAccessError.TransportFailure) {
                            addReconnectWarning(warnings, readBack.error)
                            fatalTransportFailure = true
                        }
                    }
                }
                if (fatalTransportFailure) break
            }

            if (!fatalTransportFailure) {
                counterSnapshot = readErrorCounters()
                if (counterSnapshot == null) {
                    warnings += SettingsSessionWarning.COUNTER_SNAPSHOT_UNAVAILABLE
                }
            } else {
                warnings += SettingsSessionWarning.COUNTER_SNAPSHOT_UNAVAILABLE
            }

            val shouldCommit = allOperationsSuccessful && writtenCount > 0
            val exitError = exitFactory(commit = shouldCommit)
            exitAttempted = true
            if (exitError != null) addExitFailureWarnings(warnings, exitError)
            factoryEntered = false

            val outcome = if (shouldCommit) {
                WriteSessionOutcome.Committed
            } else {
                WriteSessionOutcome.NotCommitted
            }
            var postCommitConfirmation: PostCommitConfirmation? = null
            if (outcome is WriteSessionOutcome.Committed) {
                val confirmationAttempt = confirmCommittedChanges(results)
                postCommitConfirmation = confirmationAttempt.confirmation
                if (postCommitConfirmation == null) {
                    warnings += SettingsSessionWarning.POST_COMMIT_CONFIRMATION_UNAVAILABLE
                } else if (postCommitConfirmation.mismatched.isNotEmpty()) {
                    warnings += SettingsSessionWarning.POST_COMMIT_MISMATCH
                }
                if (confirmationAttempt.exitUnconfirmed) {
                    warnings += SettingsSessionWarning.EXIT_UNCONFIRMED
                    warnings += SettingsSessionWarning.DEVICE_MAY_REMAIN_IN_FACTORY_MODE
                }
                if (confirmationAttempt.reconnectRequired) {
                    warnings += SettingsSessionWarning.RECONNECT_REQUIRED
                }
            }

            return WriteSessionResult(
                outcome = outcome,
                changes = results,
                warnings = warnings,
                errorCountersBeforeCommit = counterSnapshot,
                postCommitConfirmation = postCommitConfirmation
            )
        } finally {
            if (factoryEntered && !exitAttempted) {
                bestEffortNoCommitExit()
            }
        }
    }

    private fun validateChanges(changes: List<FieldChange>): List<FieldValidationFailure> {
        val duplicateFields = changes.groupingBy { it.field }.eachCount().filterValues { it > 1 }.keys
        val stagedValues = changes.associate { it.field to it.rawValue }
        val sharedCurrentValues = buildMap {
            changes.forEach { putAll(it.relatedValues) }
            putAll(stagedValues)
        }
        return buildList {
            changes.forEach { change ->
                if (change.field in duplicateFields) {
                    add(FieldValidationFailure(change, "Duplicate field in one write session"))
                } else {
                    when (val validation = validateField(
                        change.field,
                        change.rawValue,
                        sharedCurrentValues
                    )) {
                        ValidationResult.Valid -> Unit
                        is ValidationResult.Invalid ->
                            add(FieldValidationFailure(change, validation.reason))
                    }
                }
            }
        }
    }

    private suspend fun readRaw(address: Int): RawRead = when (
        val outcome = executeWithNoResponseRetries(
            JbdConfigCommands.readRegister(address),
            address,
            readRetries
        )
    ) {
        is GatewayOutcome.Response -> when (
            val decoded = JbdConfigCodec.decodeRegisterResponse(outcome.frame, address)
        ) {
            is RegisterResponseResult.Ok -> RawRead.Success(decoded.raw)
            is RegisterResponseResult.ErrorStatus ->
                RawRead.Failure(RegisterAccessError.ErrorStatus(decoded.status))
            is RegisterResponseResult.Malformed ->
                RawRead.Failure(RegisterAccessError.Malformed(decoded.reason))
        }
        GatewayOutcome.NoResponse -> RawRead.Failure(RegisterAccessError.NoResponse)
        is GatewayOutcome.TransportFailure ->
            RawRead.Failure(
                RegisterAccessError.TransportFailure(
                    outcome.message,
                    outcome.reconnectRequired
                )
            )
    }

    private suspend fun readErrorCounters(): IntArray? = when (
        val outcome = executeWithNoResponseRetries(
            JbdConfigCommands.readRegister(JbdConfigCodec.ERROR_COUNTERS_ADDRESS),
            JbdConfigCodec.ERROR_COUNTERS_ADDRESS,
            readRetries
        )
    ) {
        is GatewayOutcome.Response -> when (val decoded = JbdConfigCodec.decodeErrorCounters(outcome.frame)) {
            is ErrorCountersResult.Ok -> decoded.counters.copyOf()
            is ErrorCountersResult.ErrorStatus,
            is ErrorCountersResult.Malformed -> null
        }
        GatewayOutcome.NoResponse,
        is GatewayOutcome.TransportFailure -> null
    }

    private fun decodeFields(
        fields: List<SettingField>,
        raw: ByteArray,
        destination: MutableMap<SettingField, FieldValue>
    ) {
        val unsigned = U16_CODEC.decode(raw).raw
        fields.forEach { field ->
            if (field.bitIndex != null) {
                val bitRaw = if (JbdConfigCodec.getU16Bit(unsigned, field.bitIndex)) 1 else 0
                destination[field] = FieldValue(bitRaw, bitRaw.toDouble())
            } else {
                val decoded = field.register.codec.decode(raw)
                destination[field] = FieldValue(decoded.raw, decoded.physicalValue)
            }
        }
    }

    private suspend fun confirmCommittedChanges(
        results: List<FieldChangeResult>
    ): ConfirmationAttempt {
        val verifiedChanges = results.mapNotNull { result ->
            result.takeIf { it.detail is ChangeDetail.Verified }?.change
        }
        if (verifiedChanges.isEmpty()) {
            return ConfirmationAttempt(
                PostCommitConfirmation(emptyMap(), emptySet())
            )
        }

        val recoveryError = confirmPendingFactoryExit()
        if (recoveryError != null) {
            return ConfirmationAttempt(
                confirmation = null,
                exitUnconfirmed = true,
                reconnectRequired = recoveryError.requiresReconnect()
            )
        }

        val entryError = enterFactory()
        if (entryError != null) {
            return ConfirmationAttempt(
                confirmation = null,
                reconnectRequired = entryError.requiresReconnect()
            )
        }

        var factoryEntered = true
        var exitAttempted = false
        try {
            val registerFields = linkedMapOf<Int, MutableList<FieldChange>>()
            verifiedChanges.forEach { change ->
                registerFields.getOrPut(change.field.register.address) { mutableListOf() } += change
            }

            val values = linkedMapOf<SettingField, FieldValue>()
            val mismatched = linkedSetOf<SettingField>()
            var readsAvailable = true
            for ((address, changesAtAddress) in registerFields) {
                when (val read = readRaw(address)) {
                    is RawRead.Success -> {
                        val decodedAtAddress = linkedMapOf<SettingField, FieldValue>()
                        decodeFields(changesAtAddress.map { it.field }, read.data, decodedAtAddress)
                        values.putAll(decodedAtAddress)
                        changesAtAddress.forEach { change ->
                            val field = change.field
                            val matches = if (field.bitIndex != null) {
                                decodedAtAddress.getValue(field).raw == change.rawValue
                            } else {
                                read.data.contentEquals(field.register.codec.encodeRaw(change.rawValue))
                            }
                            if (!matches) mismatched += field
                        }
                    }
                    is RawRead.Failure -> {
                        readsAvailable = false
                        break
                    }
                }
            }

            val exitError = exitFactory(commit = false)
            exitAttempted = true
            factoryEntered = false
            if (!readsAvailable || exitError != null) {
                return ConfirmationAttempt(
                    confirmation = null,
                    exitUnconfirmed = exitError != null,
                    reconnectRequired = exitError?.requiresReconnect() == true
                )
            }
            return ConfirmationAttempt(PostCommitConfirmation(values, mismatched))
        } finally {
            if (factoryEntered && !exitAttempted) {
                bestEffortNoCommitExit()
            }
        }
    }

    private suspend fun enterFactory(): RegisterAccessError? {
        val address = JbdCommands.CMD_FACTORY_MODE.toInt() and 0xFF
        val error = commandError(
            executeWithNoResponseRetries(
                JbdCommands.openFactoryMode(),
                address,
                enterRetries
            ),
            address
        )
        if (error == null) factoryExitConfirmationPending = true
        return error
    }

    private suspend fun exitFactory(commit: Boolean): RegisterAccessError? {
        val address = JbdCommands.CMD_CLOSE_FACTORY_MODE.toInt() and 0xFF
        val frame = if (commit) {
            JbdConfigCommands.exitFactoryModeWithCommit()
        } else {
            JbdCommands.closeFactoryMode()
        }
        val outcome = if (commit) {
            gateway.execute(frame, address, timeoutMs)
        } else {
            executeWithNoResponseRetries(frame, address, exitRetries)
        }
        val error = commandError(outcome, address)
        if (error == null) factoryExitConfirmationPending = false
        return error
    }

    private suspend fun confirmPendingFactoryExit(): RegisterAccessError? =
        if (factoryExitConfirmationPending) exitFactory(commit = false) else null

    private suspend fun executeWithNoResponseRetries(
        frame: ByteArray,
        responseAddress: Int,
        extraAttempts: Int
    ): GatewayOutcome {
        var retriesUsed = 0
        while (true) {
            val outcome = gateway.execute(frame, responseAddress, timeoutMs)
            if (outcome !== GatewayOutcome.NoResponse || retriesUsed >= extraAttempts) {
                return outcome
            }
            retriesUsed += 1
            delay(retryDelayMs)
        }
    }

    private suspend fun bestEffortNoCommitExit() {
        withContext(NonCancellable) {
            try {
                exitFactory(commit = false)
            } catch (_: Exception) {
                // Cancellation must retain its original cause; cleanup is explicitly best effort.
            }
        }
    }

    private fun commandError(
        outcome: GatewayOutcome,
        expectedAddress: Int
    ): RegisterAccessError? = when (outcome) {
        is GatewayOutcome.Response -> when {
            outcome.frame.command != expectedAddress -> RegisterAccessError.Malformed(
                "Address echo mismatch: expected 0x${expectedAddress.toString(16)}, " +
                    "received 0x${outcome.frame.command.toString(16)}"
            )
            outcome.frame.status != 0 -> RegisterAccessError.ErrorStatus(outcome.frame.status)
            else -> null
        }
        GatewayOutcome.NoResponse -> RegisterAccessError.NoResponse
        is GatewayOutcome.TransportFailure -> RegisterAccessError.TransportFailure(
            outcome.message,
            outcome.reconnectRequired
        )
    }

    private fun shouldAbortRemainingReads(
        address: Int,
        error: RegisterAccessError
    ): Boolean = error is RegisterAccessError.TransportFailure ||
        (address in CALIBRATION_ACTION_REGISTER_RANGE &&
            error is RegisterAccessError.ErrorStatus &&
            error.status == CALIBRATION_UNSUPPORTED_STATUS)

    private fun addExitFailureWarnings(
        warnings: MutableSet<SettingsSessionWarning>,
        error: RegisterAccessError
    ) {
        warnings += SettingsSessionWarning.EXIT_UNCONFIRMED
        warnings += SettingsSessionWarning.DEVICE_MAY_REMAIN_IN_FACTORY_MODE
        addReconnectWarning(warnings, error)
    }

    private fun addReconnectWarning(
        warnings: MutableSet<SettingsSessionWarning>,
        error: RegisterAccessError
    ) {
        if (error.requiresReconnect()) {
            warnings += SettingsSessionWarning.RECONNECT_REQUIRED
        }
    }

    private fun RegisterAccessError.requiresReconnect(): Boolean =
        this is RegisterAccessError.TransportFailure && reconnectRequired

    private fun RegisterAccessError.toChangeDetail(phase: ChangePhase): ChangeDetail = when (this) {
        RegisterAccessError.NoResponse -> ChangeDetail.NoResponse(phase)
        is RegisterAccessError.ErrorStatus -> ChangeDetail.ErrorStatus(phase, status)
        is RegisterAccessError.TransportFailure -> ChangeDetail.TransportFailure(phase, message)
        is RegisterAccessError.Malformed -> ChangeDetail.MalformedResponse(phase, reason)
    }

    private sealed interface RawRead {
        data class Success(val data: ByteArray) : RawRead
        data class Failure(val error: RegisterAccessError) : RawRead
    }

    private data class ConfirmationAttempt(
        val confirmation: PostCommitConfirmation?,
        val exitUnconfirmed: Boolean = false,
        val reconnectRequired: Boolean = false
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 3_000L
        private const val CALIBRATION_UNSUPPORTED_STATUS = 0x82
        private val CALIBRATION_ACTION_REGISTER_RANGE = 0xAD..0xD7
        private val U16_CODEC = JbdConfigCodec.u16(1.0, "raw")
    }
}
