package com.gytxtx.openjbd.settings

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gytxtx.openjbd.BuildConfig
import com.gytxtx.openjbd.R
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.maintenance.ValidationResult
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupRestoreDialogFragment : DialogFragment() {
    @Inject lateinit var repository: SettingsRepository
    @Inject lateinit var bmsRepository: BmsRepository

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(::writeBackup) }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> uri?.let(::readBackup)
    }

    private lateinit var root: View
    private lateinit var refreshButton: MaterialButton
    private lateinit var progress: View
    private lateinit var readSummary: TextView
    private lateinit var readNotice: TextView
    private lateinit var exportButton: MaterialButton
    private lateinit var lastExported: TextView
    private lateinit var importButton: MaterialButton
    private lateinit var loadedSummary: TextView
    private lateinit var issuesList: LinearLayout
    private lateinit var reviewSection: View
    private lateinit var reviewList: LinearLayout
    private lateinit var resultSection: View
    private lateinit var resultOutcome: TextView
    private lateinit var resultDetails: LinearLayout
    private lateinit var applyButton: MaterialButton

    private var latestState = SettingsState()
    private var latestBmsState: BmsUiState? = null
    private var freshReadStartedAt = Long.MAX_VALUE
    private var parsedBackup: BackupParseResult? = null
    private var restoreDiff: RestoreDiffResult? = null
    private var pendingExportJson: String? = null
    private var lastExportedAt: Long? = null
    private var applying = false
    private var lastResult: WriteSessionResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_backup_restore, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        root = view
        val toolbar = view.findViewById<MaterialToolbar>(R.id.backup_restore_toolbar)
        refreshButton = view.findViewById(R.id.backup_refresh_button)
        progress = view.findViewById(R.id.backup_progress)
        readSummary = view.findViewById(R.id.backup_read_summary)
        readNotice = view.findViewById(R.id.backup_read_notice)
        exportButton = view.findViewById(R.id.backup_export_button)
        lastExported = view.findViewById(R.id.backup_last_exported)
        importButton = view.findViewById(R.id.backup_import_button)
        loadedSummary = view.findViewById(R.id.backup_loaded_summary)
        issuesList = view.findViewById(R.id.backup_issues)
        reviewSection = view.findViewById(R.id.backup_review_section)
        reviewList = view.findViewById(R.id.backup_review_list)
        resultSection = view.findViewById(R.id.backup_result_section)
        resultOutcome = view.findViewById(R.id.backup_result_outcome)
        resultDetails = view.findViewById(R.id.backup_result_details)
        applyButton = view.findViewById(R.id.backup_apply_button)

        toolbar.setNavigationOnClickListener { dismiss() }
        refreshButton.setOnClickListener { readConfiguration(clearImported = true) }
        exportButton.setOnClickListener { prepareExport() }
        importButton.setOnClickListener {
            openDocument.launch(arrayOf("application/json", "text/json", "text/plain"))
        }
        applyButton.setOnClickListener { showRestoreConfirmation() }
        applySystemBarInsets(view)

        latestBmsState = bmsRepository.getSnapshot()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect { state ->
                    latestState = state
                    rebuildDiff()
                    render()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                bmsRepository.uiState.collect { latestBmsState = it }
            }
        }
        readConfiguration(clearImported = false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applySystemBarInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun readConfiguration(clearImported: Boolean) {
        if (applying) return
        freshReadStartedAt = System.currentTimeMillis()
        if (clearImported) {
            parsedBackup = null
            restoreDiff = null
            lastResult = null
        }
        repository.readGroups(BackupFormat.configurationGroups)
        render()
    }

    private fun freshReadComplete(): Boolean = BackupFormat.configurationGroups.all { group ->
        val section = latestState.sections.getValue(group)
        !section.loading && section.lastUpdatedAtMillis?.let { it >= freshReadStartedAt } == true
    }

    private fun writeReady(): Boolean = freshReadComplete() && !latestState.notConnected &&
        BackupFormat.configurationGroups.all { group ->
            latestState.sections.getValue(group).accessMode == SettingsAccessMode.FACTORY
        }

    private fun currentRawValues(): Map<SettingField, Int> = buildMap {
        BackupFormat.configurationGroups.forEach { group ->
            latestState.sections.getValue(group).values.forEach { (field, value) ->
                put(field, value.raw)
            }
        }
    }

    private fun incompleteGroups(): List<SettingsGroup> =
        BackupFormat.configurationGroups.filter { group ->
            val section = latestState.sections.getValue(group)
            val expected = BackupFormat.configurationFields.count { it.group == group }
            section.values.size < expected || section.registerErrors.isNotEmpty()
        }

    private fun prepareExport() {
        if (!freshReadComplete() || applying) return
        val incomplete = incompleteGroups()
        if (incomplete.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_incomplete_export_title)
                .setMessage(
                    getString(
                        R.string.backup_incomplete_export_message,
                        incomplete.joinToString { getString(SettingsDisplay.groupTitleResId(it)) }
                    )
                )
                .setNegativeButton(R.string.control_cancel, null)
                .setPositiveButton(R.string.backup_export_anyway) { _, _ -> launchExport() }
                .show()
        } else {
            launchExport()
        }
    }

    private fun launchExport() {
        pendingExportJson = BackupFormat.serialize(buildBackup())
        val filename = SimpleDateFormat(BACKUP_FILENAME_PATTERN, Locale.US).format(Date())
        createDocument.launch("jbd-bms-backup-$filename.json")
    }

    private fun buildBackup(): ConfigurationBackup {
        val snapshot = latestBmsState ?: bmsRepository.getSnapshot()
        val deviceInfo = snapshot.deviceInfo
        val fields = BackupFormat.configurationFields.mapNotNull { field ->
            val value = latestState.sections.getValue(field.group).values[field] ?: return@mapNotNull null
            val display = SettingsDisplay.displayValue(field, value.raw)
            BackupFieldEntry(
                key = field.key,
                raw = value.raw,
                display = resolveDisplay(display),
                unit = display.unitStringResId?.let(::getString).orEmpty()
            )
        }
        return ConfigurationBackup(
            exportedAtUtc = utcTimestamp(System.currentTimeMillis()),
            appVersion = BuildConfig.VERSION_NAME,
            device = BackupDeviceIdentity(
                name = snapshot.deviceName.orEmpty(),
                address = snapshot.deviceAddress.orEmpty(),
                serial = deviceInfo?.serialNumber?.takeIf(String::isNotBlank),
                model = deviceInfo?.bmsModel?.takeIf(String::isNotBlank)
                    ?: deviceInfo?.batteryModel?.takeIf(String::isNotBlank)
            ),
            fields = fields
        )
    }

    private fun writeBackup(uri: Uri) {
        val json = pendingExportJson ?: return
        pendingExportJson = null
        try {
            val stream = requireContext().contentResolver.openOutputStream(uri)
                ?: error("output stream unavailable")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            lastExportedAt = System.currentTimeMillis()
            render()
        } catch (_: Exception) {
            showOperationError(R.string.backup_export_failed)
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val stream = requireContext().contentResolver.openInputStream(uri)
                ?: error("input stream unavailable")
            val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parsedBackup = BackupFormat.parse(json)
            lastResult = null
            rebuildDiff()
            render()
        } catch (_: Exception) {
            parsedBackup = null
            restoreDiff = null
            showOperationError(R.string.backup_import_failed)
        }
    }

    private fun rebuildDiff() {
        restoreDiff = parsedBackup?.let { BackupFormat.buildRestoreDiff(it, currentRawValues()) }
    }

    private fun render() {
        if (!::readSummary.isInitialized) return
        val fresh = freshReadComplete()
        val loading = BackupFormat.configurationGroups.any {
            latestState.sections.getValue(it).loading
        }
        progress.visibility = if (loading || applying) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !loading && !applying
        importButton.isEnabled = !applying

        val readCount = if (fresh) currentRawValues().size else 0
        readSummary.text = getString(
            R.string.backup_read_summary,
            readCount,
            BackupFormat.configurationFields.size
        )
        readNotice.text = restoreAvailabilityNotice(fresh)
        exportButton.isEnabled = fresh && !applying
        lastExported.text = lastExportedAt?.let {
            getString(R.string.backup_last_exported, localTimestamp(it))
        } ?: getString(R.string.backup_last_exported_never)

        val backup = parsedBackup?.backup
        loadedSummary.text = backup?.let {
            getString(
                R.string.backup_loaded_summary,
                it.device.name.ifBlank { getString(R.string.backup_unknown_device) },
                it.fields.size,
                it.exportedAtUtc
            )
        } ?: getString(R.string.backup_no_file_loaded)

        renderIssues()
        renderReview()
        renderResult()

        val unsupported = parsedBackup?.issues.orEmpty().any {
            it.kind == BackupIssueKind.UNSUPPORTED_FORMAT
        }
        applyButton.isEnabled = restoreDiff?.changes?.isNotEmpty() == true &&
            backup != null && !unsupported && writeReady() && !applying
    }

    private fun restoreAvailabilityNotice(fresh: Boolean): String = when {
        latestState.notConnected -> getString(R.string.backup_restore_not_connected)
        !fresh -> getString(R.string.backup_restore_waiting_fresh_read)
        BackupFormat.configurationGroups.any {
            latestState.sections.getValue(it).accessMode == SettingsAccessMode.BLOCKED
        } -> getString(R.string.backup_restore_blocked)
        BackupFormat.configurationGroups.any {
            latestState.sections.getValue(it).accessMode == SettingsAccessMode.DIRECT
        } -> getString(R.string.backup_restore_direct_read_only)
        else -> getString(R.string.backup_restore_ready)
    }

    private fun renderIssues() {
        issuesList.removeAllViews()
        incompleteGroups().takeIf { freshReadComplete() && it.isNotEmpty() }?.let { groups ->
            addIssueLine(
                issuesList,
                getString(
                    R.string.backup_incomplete_sections,
                    groups.joinToString { getString(SettingsDisplay.groupTitleResId(it)) }
                ),
                isError = true
            )
        }
        parsedBackup?.let { parsed ->
            parsed.issues.forEach { issue ->
                addIssueLine(issuesList, issueText(issue), isError = true)
            }
        }
        issuesList.visibility = if (issuesList.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun issueText(issue: BackupIssue): String {
        val label = when (issue.kind) {
            BackupIssueKind.MALFORMED_JSON -> R.string.backup_issue_malformed
            BackupIssueKind.UNKNOWN_KEY -> R.string.backup_issue_unknown_key
            BackupIssueKind.UNKNOWN_FIELD -> R.string.backup_issue_unknown_field
            BackupIssueKind.MISSING_VALUE -> R.string.backup_issue_missing_value
            BackupIssueKind.INVALID_VALUE -> R.string.backup_issue_invalid_value
            BackupIssueKind.UNSUPPORTED_FORMAT -> R.string.backup_issue_unsupported_format
            BackupIssueKind.DUPLICATE_FIELD -> R.string.backup_issue_duplicate_field
            BackupIssueKind.MISSING_GROUP -> R.string.backup_issue_missing_group
            BackupIssueKind.CURRENT_VALUE_UNAVAILABLE -> R.string.backup_issue_current_unavailable
        }
        val subject = if (issue.kind == BackupIssueKind.MISSING_GROUP) {
            runCatching {
                getString(SettingsDisplay.groupTitleResId(SettingsGroup.valueOf(issue.detail)))
            }.getOrDefault(issue.detail)
        } else {
            issue.path
        }
        return getString(label, subject)
    }

    private fun renderReview() {
        reviewList.removeAllViews()
        restoreDiff?.changes.orEmpty().forEach { change ->
            addIssueLine(
                reviewList,
                SettingsDisplay.buildChangeSummary(
                    change.field,
                    change.oldRaw,
                    change.newRaw,
                    ::getString
                )
            )
        }
        if (parsedBackup != null && restoreDiff?.changes?.isEmpty() == true) {
            addIssueLine(reviewList, getString(R.string.backup_no_changes))
        }
        reviewSection.visibility = if (parsedBackup == null) View.GONE else View.VISIBLE
    }

    private fun showRestoreConfirmation() {
        val changes = restoreDiff?.changes.orEmpty()
        if (changes.isEmpty() || !writeReady() || applying) return
        val summaries = changes.joinToString("\n") { change ->
            SettingsDisplay.buildChangeSummary(
                change.field,
                change.oldRaw,
                change.newRaw,
                ::getString
            )
        }
        val message = summaries + "\n\n" + getString(R.string.settings_confirm_warning) +
            "\n\n" + getString(R.string.settings_confirm_rule) +
            "\n\n" + getString(R.string.backup_restore_bulk_warning)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_restore_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.backup_restore_apply) { _, _ -> applyRestore() }
            .show()
    }

    private fun applyRestore() {
        val diff = restoreDiff ?: return
        if (diff.changes.isEmpty() || !writeReady()) return
        val current = currentRawValues()
        val changes = diff.changes.map { FieldChange(it.field, it.newRaw, current) }
        applying = true
        lastResult = null
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.applyChanges(changes)
            lastResult = result
            applying = false
            if (result.outcome is WriteSessionOutcome.Committed) {
                parsedBackup = null
                restoreDiff = null
                readConfiguration(clearImported = false)
            } else {
                render()
            }
        }
    }

    private fun renderResult() {
        val result = lastResult
        resultSection.visibility = if (result == null) View.GONE else View.VISIBLE
        if (result == null) return
        resultOutcome.setText(SettingsDisplay.outcomeLabelResId(result.outcome))
        resultDetails.removeAllViews()
        result.changes.forEach { changeResult ->
            addIssueLine(
                resultDetails,
                getString(
                    R.string.settings_change_result,
                    getString(SettingsDisplay.fieldLabelResId(changeResult.change.field)),
                    getString(SettingsDisplay.detailLabelResId(changeResult.detail))
                ),
                changeResult.detail !is ChangeDetail.Verified &&
                    changeResult.detail !is ChangeDetail.SkippedNoChange
            )
        }
        when (val outcome = result.outcome) {
            is WriteSessionOutcome.ValidationFailed -> outcome.failures.forEach { failure ->
                val mapped = SettingsDisplay.validationMessage(
                    failure.change.field,
                    ValidationResult.Invalid(failure.reason),
                    ::getString
                ) ?: SettingsUiMessage(R.string.settings_validation_fallback, listOf(failure.reason))
                addIssueLine(
                    resultDetails,
                    getString(
                        R.string.settings_validation_failure,
                        getString(SettingsDisplay.fieldLabelResId(failure.change.field)),
                        resolveMessage(mapped)
                    ),
                    true
                )
            }
            is WriteSessionOutcome.EntryFailed -> addIssueLine(
                resultDetails,
                getString(R.string.settings_entry_error, registerErrorText(outcome.error)),
                true
            )
            else -> Unit
        }

        result.postCommitConfirmation?.let { confirmation ->
            addIssueLine(
                resultDetails,
                getString(
                    R.string.settings_confirmation_matches,
                    confirmation.values.size - confirmation.mismatched.size
                )
            )
            if (confirmation.mismatched.isNotEmpty()) {
                addIssueLine(
                    resultDetails,
                    getString(
                        R.string.settings_confirmation_mismatches,
                        confirmation.mismatched.joinToString {
                            getString(SettingsDisplay.fieldLabelResId(it))
                        }
                    ),
                    true
                )
            }
        } ?: if (result.outcome is WriteSessionOutcome.Committed) {
            addIssueLine(resultDetails, getString(R.string.settings_confirmation_unavailable), true)
        } else {
            Unit
        }

        result.errorCountersBeforeCommit?.let {
            addIssueLine(resultDetails, getString(R.string.settings_counter_snapshot_available, it.size))
        }
        result.warnings.forEach { warning ->
            addIssueLine(
                resultDetails,
                getString(SettingsDisplay.warningLabelResId(warning)),
                true
            )
        }
    }

    private fun registerErrorText(error: RegisterAccessError): String {
        val label = getString(SettingsDisplay.registerErrorLabelResId(error))
        return when (error) {
            is RegisterAccessError.ErrorStatus -> "$label (0x${error.status.toString(16).uppercase()})"
            is RegisterAccessError.TransportFailure -> "$label: ${error.message}"
            is RegisterAccessError.Malformed -> "$label: ${error.reason}"
            RegisterAccessError.NoResponse -> label
        }
    }

    private fun addIssueLine(container: LinearLayout, value: String, isError: Boolean = false) {
        container.addView(TextView(requireContext()).apply {
            text = value
            setTextAppearance(R.style.TextAppearance_OpenJbd_ListItemSubtitle)
            if (isError) setTextColor(requireContext().getColor(R.color.error))
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_4), 0, 0)
        })
    }

    private fun resolveDisplay(value: SettingsDisplayValue): String = when (val text = value.text) {
        is SettingsDisplayText.NumberWithUnit -> text.number + " " + getString(text.unitStringResId)
        is SettingsDisplayText.Resource -> getString(text.stringResId)
    }

    private fun resolveMessage(message: SettingsUiMessage): String =
        getString(message.stringResId, *message.formatArgs.toTypedArray())

    private fun showOperationError(messageResId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_operation_failed_title)
            .setMessage(messageResId)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun utcTimestamp(millis: Long): String =
        SimpleDateFormat(UTC_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))

    private fun localTimestamp(millis: Long): String =
        SimpleDateFormat(LOCAL_PATTERN, Locale.getDefault()).format(Date(millis))

    private companion object {
        const val BACKUP_FILENAME_PATTERN = "yyyyMMdd-HHmm"
        const val UTC_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val LOCAL_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }
}
