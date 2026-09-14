package com.gytxtx.openjbd.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
class SettingsGroupDialogFragment : DialogFragment() {
    @Inject lateinit var repository: SettingsRepository
    @Inject lateinit var bmsRepository: BmsRepository

    private val group: SettingsGroup by lazy {
        SettingsGroup.valueOf(requireArguments().getString(ARG_GROUP).orEmpty())
    }
    private val groupFields: List<SettingField> by lazy {
        SettingField.values().filter { it.group == group }
    }
    private val stagedValues = linkedMapOf<SettingField, Int>()

    private lateinit var root: View
    private lateinit var refreshButton: MaterialButton
    private lateinit var accessModeView: TextView
    private lateinit var lastUpdatedView: TextView
    private lateinit var progressContainer: View
    private lateinit var progressText: TextView
    private lateinit var readOnlyNotice: TextView
    private lateinit var readIssues: LinearLayout
    private lateinit var fieldsList: LinearLayout
    private lateinit var stagedSection: View
    private lateinit var stagedList: LinearLayout
    private lateinit var resultSection: View
    private lateinit var resultOutcome: TextView
    private lateinit var resultDetails: LinearLayout
    private lateinit var clearAllButton: MaterialButton
    private lateinit var applyButton: MaterialButton
    private var mosLiveCharge: TextView? = null
    private var mosLiveDischarge: TextView? = null

    private var latestState = SettingsState()
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
    ): View = inflater.inflate(R.layout.fragment_settings_group, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        root = view
        val toolbar = view.findViewById<MaterialToolbar>(R.id.settings_group_toolbar)
        refreshButton = view.findViewById(R.id.settings_refresh_button)
        accessModeView = view.findViewById(R.id.settings_access_mode)
        lastUpdatedView = view.findViewById(R.id.settings_last_updated)
        progressContainer = view.findViewById(R.id.settings_progress_container)
        progressText = view.findViewById(R.id.settings_progress_text)
        readOnlyNotice = view.findViewById(R.id.settings_read_only_notice)
        readIssues = view.findViewById(R.id.settings_read_issues)
        fieldsList = view.findViewById(R.id.settings_fields_list)
        stagedSection = view.findViewById(R.id.settings_staged_section)
        stagedList = view.findViewById(R.id.settings_staged_list)
        resultSection = view.findViewById(R.id.settings_result_section)
        resultOutcome = view.findViewById(R.id.settings_result_outcome)
        resultDetails = view.findViewById(R.id.settings_result_details)
        clearAllButton = view.findViewById(R.id.settings_clear_all_button)
        applyButton = view.findViewById(R.id.settings_apply_button)

        if (group == SettingsGroup.MOS) {
            val liveStatus = layoutInflater.inflate(R.layout.view_mos_live_status, null, false)
            mosLiveCharge = liveStatus.findViewById(R.id.mos_live_charge_status)
            mosLiveDischarge = liveStatus.findViewById(R.id.mos_live_discharge_status)
            (accessModeView.parent as ViewGroup).addView(liveStatus, 0)
            renderMosLiveStatus(bmsRepository.getSnapshot())
        }

        toolbar.setTitle(SettingsDisplay.groupTitleResId(group))
        toolbar.setNavigationOnClickListener { dismiss() }
        refreshButton.setOnClickListener { readGroup() }
        clearAllButton.setOnClickListener {
            stagedValues.clear()
            render(latestState)
        }
        applyButton.setOnClickListener { showApplyConfirmation() }
        applySystemBarInsets(view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect {
                    latestState = it
                    render(it)
                }
            }
        }
        if (group == SettingsGroup.MOS) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    bmsRepository.uiState.collect(::renderMosLiveStatus)
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
        if (!applying) repository.readGroups(listOf(group))
    }

    private fun render(state: SettingsState) {
        val section = state.sections.getValue(group)
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
            state.notConnected,
            section.accessMode,
            section.values.isNotEmpty()
        )
        readOnlyNotice.visibility = if (notice == null) View.GONE else View.VISIBLE
        notice?.let { readOnlyNotice.setText(SettingsDisplay.readOnlyNoticeResId(it)) }

        discardUnavailableStaging(state, section)
        renderReadIssues(state, section)
        renderFields(state, section)
        renderStaged(section)
        renderResult()

        val canApply = stagedValues.isNotEmpty() &&
            section.accessMode == SettingsAccessMode.FACTORY &&
            !section.loading && !applying && !state.notConnected
        applyButton.isEnabled = canApply
        clearAllButton.isEnabled = stagedValues.isNotEmpty() && !applying
    }

    private fun discardUnavailableStaging(
        state: SettingsState,
        section: SettingsSectionState
    ) {
        if (state.notConnected) {
            stagedValues.clear()
            return
        }
        stagedValues.entries.removeAll { (field, raw) ->
            !SettingsDisplay.isEditable(
                field,
                section.accessMode,
                section.values,
                section.registerErrors
            ) || section.values[field]?.raw == raw
        }
    }

    private fun renderReadIssues(state: SettingsState, section: SettingsSectionState) {
        readIssues.removeAllViews()
        state.lastError?.takeIf {
            it.isNotBlank() && !applying && lastResult == null && !state.notConnected &&
                section.values.isEmpty() && section.registerErrors.isEmpty()
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

    private fun renderFields(state: SettingsState, section: SettingsSectionState) {
        fieldsList.removeAllViews()
        groupFields.forEach { field ->
            val row = layoutInflater.inflate(R.layout.row_settings_field, fieldsList, false)
            val label = row.findViewById<TextView>(R.id.settings_field_label)
            val value = row.findViewById<TextView>(R.id.settings_field_value)
            val error = row.findViewById<TextView>(R.id.settings_field_error)
            val chevron = row.findViewById<ImageView>(R.id.settings_field_chevron)
            label.setText(SettingsDisplay.fieldLabelResId(field))

            val fieldValue = section.values[field]
            val resolvedValue = fieldValue?.let {
                resolveDisplay(SettingsDisplay.displayValue(field, it.raw))
            }
            value.text = when {
                resolvedValue == null -> getString(R.string.settings_value_unread)
                field == SettingField.BAL_WINDOW -> getString(
                    R.string.settings_balance_window_row_value,
                    resolvedValue
                )
                else -> resolvedValue
            }
            val registerError = section.registerErrors[field.register.address]
            error.visibility = if (registerError == null) View.GONE else View.VISIBLE
            if (registerError != null) error.text = registerErrorText(registerError)

            val editable = SettingsDisplay.isEditable(
                field,
                section.accessMode,
                section.values,
                section.registerErrors
            ) && !state.notConnected && !applying
            row.isEnabled = editable
            row.isClickable = editable
            row.isFocusable = editable
            row.alpha = if (editable) 1f else 0.62f
            chevron.visibility = if (editable) View.VISIBLE else View.GONE
            if (editable && fieldValue != null) {
                row.setOnClickListener { showEditDialog(field, fieldValue.raw, section) }
            }
            fieldsList.addView(row)
        }
    }

    private fun showEditDialog(
        field: SettingField,
        currentRaw: Int,
        section: SettingsSectionState
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_settings_edit, null, false)
        val inputLayout = content.findViewById<TextInputLayout>(R.id.settings_edit_input_layout)
        val input = content.findViewById<TextInputEditText>(R.id.settings_edit_input)
        val toggle = content.findViewById<SwitchMaterial>(R.id.settings_edit_toggle)
        val balanceControls = content.findViewById<View>(R.id.settings_edit_balance_controls)
        val decreaseButton = content.findViewById<MaterialButton>(R.id.settings_balance_decrease)
        val increaseButton = content.findViewById<MaterialButton>(R.id.settings_balance_increase)
        val preset5Button = content.findViewById<MaterialButton>(R.id.settings_balance_preset_5)
        val preset10Button = content.findViewById<MaterialButton>(R.id.settings_balance_preset_10)
        val preset15Button = content.findViewById<MaterialButton>(R.id.settings_balance_preset_15)
        val preset20Button = content.findViewById<MaterialButton>(R.id.settings_balance_preset_20)
        val display = SettingsDisplay.displayValue(field, stagedValues[field] ?: currentRaw)
        val isBit = field.bitIndex != null
        val isBalanceWindow = field == SettingField.BAL_WINDOW

        inputLayout.visibility = if (isBit) View.GONE else View.VISIBLE
        toggle.visibility = if (isBit) View.VISIBLE else View.GONE
        balanceControls.visibility = if (isBalanceWindow) View.VISIBLE else View.GONE
        if (isBit) {
            toggle.isChecked = (stagedValues[field] ?: currentRaw) == 1
            toggle.setText(
                SettingsDisplay.toggleValueLabelResId(
                    field,
                    SettingsDisplay.rawFromToggle(toggle.isChecked)
                )
            )
            toggle.setOnCheckedChangeListener { button, checked ->
                button.setText(SettingsDisplay.toggleValueLabelResId(
                    field,
                    SettingsDisplay.rawFromToggle(checked)
                ))
            }
        } else {
            input.inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            input.setText(display.inputText)
            inputLayout.suffixText = display.unitStringResId?.let(::getString)
        }

        if (isBalanceWindow) {
            fun setBalanceWindowInput(rawValue: Int) {
                input.setText(SettingsDisplay.displayValue(field, rawValue).inputText)
                input.setSelection(input.text?.length ?: 0)
                inputLayout.error = null
            }

            fun validatedBalanceWindowInput(): Int? {
                val parsed = SettingsDisplay.parseInput(field, input.text?.toString().orEmpty())
                if (parsed is SettingsInputResult.Invalid) {
                    inputLayout.error = resolveMessage(parsed.message)
                    return null
                }
                val rawValue = (parsed as SettingsInputResult.Valid).rawValue
                val message = SettingsDisplay.validationMessage(
                    field,
                    validateField(field, rawValue),
                    ::getString
                )
                if (message != null) {
                    inputLayout.error = resolveMessage(message)
                    return null
                }
                inputLayout.error = null
                return rawValue
            }

            decreaseButton.setOnClickListener {
                validatedBalanceWindowInput()?.let { rawValue ->
                    setBalanceWindowInput(stepBalanceWindow(rawValue, increase = false))
                }
            }
            increaseButton.setOnClickListener {
                validatedBalanceWindowInput()?.let { rawValue ->
                    setBalanceWindowInput(stepBalanceWindow(rawValue, increase = true))
                }
            }
            listOf(
                preset5Button to 5,
                preset10Button to 10,
                preset15Button to 15,
                preset20Button to 20
            ).forEach { (button, rawValue) ->
                button.setOnClickListener { setBalanceWindowInput(rawValue) }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(
                    R.string.settings_edit_title,
                    getString(SettingsDisplay.fieldLabelResId(field))
                )
            )
            .setView(content)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.settings_apply, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val candidate = if (isBit) {
                    SettingsInputResult.Valid(SettingsDisplay.rawFromToggle(toggle.isChecked))
                } else {
                    val entered = input.text?.toString().orEmpty()
                    if (entered.trim() == display.inputText) {
                        SettingsInputResult.Valid(stagedValues[field] ?: currentRaw)
                    } else {
                        SettingsDisplay.parseInput(field, entered)
                    }
                }
                if (candidate is SettingsInputResult.Invalid) {
                    inputLayout.error = resolveMessage(candidate.message)
                    return@setOnClickListener
                }

                val candidateRaw = (candidate as SettingsInputResult.Valid).rawValue
                val mergedValues = section.values.mapValues { it.value.raw } +
                    stagedValues + (field to candidateRaw)
                val validation = validateField(field, candidateRaw, mergedValues)
                val validationMessage = SettingsDisplay.validationMessage(
                    field,
                    validation,
                    ::getString
                )
                if (validationMessage != null) {
                    inputLayout.visibility = View.VISIBLE
                    inputLayout.error = resolveMessage(validationMessage)
                    return@setOnClickListener
                }

                if (candidateRaw == currentRaw) stagedValues.remove(field)
                else stagedValues[field] = candidateRaw
                dialog.dismiss()
                render(latestState)
            }
        }
        dialog.show()
    }

    private fun renderStaged(section: SettingsSectionState) {
        stagedList.removeAllViews()
        stagedValues.forEach { (field, newRaw) ->
            val oldRaw = section.values.getValue(field).raw
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_4))
            }
            row.addView(TextView(requireContext()).apply {
                text = SettingsDisplay.buildChangeSummary(
                    field,
                    oldRaw,
                    newRaw,
                    ::getString
                )
                setTextAppearance(R.style.TextAppearance_OpenJbd_ListItemSubtitle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialButton(requireContext()).apply {
                text = getString(R.string.settings_remove)
                isAllCaps = false
                setOnClickListener {
                    stagedValues.remove(field)
                    render(latestState)
                }
            })
            stagedList.addView(row)
        }
        stagedSection.visibility = if (stagedValues.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showApplyConfirmation() {
        val section = latestState.sections.getValue(group)
        if (stagedValues.isEmpty() || applying) return
        val summaries = stagedValues.map { (field, raw) ->
            SettingsDisplay.buildChangeSummary(
                field,
                section.values.getValue(field).raw,
                raw,
                ::getString
            )
        }
        val groupWarning = if (group == SettingsGroup.MOS) {
            "\n\n" + getString(R.string.mos_confirm_warning)
        } else {
            ""
        }
        val message = summaries.joinToString("\n") + "\n\n" +
            getString(R.string.settings_confirm_warning) + groupWarning + "\n\n" +
            getString(R.string.settings_confirm_rule)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.settings_apply) { _, _ -> applyChanges() }
            .show()
    }

    private fun applyChanges() {
        val section = latestState.sections.getValue(group)
        val currentRaws = section.values.mapValues { it.value.raw }
        val changes = stagedValues.map { (field, rawValue) ->
            FieldChange(field, rawValue, currentRaws)
        }
        if (changes.isEmpty()) return

        applying = true
        lastResult = null
        render(latestState)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.applyChanges(changes)
            lastResult = result
            applying = false
            if (result.outcome is WriteSessionOutcome.Committed) {
                stagedValues.clear()
                repository.readGroups(listOf(group))
            }
            render(latestState)
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
                    getString(SettingsDisplay.fieldLabelResId(changeResult.change.field)),
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
                        getString(SettingsDisplay.fieldLabelResId(failure.change.field)),
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
                        confirmation.mismatched.joinToString { field ->
                            getString(SettingsDisplay.fieldLabelResId(field))
                        }
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
            is RegisterAccessError.ErrorStatus -> "$label (0x${error.status.toString(16).uppercase()})"
            is RegisterAccessError.TransportFailure -> "$label: ${error.message}"
            is RegisterAccessError.Malformed -> "$label: ${error.reason}"
            RegisterAccessError.NoResponse -> label
        }
    }

    private fun resolveDisplay(value: SettingsDisplayValue): String = when (val text = value.text) {
        is SettingsDisplayText.NumberWithUnit ->
            text.number + " " + getString(text.unitStringResId)
        is SettingsDisplayText.Resource -> getString(text.stringResId)
    }

    private fun resolveMessage(message: SettingsUiMessage): String =
        getString(message.stringResId, *message.formatArgs.toTypedArray())

    private fun renderMosLiveStatus(state: BmsUiState) {
        val info = state.basicInfo
        val fetState = if (info == null) {
            null
        } else {
            (if (info.chargeEnabled) 1 else 0) or (if (info.dischargeEnabled) 2 else 0)
        }
        fun status(bitIndex: Int): String = if (fetState == null) {
            getString(R.string.mos_live_unavailable)
        } else if (MosStateMapping.isConducting(fetState, bitIndex)) {
            getString(R.string.mos_live_conducting)
        } else {
            getString(R.string.mos_live_blocked)
        }
        mosLiveCharge?.text = getString(R.string.mos_live_charge, status(0))
        mosLiveDischarge?.text = getString(R.string.mos_live_discharge, status(1))
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(KST_TIME_ZONE)
        }.format(Date(millis))

    companion object {
        private const val ARG_GROUP = "settings_group"
        private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
        private const val KST_TIME_ZONE = "Asia/Seoul"

        fun newInstance(group: SettingsGroup) = SettingsGroupDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP, group.name) }
        }
    }
}
