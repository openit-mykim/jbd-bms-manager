package com.gytxtx.openjbd

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal object SystemBars {
    @JvmStatic
    fun applyAppBars(activity: Activity) {
        val window = activity.window
        val darkTheme = isDarkTheme(activity)

        // Android 15/16 enforce edge-to-edge for modern target SDKs. Handle the
        // insets explicitly instead of relying on windowOptOutEdgeToEdgeEnforcement.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // OpenJBD uses different toolbar IDs for each standalone Activity.
        // Android 16 exposes the bug when only the main toolbar receives the
        // status-bar inset, so resolve every known app bar here.
        val topBar = findTopAppBar(activity)
        val bottomBar = activity.findViewById<View?>(R.id.bottom_navigation)
        val contentRoot = activity.findViewById<View>(android.R.id.content)

        if (topBar != null) {
            applyTopInset(topBar)
        }

        if (bottomBar != null) {
            applyBottomInset(bottomBar, resizeHeight = true)
        } else {
            applyBottomInset(contentRoot, resizeHeight = false)
        }

        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                val appearance = if (darkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                controller.setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                applyLegacyBarIcons(window, darkTheme)
            }
        } else {
            applyLegacyBarIcons(window, darkTheme)
        }
    }

    private fun findTopAppBar(activity: Activity): View? {
        val ids = intArrayOf(
            R.id.top_app_bar,
            R.id.device_top_app_bar,
            R.id.about_top_app_bar,
            R.id.licenses_top_app_bar
        )
        for (id in ids) {
            val view = activity.findViewById<View?>(id)
            if (view != null) return view
        }
        return null
    }

    private fun applyTopInset(view: View) {
        val baseHeight = view.layoutParams.height
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            target.setPadding(baseLeft, baseTop + top, baseRight, baseBottom)
            if (baseHeight > 0) {
                val params = target.layoutParams
                params.height = baseHeight + top
                target.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun applyBottomInset(view: View, resizeHeight: Boolean) {
        val baseHeight = view.layoutParams?.height ?: 0
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            target.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottom)
            if (resizeHeight && baseHeight > 0) {
                val params = target.layoutParams
                params.height = baseHeight + bottom
                target.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    @JvmStatic
    fun applyFullscreen(activity: Activity) {
        val window = activity.window
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                applyLegacyFullscreen(window)
            }
        } else {
            applyLegacyFullscreen(window)
        }
    }

    private fun isDarkTheme(activity: Activity): Boolean {
        val nightMode =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyLegacyBarIcons(window: Window, darkTheme: Boolean) {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (!darkTheme && Build.VERSION.SDK_INT >= 26) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyFullscreen(window: Window) {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }
}
