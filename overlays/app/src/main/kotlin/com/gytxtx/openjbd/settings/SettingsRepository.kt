package com.gytxtx.openjbd.settings

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.gytxtx.openjbd.maintenance.WriteAuthorization
import com.gytxtx.openjbd.maintenance.WriteDecision

data class SettingsSectionState(
    val values: Map<SettingField, FieldValue> = emptyMap(),
    val registerErrors: Map<Int, RegisterAccessError> = emptyMap(),
    val accessMode: SettingsAccessMode = SettingsAccessMode.BLOCKED,
    val warnings: Set<SettingsSessionWarning> = emptySet(),
    val loading: Boolean = false,
    val lastUpdatedAtMillis: Long? = null
)

data class SettingsState(
    val sections: Map<SettingsGroup, SettingsSectionState> = SettingsGroup.values().associateWith {
        SettingsSectionState()
    },
    val notConnected: Boolean = true,
    val lastError: String? = null,
    val lastWriteResult: WriteSessionResult? = null,
    /** True while a write session runs; it outlives the dialog that started it. */
    val writeInProgress: Boolean = false
)

/**
 * Application-facing owner of settings state. Production uses IO; the internal constructor keeps
 * the clock and dispatcher deterministic for local JVM tests without adding Hilt bindings.
 */
@Singleton
class SettingsRepository internal constructor(
    private val gateway: BleSettingsGateway,
    private val clock: () -> Long,
    dispatcher: CoroutineDispatcher,
    private val authorizer: SettingsWriteAuthorizer
) {
    @Inject
    constructor(gateway: BleSettingsGateway, authorization: WriteAuthorization) : this(
        gateway,
        System::currentTimeMillis,
        Dispatchers.IO,
        authorization
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val coordinator = SettingsSessionCoordinator(gateway)
    private val readInProgress = AtomicBoolean(false)
    private val writeInProgress = AtomicBoolean(false)
    private val _state = MutableStateFlow(
        SettingsState(notConnected = !gateway.isConnected())
    )

    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun readGroups(groups: Collection<SettingsGroup>) {
        val requestedGroups = groups.toSet()
        if (requestedGroups.isEmpty() || !readInProgress.compareAndSet(false, true)) return

        if (!gateway.isConnected()) {
            _state.update {
                it.copy(notConnected = true, lastError = NOT_CONNECTED_MESSAGE)
            }
            readInProgress.set(false)
            return
        }

        _state.update { current ->
            current.copy(
                sections = current.sections.mapValues { (group, section) ->
                    if (group in requestedGroups) section.copy(loading = true) else section
                },
                notConnected = false,
                lastError = null
            )
        }

        scope.launch {
            try {
                val fields = SettingField.values().filter { it.group in requestedGroups }
                val result = gateway.withSession { coordinator.readFields(fields) }
                val updatedAt = clock()
                val reconnectRequired =
                    SettingsSessionWarning.RECONNECT_REQUIRED in result.warnings
                _state.update { current ->
                    val sections = current.sections.toMutableMap()
                    requestedGroups.forEach { group ->
                        val groupFields = SettingField.values().filter { it.group == group }
                        val addresses = groupFields.mapTo(mutableSetOf()) { it.register.address }
                        sections[group] = SettingsSectionState(
                            values = result.values.filterKeys { it.group == group },
                            registerErrors = result.registerErrors.filterKeys { it in addresses },
                            accessMode = result.accessMode,
                            warnings = result.warnings,
                            loading = false,
                            lastUpdatedAtMillis = updatedAt
                        )
                    }
                    current.copy(
                        sections = sections,
                        notConnected = reconnectRequired,
                        lastError = if (result.registerErrors.isEmpty()) {
                            null
                        } else {
                            REGISTER_READ_ERROR_MESSAGE
                        }
                    )
                }
            } catch (error: IllegalStateException) {
                if (error.message != NOT_CONNECTED_MESSAGE) throw error
                markReadFailed(requestedGroups, true, NOT_CONNECTED_MESSAGE)
            } catch (error: Exception) {
                markReadFailed(
                    requestedGroups,
                    !gateway.isConnected(),
                    error.message ?: SETTINGS_READ_ERROR_MESSAGE
                )
            } finally {
                readInProgress.set(false)
            }
        }
    }

    /**
     * Runs one write session in the application-scoped [scope], not in the caller's scope.
     * A dialog destroyed by rotation or dismissal cancels only its wait; the BLE session itself
     * always finishes (including the commit / no-commit factory exit) and the result is kept in
     * [SettingsState.lastWriteResult] for the recreated UI. Concurrent sessions are rejected.
     */
    suspend fun applyChanges(changes: List<FieldChange>): WriteSessionResult {
        if (!gateway.isConnected()) {
            return notConnectedWriteResult().also { result ->
                _state.update {
                    it.copy(
                        notConnected = true,
                        lastError = NOT_CONNECTED_MESSAGE,
                        lastWriteResult = result
                    )
                }
            }
        }
        // D005 gate, checked at the moment of writing (not only in the UI): no frame is sent
        // unless Maintenance Mode is unlocked for this device and it is verified or consented.
        val decision = authorizer.authorize()
        if (decision is WriteDecision.Denied) {
            val result = WriteSessionResult(
                outcome = WriteSessionOutcome.NotPermitted(decision.reason)
            )
            _state.update {
                it.copy(lastError = WRITE_NOT_PERMITTED_MESSAGE, lastWriteResult = result)
            }
            return result
        }
        decision as WriteDecision.Allowed
        if (!writeInProgress.compareAndSet(false, true)) {
            return WriteSessionResult(
                outcome = WriteSessionOutcome.EntryFailed(
                    RegisterAccessError.TransportFailure(WRITE_BUSY_MESSAGE)
                )
            )
        }
        _state.update { it.copy(writeInProgress = true) }

        return scope.async {
            try {
                val result = try {
                    gateway.withSession { coordinator.applyChanges(changes) }
                } catch (error: IllegalStateException) {
                    if (error.message != NOT_CONNECTED_MESSAGE) throw error
                    notConnectedWriteResult()
                }
                authorizer.recordSession(decision, result)
                _state.update {
                    it.copy(
                        notConnected = !gateway.isConnected(),
                        lastError = writeErrorSummary(result),
                        lastWriteResult = result,
                        writeInProgress = false
                    )
                }
                result
            } finally {
                writeInProgress.set(false)
                _state.update { if (it.writeInProgress) it.copy(writeInProgress = false) else it }
            }
        }.await()
    }

    private fun markReadFailed(
        groups: Set<SettingsGroup>,
        notConnected: Boolean,
        message: String
    ) {
        _state.update { current ->
            current.copy(
                sections = current.sections.mapValues { (group, section) ->
                    if (group in groups) section.copy(loading = false) else section
                },
                notConnected = notConnected,
                lastError = message
            )
        }
    }

    private fun notConnectedWriteResult() = WriteSessionResult(
        outcome = WriteSessionOutcome.EntryFailed(
            RegisterAccessError.TransportFailure(NOT_CONNECTED_MESSAGE)
        )
    )

    private fun writeErrorSummary(result: WriteSessionResult): String? = when (result.outcome) {
        WriteSessionOutcome.Committed, WriteSessionOutcome.Applied -> null
        WriteSessionOutcome.NotCommitted -> WRITE_NOT_COMMITTED_MESSAGE
        is WriteSessionOutcome.CommitUnconfirmed -> WRITE_COMMIT_UNCONFIRMED_MESSAGE
        is WriteSessionOutcome.NotPermitted -> WRITE_NOT_PERMITTED_MESSAGE
        is WriteSessionOutcome.ValidationFailed -> WRITE_VALIDATION_ERROR_MESSAGE
        is WriteSessionOutcome.EntryFailed -> when (val error = result.outcome.error) {
            is RegisterAccessError.TransportFailure -> error.message
            else -> SETTINGS_WRITE_ERROR_MESSAGE
        }
    }

    private companion object {
        const val NOT_CONNECTED_MESSAGE = "not connected"
        const val REGISTER_READ_ERROR_MESSAGE = "One or more settings registers could not be read"
        const val SETTINGS_READ_ERROR_MESSAGE = "Settings read failed"
        const val SETTINGS_WRITE_ERROR_MESSAGE = "Settings write session could not start"
        const val WRITE_NOT_COMMITTED_MESSAGE = "Settings changes were not committed"
        const val WRITE_VALIDATION_ERROR_MESSAGE = "Settings changes failed validation"
        const val WRITE_COMMIT_UNCONFIRMED_MESSAGE =
            "The BMS did not acknowledge the commit; re-read the values before trusting them"
        const val WRITE_BUSY_MESSAGE = "Another settings write session is still running"
        const val WRITE_NOT_PERMITTED_MESSAGE = "Writes are not permitted for this device"
    }
}
