package com.gytxtx.openjbd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.maintenance.MaintenanceGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ControlFragment : Fragment() {
    @Inject lateinit var repository: BmsRepository

    private val maintenanceGate = MaintenanceGate()
    private lateinit var lockedContent: View
    private lateinit var unlockedContent: View
    private lateinit var targetName: TextView
    private lateinit var targetAddress: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lockedContent = view.findViewById(R.id.control_locked_content)
        unlockedContent = view.findViewById(R.id.control_unlocked_content)
        targetName = view.findViewById(R.id.control_target_name)
        targetAddress = view.findViewById(R.id.control_target_address)

        view.findViewById<View>(R.id.control_unlock_button).setOnClickListener {
            showUnlockConfirmation()
        }
        view.findViewById<View>(R.id.control_relock_button).setOnClickListener {
            maintenanceGate.relock()
            renderGateState()
        }

        renderGateState()
        renderTarget(repository.getSnapshot())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderTarget(it) }
            }
        }
    }

    private fun showUnlockConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.control_unlock_dialog_title)
            .setMessage(R.string.control_unlock_dialog_message)
            .setNegativeButton(R.string.control_cancel) { _, _ ->
                maintenanceGate.requestUnlock(confirmed = false)
            }
            .setPositiveButton(R.string.control_unlock_confirm) { _, _ ->
                maintenanceGate.requestUnlock(confirmed = true)
                renderGateState()
            }
            .show()
    }

    private fun renderGateState() {
        lockedContent.visibility = if (maintenanceGate.isUnlocked) View.GONE else View.VISIBLE
        unlockedContent.visibility = if (maintenanceGate.isUnlocked) View.VISIBLE else View.GONE
    }

    private fun renderTarget(snapshot: BmsUiState) {
        val connectedName = snapshot.deviceName?.takeIf { it.isNotBlank() }
        val connectedAddress = snapshot.deviceAddress?.takeIf { it.isNotBlank() }
        val prefs = AppSettings.prefs(requireContext())
        val savedAddress = prefs.getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
        val savedName = prefs.getString(AppSettings.PREF_LAST_DEVICE_NAME, savedAddress)
            .orEmpty()
            .takeIf { it.isNotBlank() }

        val name: String
        val address: String?
        if (snapshot.connected && (connectedName != null || connectedAddress != null)) {
            name = connectedName ?: connectedAddress!!
            address = connectedAddress
        } else if (savedAddress != null) {
            name = savedName ?: savedAddress
            address = savedAddress
        } else {
            name = getString(R.string.bms_selector_disconnected)
            address = null
        }

        targetName.setTextIfChanged(name)
        targetAddress.visibility = if (address == null) View.GONE else View.VISIBLE
        if (address != null) targetAddress.setTextIfChanged(address)
    }
}
