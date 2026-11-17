package com.gytxtx.openjbd.ui

import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gytxtx.openjbd.BalanceFragment
import com.gytxtx.openjbd.ControlFragment
import com.gytxtx.openjbd.DetailFragment
import com.gytxtx.openjbd.MainActivity
import com.gytxtx.openjbd.OverviewFragment
import com.gytxtx.openjbd.R
import com.gytxtx.openjbd.SettingsFragment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Screen-level smoke tests (mobile dev standard 5.3, issue #9): the five tabs inflate and
 * render their disconnected state on the JVM, the Control tab starts locked, and a
 * recreation (rotation) keeps the selected tab. No BMS or BLE link is involved.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class MainScreensTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    private val tabs = listOf(
        R.id.nav_overview to OverviewFragment::class.java,
        R.id.nav_detail to DetailFragment::class.java,
        R.id.nav_balance to BalanceFragment::class.java,
        R.id.nav_control to ControlFragment::class.java,
        R.id.nav_settings to SettingsFragment::class.java,
    )

    private fun visibleFragment(activity: MainActivity): Fragment? =
        activity.supportFragmentManager.fragments.firstOrNull { it.isVisible }

    @Test
    fun bottomNavigationHasFiveTabsAndEachRendersItsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                assertEquals(5, nav.menu.size())
                for ((itemId, type) in tabs) {
                    nav.selectedItemId = itemId
                    shadowOf(Looper.getMainLooper()).idle()
                    val shown = visibleFragment(activity)
                    assertNotNull("no visible fragment for ${type.simpleName}", shown)
                    assertTrue("expected ${type.simpleName}, got ${shown!!.javaClass.simpleName}", type.isInstance(shown))
                    assertNotNull(shown.view)
                }
            }
        }
    }

    @Test
    fun overviewShowsConnectPlaceholderWhenDisconnected() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                val placeholder = activity.findViewById<View>(R.id.placeholder_connect)
                assertEquals(View.VISIBLE, placeholder.visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.content_connected_overview).visibility)
            }
        }
    }

    @Test
    fun controlTabStartsLocked() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_control
                shadowOf(Looper.getMainLooper()).idle()
                val control = visibleFragment(activity)!!.requireView()
                assertEquals(View.VISIBLE, control.findViewById<View>(R.id.control_locked_content).visibility)
                assertEquals(View.GONE, control.findViewById<View>(R.id.control_unlocked_content).visibility)
            }
        }
    }

    @Test
    fun recreationKeepsSelectedTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_balance
                shadowOf(Looper.getMainLooper()).idle()
            }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(R.id.nav_balance, activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId)
                assertTrue(visibleFragment(activity) is BalanceFragment)
            }
        }
    }
}
