package com.gytxtx.openjbd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import com.gytxtx.openjbd.history.HistoryStore
import com.gytxtx.openjbd.registry.BmsDeviceRegistry
import com.gytxtx.openjbd.registry.BmsReachability
import com.gytxtx.openjbd.registry.Reachability
import com.gytxtx.openjbd.registry.ReachabilityScanner
import com.gytxtx.openjbd.registry.RegisteredBms
import com.gytxtx.openjbd.registry.RegistryEdit
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var connected = false
    private var currentPage = -1
    private var latestSnapshot: BmsUiState? = null
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView
    private var bmsStatusText: TextView? = null

    @Inject lateinit var connectionManager: BmsConnectionManager
    @Inject lateinit var repository: BmsRepository
    @Inject lateinit var deviceRegistry: BmsDeviceRegistry
    @Inject lateinit var historyStore: HistoryStore
    private var reachabilityScanner: ReachabilityScanner? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.preferredContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyThemePreference(this)
        super.onCreate(savedInstanceState)
        connectionManager.refreshLocalizedStatus()
        connectionManager.setRefreshInterval(AppSettings.refreshIntervalMs(this))
        configureAutoReconnect()
        buildUi()
        updateToolbar()
        val page = savedInstanceState?.getInt(STATE_CURRENT_PAGE, PAGE_OVERVIEW) ?: PAGE_OVERVIEW
        val itemId = navItemId(page)
        if (bottomNavigationView.selectedItemId == itemId) showPage(page) else bottomNavigationView.selectedItemId = itemId
        // Only on a cold start: recreation (rotation, language change) must not tear down a live
        // BLE link, which would abort an in-flight settings session mid-write.
        if (savedInstanceState == null) maybeAutoConnect()
        // One collector per Activity instance; repeatOnLifecycle restarts it on every STARTED.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                deviceRegistry.state.collect { updateBmsStatus(latestSnapshot) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_PAGE, if (currentPage < 0) PAGE_OVERVIEW else currentPage)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SELECT_DEVICE || resultCode != RESULT_OK || data == null) return
        val address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS) ?: run {
            toast(getString(R.string.status_bluetooth_unavailable)); return
        }
        val name = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_NAME)
        if (deviceRegistry.registerAndSelect(address, name) is RegistryEdit.Rejected) {
            toast(getString(R.string.status_auto_connect_invalid_device)); return
        }
        connectSelected()
    }

    private fun buildUi() {
        setContentView(R.layout.activity_main)
        SystemBars.applyAppBars(this)
        toolbar = findViewById(R.id.top_app_bar)
        toolbar.setNavigationIconTint(getColor(R.color.on_primary))
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bmsStatusText = findViewById(R.id.bms_status_text)

        toolbar.setNavigationOnClickListener { openBmsManager() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_disconnect -> { connectionManager.disconnect(); true }
                R.id.action_dashboard -> { startActivity(Intent(this, DashboardActivity::class.java)); true }
                else -> false
            }
        }
        for (i in 0 until toolbar.menu.size()) toolbar.menu.getItem(i)?.icon?.setTint(getColor(R.color.on_primary))
        bottomNavigationView.setOnItemSelectedListener { item ->
            val page = when (item.itemId) {
                R.id.nav_overview -> PAGE_OVERVIEW
                R.id.nav_detail -> PAGE_DETAIL
                R.id.nav_balance -> PAGE_BALANCE
                R.id.nav_control -> PAGE_CONTROL
                R.id.nav_settings -> PAGE_SETTINGS
                else -> return@setOnItemSelectedListener false
            }
            showPage(page); true
        }
    }

    private fun showPage(page: Int) {
        if (page == currentPage) return
        val previousPage = currentPage
        currentPage = page
        val tx = supportFragmentManager.beginTransaction()
        if (systemAnimationsEnabled()) tx.setCustomAnimations(R.anim.fragment_material_enter, R.anim.fragment_material_exit)
        var target = supportFragmentManager.findFragmentByTag(pageTag(page))
        if (target == null) {
            target = createPageFragment(page)
            tx.add(R.id.main_fragment_container, target, pageTag(page))
        }
        ALL_PAGES.forEach { hidePage(tx, it, page) }
        tx.show(target).commit()
        updateToolbar()
        if (previousPage >= 0) animateSelectedNavItem(page)
    }

    private fun createPageFragment(page: Int): Fragment = when (page) {
        PAGE_DETAIL -> DetailFragment()
        PAGE_BALANCE -> BalanceFragment()
        PAGE_CONTROL -> ControlFragment()
        PAGE_SETTINGS -> SettingsFragment()
        else -> OverviewFragment()
    }

    private fun hidePage(tx: FragmentTransaction, page: Int, visiblePage: Int) {
        if (page != visiblePage) supportFragmentManager.findFragmentByTag(pageTag(page))?.let { tx.hide(it) }
    }

    private fun pageTag(page: Int) = when (page) {
        PAGE_DETAIL -> TAG_DETAIL
        PAGE_BALANCE -> TAG_BALANCE
        PAGE_CONTROL -> TAG_CONTROL
        PAGE_SETTINGS -> TAG_SETTINGS
        else -> TAG_OVERVIEW
    }

    private fun navItemId(page: Int) = when (page) {
        PAGE_DETAIL -> R.id.nav_detail
        PAGE_BALANCE -> R.id.nav_balance
        PAGE_CONTROL -> R.id.nav_control
        PAGE_SETTINGS -> R.id.nav_settings
        else -> R.id.nav_overview
    }

    private fun animateSelectedNavItem(page: Int) {
        val itemView = bottomNavigationView.findViewById<View>(navItemId(page)) ?: return
        itemView.animate().cancel()
        if (!systemAnimationsEnabled()) { itemView.scaleX = 1f; itemView.scaleY = 1f; return }
        itemView.scaleX = 0.96f; itemView.scaleY = 0.96f
        itemView.animate().scaleX(1f).scaleY(1f).setDuration(PAGE_TRANSITION_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun systemAnimationsEnabled(): Boolean = animationsEnabled(
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    )

    private fun renderState(snapshot: BmsUiState) {
        latestSnapshot = snapshot
        connected = snapshot.connected
        updateToolbar()
        updateBmsStatus(snapshot)
    }

    private fun updateBmsStatus(snapshot: BmsUiState?) {
        val label = if (snapshot?.connected == true) {
            deviceRegistry.state.value.find(snapshot.deviceAddress)?.displayName
                ?: snapshot.deviceName ?: snapshot.deviceAddress ?: getString(R.string.bms_selector_disconnected)
        } else {
            getString(R.string.bms_selector_disconnected)
        }
        bmsStatusText?.text = label
    }

    private fun openBmsManager() {
        if (deviceRegistry.state.value.devices.isEmpty()) {
            openDeviceList()
            return
        }
        showRegisteredBmsSheet()
    }

    /** Phase B/C: registered-device list with selection, alias, removal and a short signal scan. */
    private fun showRegisteredBmsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_bms_selector, null)
        dialog.setContentView(view)
        val list = view.findViewById<LinearLayout>(R.id.bms_device_list)
        val scanStatus = view.findViewById<TextView>(R.id.bms_scan_status)
        val scanButton = view.findViewById<View>(R.id.bms_scan_button)
        val scanner = ReachabilityScanner(applicationContext, deviceRegistry).also { reachabilityScanner = it }

        fun render() {
            val registry = deviceRegistry.state.value
            val scan = deviceRegistry.scanStatus.value
            val sightings = deviceRegistry.scanResults.snapshot()
            list.removeAllViews()
            registry.devices.forEach { device ->
                list.addView(buildDeviceRow(dialog, list, device, registry.selectedAddress == device.address, sightings, scan.startedAtMillis))
            }
            scanStatus.text = getString(
                when {
                    scan.scanning -> R.string.bms_scan_running
                    scan.failed -> R.string.bms_scan_failed
                    scan.startedAtMillis != null -> R.string.bms_scan_done
                    else -> R.string.bms_scan_idle
                }
            )
            scanButton.isEnabled = !scan.scanning
        }

        scanButton.setOnClickListener {
            when (scanner.start()) {
                ReachabilityScanner.StartResult.STARTED -> Unit
                ReachabilityScanner.StartResult.BLUETOOTH_OFF -> scanStatus.setText(R.string.status_bluetooth_off)
                ReachabilityScanner.StartResult.PERMISSION_REQUIRED -> scanStatus.setText(R.string.bms_scan_permission_required)
                ReachabilityScanner.StartResult.UNAVAILABLE -> scanStatus.setText(R.string.status_ble_scanner_unavailable)
            }
        }
        view.findViewById<View>(R.id.bms_add_button).setOnClickListener {
            dialog.dismiss()
            openDeviceList()
        }
        val jobs = mutableListOf<Job>()
        jobs += lifecycleScope.launch { deviceRegistry.state.collect { render() } }
        jobs += lifecycleScope.launch { deviceRegistry.scanStatus.collect { render() } }
        jobs += lifecycleScope.launch { repository.uiState.collect { render() } }
        dialog.setOnDismissListener {
            jobs.forEach { it.cancel() }
            scanner.stop()
            if (reachabilityScanner === scanner) reachabilityScanner = null
        }
        dialog.show()
    }

    private fun buildDeviceRow(
        dialog: BottomSheetDialog,
        parent: LinearLayout,
        device: RegisteredBms,
        selected: Boolean,
        sightings: Map<String, com.gytxtx.openjbd.registry.ScanSighting>,
        scanStartedAtMillis: Long?
    ): View {
        val row = layoutInflater.inflate(R.layout.row_registered_bms, parent, false)
        val signal = BmsReachability.resolve(device, latestSnapshot ?: repository.getSnapshot(), sightings, scanStartedAtMillis)
        val dot = row.findViewById<TextView>(R.id.bms_row_dot)
        dot.setTextColor(
            getColor(
                when (BmsReachability.color(signal.reachability)) {
                    BmsReachability.Color.GREEN -> R.color.bms_signal_reachable
                    BmsReachability.Color.RED -> R.color.bms_signal_failed
                    BmsReachability.Color.GRAY -> R.color.bms_signal_unknown
                }
            )
        )
        val stateText = getString(
            when (signal.reachability) {
                Reachability.CONNECTED -> R.string.bms_signal_connected
                Reachability.CONNECTING -> R.string.bms_signal_connecting
                Reachability.SEEN -> R.string.bms_signal_seen
                Reachability.NOT_SEEN -> R.string.bms_signal_not_seen
                Reachability.FAILED -> R.string.bms_signal_failed
            }
        )
        val signalText = buildString {
            if (selected) append(getString(R.string.bms_row_selected)).append(" · ")
            append(stateText)
            signal.rssi?.let { append(" · ").append(getString(R.string.bms_signal_rssi, it)) }
        }
        row.findViewById<TextView>(R.id.bms_row_name).text = device.displayName
        row.findViewById<TextView>(R.id.bms_row_address).text = device.address
        row.findViewById<TextView>(R.id.bms_row_signal).text = signalText
        val select = row.findViewById<View>(R.id.bms_row_select)
        select.contentDescription = "${device.displayName}, $signalText"
        select.setOnClickListener {
            dialog.dismiss()
            switchTo(device)
        }
        row.findViewById<View>(R.id.bms_row_edit).apply {
            contentDescription = getString(R.string.bms_row_edit_description, device.displayName)
            setOnClickListener { showDeviceActions(device) }
        }
        return row
    }

    /**
     * Design "Selecting a registered BMS": cancel pending reconnect, disconnect the current BMS,
     * set the selected device, connect it. The top-right status follows the repository state.
     */
    private fun switchTo(device: RegisteredBms) {
        connectionManager.cancelReconnect()
        connectionManager.disconnect()
        if (deviceRegistry.select(device.address) is RegistryEdit.Rejected) return
        connectSelected()
    }

    private fun connectSelected() {
        val selected = deviceRegistry.selected ?: return
        configureAutoReconnect()
        updateBmsStatus(latestSnapshot)
        connectionManager.connect(selected.address, selected.displayName)
    }

    private fun showDeviceActions(device: RegisteredBms) {
        MaterialAlertDialogBuilder(this)
            .setTitle(device.displayName)
            .setItems(arrayOf(getString(R.string.bms_action_rename), getString(R.string.bms_action_remove))) { _, which ->
                if (which == 0) showRenameDialog(device) else showRemoveDialog(device)
            }
            .show()
    }

    private fun showRenameDialog(device: RegisteredBms) {
        val content = layoutInflater.inflate(R.layout.dialog_bms_alias, null)
        val input = content.findViewById<EditText>(R.id.bms_alias_input)
        input.setText(device.alias)
        input.setSelection(input.text.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bms_rename_title)
            .setMessage(getString(R.string.bms_rename_message, device.bleName.ifBlank { device.address }))
            .setView(content)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.bms_rename_confirm) { _, _ ->
                when (deviceRegistry.rename(device.address, input.text.toString())) {
                    is RegistryEdit.Changed -> {
                        if (deviceRegistry.selected?.address == device.address) configureAutoReconnect()
                    }
                    is RegistryEdit.Rejected -> toast(getString(R.string.bms_rename_rejected))
                }
            }
            .show()
    }

    private fun showRemoveDialog(device: RegisteredBms) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.bms_remove_title, device.displayName))
            .setMessage(R.string.bms_remove_message)
            .setNegativeButton(R.string.control_cancel, null)
            .setPositiveButton(R.string.bms_remove_confirm) { _, _ ->
                val wasSelected = deviceRegistry.selected?.address == device.address
                if (deviceRegistry.remove(device.address) is RegistryEdit.Rejected) return@setPositiveButton
                if (wasSelected) {
                    // No silent switch to another BMS: nothing is selected until the user chooses.
                    connectionManager.cancelReconnect()
                    connectionManager.disconnect()
                    configureAutoReconnect()
                }
                lifecycleScope.launch { runCatching { historyStore.clearDevice(device.address) } }
            }
            .show()
    }

    private fun updateToolbar() {
        toolbar.title = when (currentPage) {
            PAGE_DETAIL -> getString(R.string.detail_title)
            PAGE_BALANCE -> getString(R.string.balance_title)
            PAGE_CONTROL -> getString(R.string.control_title)
            PAGE_SETTINGS -> getString(R.string.settings_title)
            else -> getString(R.string.overview_title)
        }
        toolbar.setNavigationIcon(R.drawable.ic_list_24)
        toolbar.setNavigationIconTint(getColor(R.color.on_primary))
        toolbar.menu.findItem(R.id.action_disconnect)?.isVisible = currentPage == PAGE_OVERVIEW && connected
        toolbar.menu.findItem(R.id.action_dashboard)?.isVisible = currentPage == PAGE_OVERVIEW && connected
        updateBmsStatus(latestSnapshot)
    }

    private fun openDeviceList() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, DeviceListActivity::class.java), REQUEST_SELECT_DEVICE)
    }

    /** Only the last explicitly selected BMS is eligible for automatic reconnect at startup. */
    private fun maybeAutoConnect() {
        if (!AppSettings.autoConnectEnabled(this)) return
        val selected = deviceRegistry.selected
        if (selected == null) {
            repository.update(BmsUiState.withConnectionState(ConnectionState.INVALID_DEVICE, false, null, null, getString(R.string.status_auto_connect_no_device), null, null))
            return
        }
        connectionManager.connect(selected.address, selected.displayName)
    }

    private fun configureAutoReconnect() {
        val selected = deviceRegistry.selected
        connectionManager.setAutoReconnect(AppSettings.autoConnectEnabled(this), selected?.address, selected?.displayName)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_SELECT_DEVICE = 101
        private const val STATE_CURRENT_PAGE = "current_page"
        private const val PAGE_OVERVIEW = 0
        private const val PAGE_DETAIL = 1
        private const val PAGE_BALANCE = 2
        private const val PAGE_CONTROL = 3
        private const val PAGE_SETTINGS = 4
        private const val PAGE_TRANSITION_MS = 160L
        private const val TAG_OVERVIEW = "overview"
        private const val TAG_DETAIL = "detail"
        private const val TAG_BALANCE = "balance"
        private const val TAG_CONTROL = "control"
        private const val TAG_SETTINGS = "settings"
        private val ALL_PAGES = intArrayOf(PAGE_OVERVIEW, PAGE_DETAIL, PAGE_BALANCE, PAGE_CONTROL, PAGE_SETTINGS)
        @JvmStatic fun animationsEnabled(animatorDurationScale: Float): Boolean = animatorDurationScale > 0f
    }
}
