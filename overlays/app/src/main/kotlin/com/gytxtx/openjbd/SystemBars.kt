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

        val topBar = activity.findViewById<View?>(R.id.top_app_bar)
        val bottomBar = activity.findViewById<View?>(R.id.bottom_navigation)
        val contentRoot = activity.findViewById<View>(android.R.id.content)

        if (topBar != null) {
            val baseHeight = topBar.layoutParams.height
            val baseLeft = topBar.paddingLeft
            val baseTop = topBar.paddingTop
            val baseRight = topBar.paddingRight
            val baseBottom = topBar.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(baseLeft, baseTop + top, baseRight, baseBottom)
                if (baseHeight > 0) {
                    val params = view.layoutParams
                    params.height = baseHeight + top
                    view.layoutParams = params
                }
                insets
            }
            ViewCompat.requestApplyInsets(topBar)
        }

        if (bottomBar != null) {
            val baseHeight = bottomBar.layoutParams.height
            val baseLeft = bottomBar.paddingLeft
            val baseTop = bottomBar.paddingTop
            val baseRight = bottomBar.paddingRight
            val baseBottom = bottomBar.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { view, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottom)
                if (baseHeight > 0) {
                    val params = view.layoutParams
                    params.height = baseHeight + bottom
                    view.layoutParams = params
                }
                insets
            }
            ViewCompat.requestApplyInsets(bottomBar)
        } else {
            val baseLeft = contentRoot.paddingLeft
            val baseTop = contentRoot.paddingTop
            val baseRight = contentRoot.paddingRight
            val baseBottom = contentRoot.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottom)
                insets
            }
            ViewCompat.requestApplyInsets(contentRoot)
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
