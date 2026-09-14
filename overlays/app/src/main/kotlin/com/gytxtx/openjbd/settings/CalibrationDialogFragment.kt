package com.gytxtx.openjbd.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.gytxtx.openjbd.R
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.maintenance.ValidationResult
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CalibrationDialogFragment : DialogFragment() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var bmsRepository: BmsRepository

    private val group = SettingsGroup.CALIBRATION
    private val stagedValues = linkedMapOf<SettingField, Int>()

    private lateinit var root: View
    private lateinit var refreshButton: MaterialButton
    private lateinit var accessModeView: TextView
    private lateinit var lastUpdatedView: TextView
    private lateinit var progressContainer: View
    private lateinit var progressText: TextView
    private lateinit var readOnlyNotice: TextView
    private lateinit var readIssues: LinearLayout
    private lateinit var currentList: LinearLayout
    private lateinit var cellsCard: View
    private lateinit var cellsList: LinearLayout
    private lateinit var noCells: TextView
    private lateinit var ntcsCard: View
    private lateinit var ntcsList: LinearLayout
    private lateinit var noNtcs: TextView
    private lateinit var stagedSection: View
    private lateinit var stagedList: LinearLayout
    private lateinit var resultSection: View
    private lateinit var resultOutcome: TextView
    private lateinit var resultDetails: LinearLayout
    private lateinit var clearAllButton: MaterialButton
    private lateinit var applyButton: MaterialButton

    private var latestSettingsState = SettingsState()
    private var latestBmsState = BmsUiState.disconnected(null, null, "")
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
    ): View = inflater.inflate(R.layout.fragment_calibration, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        root = view
        view.findViewById<MaterialToolbar>(R.id.calibration_toolbar)
            .setNavigationOnClickListener { dismiss() }
        refreshButton = view.findViewById(R.id.calibration_refresh_button)
        accessModeView = view.findViewById(R.id.calibration_access_mode)
        lastUpdatedView = view.findViewById(R.id.calibration_last_updated)
        progressContainer = view.findViewById(R.id.calibration_progress_container)
        progressText = view.findViewById(R.id.calibration_progress_text)
        readOnlyNotice = view.findViewById(R.id.calibration_read_only_notice)
        readIssues = view.findViewById(R.id.calibration_read_issues)
        currentList = view.findViewById(R.id.calibration_current_list)
        cellsCard = view.findViewById(R.id.calibration_cells_card)
        cellsList = view.findViewById(R.id.calibration_cells_list)
        noCells = view.findViewById(R.id.calibration_no_cells)
        ntcsCard = view.findViewById(R.id.calibration_ntcs_card)
        ntcsList = view.findViewById(R.id.calibration_ntcs_list)
        noNtcs = view.findViewById(R.id.calibration_no_ntcs)
        stagedSection = view.findViewById(R.id.calibration_staged_section)
        stagedList = view.findViewById(R.id.calibration_staged_list)
        resultSection = view.findViewById(R.id.calibration_result_section)
        resultOutcome = view.findViewById(R.id.calibration_result_outcome)
        resultDetails = view.findViewById(R.id.calibration_result_details)
        clearAllButton = view.findViewById(R.id.calibration_clear_all_button)
        applyButton = view.findViewById(R.id.calibration_apply_button)

        refreshButton.setOnClickListener { readGroup() }
        clearAllButton.setOnClickListener {
            stagedValues.clear()
            render()
        }
        applyButton.setOnClickListener { showApplyConfirmation() }
        applySystemBarInsets(view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.state.collect {
                        latestSettingsState = it
                        render()
                    }
                }
                launch {
                    bmsRepository.uiState.collect {
                        latestBmsState = it
                        render()
                    }
                }
            }
        }
        readGroup()
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

    private fun readGroup() {
        if (!applying) settingsRepository.readGroups(listOf(group))
    }

    private fun render() {
        if (!::root.isInitialized) return
        val section = latestSettingsState.sections.getValue(group)
        accessModeView.text = getString(
            R.string.settings_access_mode,
            getString(SettingsDisplay.accessModeLabelResId(section.accessMode))
        )
        lastUpdatedView.text = section.lastUpdatedAtMillis?.let {
            getString(R.string.settings_last_updated, formatTimestamp(it))
        } ?: getString(R.string.settings_last_updated_never)

        progressContainer.visibility = if (section.loading || applying) View.VISIBLE else View.GONE
        progressText.setText(
            if (applying) R.string.settings_progress_applying else R.string.settings_loading
        )
        refreshButton.isEnabled = !section.loading && !applying

        val notice = SettingsDisplay.readOnlyNotice(
            latestSettingsState.notConnected,
            section.accessMode,
            section.values.isNotEmpty()
        )
        readOnlyNotice.visibility = if (notice == null) View.GONE else View.VISIBLE
        notice?.let { readOnlyNotice.setText(SettingsDisplay.readOnlyNoticeResId(it)) }

        val targets = visibleTargets()
        discardUnavailableStaging(section, targets)
        renderReadIssues(section)
        renderTargets(section, targets)
        renderStaged(section)
        renderResult()

        applyButton.isEnabled = stagedValues.isNotEmpty() &&
            section.accessMode == SettingsAccessMode.FACTORY &&
            !section.loading && !applying && !latestSettingsState.notConnected
        clearAllButton.isEnabled = stagedValues.isNotEmpty() && !applying
    }

    private fun visibleTargets(): List<CalibrationTarget> {
        val info = latestBmsState.basicInfo
        return CalibrationTargets.visibleTargets(
            cellCount = info?.cellCount,
            ntcCount = info?.ntcCount,
            observedCellCount = latestBmsState.cellVoltages?.cells?.size ?: 0,
            observedNtcCount = info?.temperaturesC?.size ?: 0
        )
    }

    private fun discardUnavailableStaging(
        section: SettingsSectionState,
        targets: List<CalibrationTarget>
    ) {
        if (latestSettingsState.notConnected || !latestBmsState.connected) {
            stagedValues.clear()
            return
        }
        val visibleFields = targets.mapTo(mutableSetOf()) { it.field }
        stagedValues.entries.removeAll { (field, raw) ->
            field !in visibleFields ||
                !SettingsDisplay.isEditable(
                    field,
                    section.accessMode,
                    section.values,
                    section.registerErrors
                ) || section.values[field]?.raw == raw
        }
    }

    private fun renderReadIssues(section: SettingsSectionState) {
        readIssues.removeAllViews()
        latestSettingsState.lastError?.takeIf {
            it.isNotBlank() && !applying && lastResult == null &&
                !latestSettingsState.notConnected && section.values.isEmpty() &&
                section.registerErrors.isEmpty()
        }?.let { error ->
            addIssueLine(
                readIssues,
                getString(R.string.settings_operation_error, error),
                isError = true
            )
        }
        if (section.registerErrors.isNotEmpty()) {
            addIssueHeading(readIssues, R.string.settings_register_errors_title)
            section.registerErrors.toSortedMap().forEach { (address, error) ->
                addIssueLine(
                    readIssues,
                    getString(
                        R.string.settings_register_error,
                        address,
                        registerErrorText(error)
                    ),
                    isError = true
                )
            }
        }
        if (section.warnings.isNotEmpty()) {
            addIssueHeading(readIssues, R.string.settings_read_warnings_title)
            section.warnings.forEach { warning ->
                addIssueLine(readIssues, getString(SettingsDisplay.warningLabelResId(warning)))
            }
        }
        readIssues.visibility = if (readIssues.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun renderTargets(
        section: SettingsSectionState,
        targets: List<CalibrationTarget>
    ) {
        val currents = targets.filter { it.kind in CURRENT_KINDS }
        val cells = targets.filter { it.kind == CalibrationKind.CELL_VOLTAGE }
        val ntcs = targets.filter { it.kind == CalibrationKind.NTC }
        renderTargetList(currentList, currents, section)
        renderTargetList(cellsList, cells, section)
        renderTargetList(ntcsList, ntcs, section)
        cellsCard.visibility = if (cells.isEmpty()) View.GONE else View.VISIBLE
        noCells.visibility = if (cells.isEmpty()) View.VISIBLE else View.GONE
        ntcsCard.visibility = if (ntcs.isEmpty()) View.GONE else View.VISIBLE
        noNtcs.visibility = if (ntcs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderTargetList(
        container: LinearLayout,
        targets: List<CalibrationTarget>,
        section: SettingsSectionState
    ) {
        container.removeAllViews()
        targets.forEach { target ->
            val row = layoutInflater.inflate(R.layout.row_calibration_target, container, false)
            row.findViewById<TextView>(R.id.calibration_target_label).text = fieldLabel(target.field)
            row.findViewById<TextView>(R.id.calibration_target_guidance)
                .setText(guidanceResId(target.guidanceKey))
            val live = liveMeasurement(target)
            row.findViewById<TextView>(R.id.calibration_target_live).text = getString(
                R.string.calibration_live_value,
                live?.let(::formatMeasurement)
                    ?: getString(R.string.calibration_live_unavailable)
            )
            val stored = section.values[target.field]?.raw?.let {
                resolveDisplay(SettingsDisplay.displayValue(target.field, it))
            } ?: getString(R.string.settings_value_unread)
            row.findViewById<TextView>(R.id.calibration_target_stored).text =
                getString(R.string.calibration_stored_value, stored)

            val registerError = section.registerErrors[target.field.register.address]
            row.findViewById<TextView>(R.id.calibration_target_error).apply {
                visibility = if (registerError == null) View.GONE else View.VISIBLE
                if (registerError != null) text = registerErrorText(registerError)
            }
            val editable = live != null && latestBmsState.connected &&
                !latestSettingsState.notConnected && !applying &&
                SettingsDisplay.isEditable(
                    target.field,
                    section.accessMode,
                    section.values,
                    section.registerErrors
                )
            row.alpha = if (editable) 1f else 0.62f
            row.findViewById<MaterialButton>(R.id.calibration_target_action).apply {
                setText(
                    if (target.kind == CalibrationKind.IDLE_CURRENT) {
                        R.string.calibration_zero_action
                    } else {
                        R.string.calibration_edit
                    }
                )
                isEnabled = editable
                setOnClickListener {
                    if (target.kind == CalibrationKind.IDLE_CURRENT) {
                        showIdleZeroConfirmation(target, section)
                    } else {
                        showReferenceInput(target, section)
                    }
                }
            }
            container.addView(row)
        }
    }

    private fun showIdleZeroConfirmation(
        target: CalibrationTarget,
        section: SettingsSectionState
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.calibration_zero_title)
            .setMessage(R.string.calibration_zero_warning)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.calibration_zero_confirm) { _, _ ->
                val validation = validateField(target.field, 0, currentRaws(section) + stagedValues)
                if (validation is ValidationResult.Valid) {
                    stageValue(target.field, 0, section)
                }
            }
            .show()
    }

    private fun showReferenceInput(
        target: CalibrationTarget,
        section: SettingsSectionState
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_calibration_input, null, false)
        val inputLayout = content.findViewById<TextInputLayout>(R.id.calibration_input_layout)
        val input = content.findViewById<TextInputEditText>(R.id.calibration_input_value)
        val operationView = content.findViewById<TextView>(R.id.calibration_input_operation)
        content.findViewById<TextView>(R.id.calibration_input_guidance)
            .setText(guidanceResId(target.guidanceKey))
        content.findViewById<TextView>(R.id.calibration_input_bms_value).text =
            liveMeasurement(target)?.let(::formatMeasurement)
                ?: getString(R.string.calibration_live_unavailable)
        content.findViewById<TextView>(R.id.calibration_input_note).setText(
            operationNoteResId(target.kind)
        )

        val currentRaw = section.values.getValue(target.field).raw
        val initialRaw = stagedValues[target.field] ?: currentRaw
        val display = SettingsDisplay.displayValue(target.field, initialRaw)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(display.inputText)
        inputLayout.suffixText = display.unitStringResId?.let(::getString)

        fun updateOperationPreview() {
            val candidate = parsedAndValidated(target.field, input.text?.toString().orEmpty(), section)
            val raw = (candidate as? SettingsInputResult.Valid)?.rawValue
            val concept = CalibrationTargets.buildConceptViewModel(
                target,
                storedCalibrationRaw = currentRaw,
                bmsReading = liveMeasurement(target),
                proposedRaw = raw
            )
            operationView.text = concept.resultingOperation?.let(::formatOperation)
                ?: getString(R.string.calibration_operation_waiting)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                inputLayout.error = null
                updateOperationPreview()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateOperationPreview()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.calibration_edit_title, fieldLabel(target.field)))
            .setView(content)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.settings_apply, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                when (
                    val candidate = parsedAndValidated(
                        target.field,
                        input.text?.toString().orEmpty(),
                        section
                    )
                ) {
                    is SettingsInputResult.Invalid ->
                        inputLayout.error = resolveMessage(candidate.message)
                    is SettingsInputResult.Valid -> {
                        stageValue(target.field, candidate.rawValue, section)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun parsedAndValidated(
        field: SettingField,
        input: String,
        section: SettingsSectionState
    ): SettingsInputResult {
        val parsed = SettingsDisplay.parseInput(field, input)
        if (parsed is SettingsInputResult.Invalid) return parsed
        val raw = (parsed as SettingsInputResult.Valid).rawValue
        val validation = validateField(field, raw, currentRaws(section) + stagedValues + (field to raw))
        val message = SettingsDisplay.validationMessage(field, validation, ::getString)
        return if (message == null) parsed else SettingsInputResult.Invalid(message)
    }

    private fun stageValue(field: SettingField, raw: Int, section: SettingsSectionState) {
        if (section.values.getValue(field).raw == raw) stagedValues.remove(field)
        else stagedValues[field] = raw
        render()
    }

    private fun renderStaged(section: SettingsSectionState) {
        stagedList.removeAllViews()
        stagedValues.forEach { (field, newRaw) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_4))
            }
            row.addView(TextView(requireContext()).apply {
                text = changeSummary(field, section.values.getValue(field).raw, newRaw)
                setTextAppearance(R.style.TextAppearance_OpenJbd_ListItemSubtitle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialButton(requireContext()).apply {
                text = getString(R.string.settings_remove)
                isAllCaps = false
                setOnClickListener {
                    stagedValues.remove(field)
                    render()
                }
            })
            stagedList.addView(row)
        }
        stagedSection.visibility = if (stagedValues.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showApplyConfirmation() {
        val section = latestSettingsState.sections.getValue(group)
        if (stagedValues.isEmpty() || applying) return
        val summaries = stagedValues.map { (field, raw) ->
            changeSummary(field, section.values.getValue(field).raw, raw)
        }
        val message = summaries.joinToString("\n") + "\n\n" +
            getString(R.string.settings_confirm_warning) + "\n\n" +
            getString(R.string.settings_confirm_rule) + "\n\n" +
            getString(R.string.calibration_confirm_line)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.settings_apply) { _, _ -> applyChanges() }
            .show()
    }

    private fun applyChanges() {
        val section = latestSettingsState.sections.getValue(group)
        val currentRaws = currentRaws(section)
        val changes = stagedValues.map { (field, rawValue) ->
            FieldChange(field, rawValue, currentRaws)
        }
        if (changes.isEmpty()) return

        applying = true
        lastResult = null
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = settingsRepository.applyChanges(changes)
            lastResult = result
            applying = false
            if (result.outcome is WriteSessionOutcome.Committed) {
                stagedValues.clear()
                settingsRepository.readGroups(listOf(group))
            }
            render()
        }
    }

    private fun renderResult() {
        val result = lastResult
        resultSection.visibility = if (result == null) View.GONE else View.VISIBLE
        if (result == null) return

        resultOutcome.setText(SettingsDisplay.outcomeLabelResId(result.outcome))
        resultDetails.removeAllViews()
        result.changes.forEach { changeResult ->
            addResultLine(
                getString(
                    R.string.settings_change_result,
                    fieldLabel(changeResult.change.field),
                    getString(SettingsDisplay.detailLabelResId(changeResult.detail))
                )
            )
        }
        when (val outcome = result.outcome) {
            is WriteSessionOutcome.ValidationFailed -> outcome.failures.forEach { failure ->
                val mapped = SettingsDisplay.validationMessage(
                    failure.change.field,
                    ValidationResult.Invalid(failure.reason),
                    ::getString
                ) ?: SettingsUiMessage(R.string.settings_validation_fallback, listOf(failure.reason))
                addResultLine(
                    getString(
                        R.string.settings_validation_failure,
                        fieldLabel(failure.change.field),
                        resolveMessage(mapped)
                    ),
                    isError = true
                )
            }
            is WriteSessionOutcome.EntryFailed -> addResultLine(
                getString(R.string.settings_entry_error, registerErrorText(outcome.error)),
                isError = true
            )
            else -> Unit
        }

        val confirmation = result.postCommitConfirmation
        if (confirmation != null) {
            addResultLine(
                getString(
                    R.string.settings_confirmation_matches,
                    confirmation.values.size - confirmation.mismatched.size
                )
            )
            if (confirmation.mismatched.isNotEmpty()) {
                addResultLine(
                    getString(
                        R.string.settings_confirmation_mismatches,
                        confirmation.mismatched.joinToString { fieldLabel(it) }
                    ),
                    isError = true
                )
            }
        } else if (result.outcome is WriteSessionOutcome.Committed) {
            addResultLine(getString(R.string.settings_confirmation_unavailable), isError = true)
        }

        result.errorCountersBeforeCommit?.let {
            addResultLine(getString(R.string.settings_counter_snapshot_available, it.size))
        } ?: addResultLine(getString(R.string.settings_counter_snapshot_unavailable), isError = true)

        if (result.warnings.isNotEmpty()) {
            addIssueHeading(resultDetails, R.string.settings_warnings_title)
            result.warnings.forEach { warning ->
                addResultLine(getString(SettingsDisplay.warningLabelResId(warning)), isError = true)
            }
        }
    }

    private fun liveMeasurement(target: CalibrationTarget): CalibrationMeasurement? {
        if (!latestBmsState.connected) return null
        val info = latestBmsState.basicInfo
        return when (target.kind) {
            CalibrationKind.IDLE_CURRENT,
            CalibrationKind.CHARGE_CURRENT,
            CalibrationKind.DISCHARGE_CURRENT -> info?.current?.toDouble()?.let {
                CalibrationMeasurement(it, CalibrationUnit.AMP)
            }
            CalibrationKind.CELL_VOLTAGE -> target.slotNumber?.let { slot ->
                latestBmsState.cellVoltages?.cells?.getOrNull(slot - 1)?.toDouble()?.let {
                    CalibrationMeasurement(it * 1_000.0, CalibrationUnit.MILLIVOLT)
                }
            }
            CalibrationKind.NTC -> target.slotNumber?.let { slot ->
                info?.temperaturesC?.getOrNull(slot - 1)?.toDouble()?.let {
                    CalibrationMeasurement(it, CalibrationUnit.CELSIUS)
                }
            }
        }
    }

    private fun formatMeasurement(measurement: CalibrationMeasurement): String {
        val decimals = when (measurement.unit) {
            CalibrationUnit.RAW, CalibrationUnit.MILLIVOLT -> 0
            CalibrationUnit.AMP -> 2
            CalibrationUnit.CELSIUS -> 1
        }
        val unit = when (measurement.unit) {
            CalibrationUnit.RAW -> getString(R.string.calibration_unit_raw)
            CalibrationUnit.AMP -> getString(R.string.settings_unit_amp)
            CalibrationUnit.MILLIVOLT -> getString(R.string.settings_unit_mv)
            CalibrationUnit.CELSIUS -> getString(R.string.settings_unit_celsius)
        }
        return "${formatNumber(measurement.value, decimals)} $unit"
    }

    private fun formatOperation(operation: CalibrationOperation): String = getString(
        R.string.calibration_operation_preview,
        operation.registerAddress,
        operation.encodedRaw and 0xFFFF,
        operation.encodedRaw
    )

    private fun operationNoteResId(kind: CalibrationKind): Int =
        if (kind == CalibrationKind.CHARGE_CURRENT || kind == CalibrationKind.DISCHARGE_CURRENT) {
            R.string.calibration_note_current_scale
        } else {
            R.string.calibration_note_hardware_unverified
        }

    private fun guidanceResId(key: CalibrationGuidanceKey): Int = when (key) {
        CalibrationGuidanceKey.IDLE_ZERO -> R.string.calibration_guidance_idle
        CalibrationGuidanceKey.CHARGE_REFERENCE -> R.string.calibration_guidance_charge
        CalibrationGuidanceKey.DISCHARGE_REFERENCE -> R.string.calibration_guidance_discharge
        CalibrationGuidanceKey.CELL_REFERENCE -> R.string.calibration_guidance_cell
        CalibrationGuidanceKey.NTC_REFERENCE -> R.string.calibration_guidance_ntc
    }

    private fun changeSummary(field: SettingField, oldRaw: Int, newRaw: Int): String =
        fieldLabel(field) + " " +
            resolveDisplay(SettingsDisplay.displayValue(field, oldRaw)) + " → " +
            resolveDisplay(SettingsDisplay.displayValue(field, newRaw))

    private fun fieldLabel(field: SettingField): String = getString(
        SettingsDisplay.fieldLabelResId(field),
        *SettingsDisplay.fieldLabelFormatArgs(field).toTypedArray()
    )

    private fun currentRaws(section: SettingsSectionState): Map<SettingField, Int> =
        section.values.mapValues { it.value.raw }

    private fun addResultLine(text: String, isError: Boolean = false) {
        addIssueLine(resultDetails, text, isError)
    }

    private fun addIssueHeading(container: LinearLayout, textResId: Int) {
        container.addView(TextView(requireContext()).apply {
            setText(textResId)
            setTextAppearance(R.style.TextAppearance_OpenJbd_ListItemTitle)
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_8), 0, 0)
        })
    }

    private fun addIssueLine(
        container: LinearLayout,
        textValue: String,
        isError: Boolean = false
    ) {
        container.addView(TextView(requireContext()).apply {
            text = textValue
            setTextAppearance(R.style.TextAppearance_OpenJbd_ListItemSubtitle)
            if (isError) setTextColor(requireContext().getColor(R.color.error))
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_4), 0, 0)
        })
    }

    private fun registerErrorText(error: RegisterAccessError): String {
        val label = getString(SettingsDisplay.registerErrorLabelResId(error))
        return when (error) {
            is RegisterAccessError.ErrorStatus ->
                "$label (0x${error.status.toString(16).uppercase()})"
            is RegisterAccessError.TransportFailure -> "$label: ${error.message}"
            is RegisterAccessError.Malformed -> "$label: ${error.reason}"
            RegisterAccessError.NoResponse -> label
        }
    }

    private fun resolveDisplay(value: SettingsDisplayValue): String = when (val text = value.text) {
        is SettingsDisplayText.NumberWithUnit -> text.number + " " + getString(text.unitStringResId)
        is SettingsDisplayText.Resource -> getString(text.stringResId)
    }

    private fun resolveMessage(message: SettingsUiMessage): String =
        getString(message.stringResId, *message.formatArgs.toTypedArray())

    private fun formatNumber(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = true
        }.format(value)

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(KST_TIME_ZONE)
        }.format(Date(millis))

    companion object {
        private val CURRENT_KINDS = setOf(
            CalibrationKind.IDLE_CURRENT,
            CalibrationKind.CHARGE_CURRENT,
            CalibrationKind.DISCHARGE_CURRENT
        )
        private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
        private const val KST_TIME_ZONE = "Asia/Seoul"

        fun newInstance() = CalibrationDialogFragment()
    }
}
