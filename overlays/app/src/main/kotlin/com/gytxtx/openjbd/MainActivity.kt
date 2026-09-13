package com.gytxtx.openjbd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var connected = false
    private var currentPage = -1
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView

    @Inject lateinit var connectionManager: BmsConnectionManager
    @Inject lateinit var repository: BmsRepository

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
        maybeAutoConnect()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { renderState(it) }
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
        rememberDevice(address, name)
        connectionManager.connect(address, name ?: address)
    }

    private fun buildUi() {
        setContentView(R.layout.activity_main)
        SystemBars.applyAppBars(this)
        toolbar = findViewById(R.id.top_app_bar)
        toolbar.setNavigationIconTint(getColor(R.color.on_primary))
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        toolbar.setNavigationOnClickListener { openDeviceList() }
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
        PAGE_DETAIL -> ParametersFragment()
        PAGE_BALANCE -> ComingSoonFragment.newInstance(R.string.balance_title)
        PAGE_CONTROL -> ComingSoonFragment.newInstance(R.string.control_title)
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

    private fun renderState(snapshot: BmsUiState) { connected = snapshot.connected; updateToolbar() }

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
    }

    private fun openDeviceList() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, DeviceListActivity::class.java), REQUEST_SELECT_DEVICE)
    }

    private fun maybeAutoConnect() {
        val prefs = AppSettings.prefs(this)
        if (!AppSettings.autoConnectEnabled(this)) return
        val address = prefs.getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, "") ?: ""
        if (address.isEmpty()) {
            repository.update(BmsUiState.withConnectionState(ConnectionState.INVALID_DEVICE, false, null, null, getString(R.string.status_auto_connect_no_device), null, null))
            return
        }
        val name = prefs.getString(AppSettings.PREF_LAST_DEVICE_NAME, address) ?: address
        connectionManager.connect(address, if (name.isEmpty()) address else name)
    }

    private fun rememberDevice(address: String, name: String?) {
        AppSettings.prefs(this).edit().putString(AppSettings.PREF_LAST_DEVICE_ADDRESS, address).putString(AppSettings.PREF_LAST_DEVICE_NAME, name ?: address).apply()
        configureAutoReconnect()
    }

    private fun configureAutoReconnect() {
        val prefs = AppSettings.prefs(this)
        val address = prefs.getString(AppSettings.PREF_LAST_DEVICE_ADDRESS, "") ?: ""
        connectionManager.setAutoReconnect(AppSettings.autoConnectEnabled(this), address, prefs.getString(AppSettings.PREF_LAST_DEVICE_NAME, address))
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
