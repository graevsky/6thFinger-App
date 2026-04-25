package com.example.a6thfingercontrollapp.ui

import androidx.annotation.StringRes
import com.example.a6thfingercontrollapp.R

/**
 * Top-level destinations available in the main bottom navigation bar.
 *
 * Each entry stores both the navigation route and the localized label resource
 * used by the UI.
 */
enum class NavRoute(val route: String, @StringRes val labelRes: Int) {
    Connect("connect", R.string.nav_connectivity),
    Control("control", R.string.nav_controls),
    Sim("sim", R.string.nav_simulation),
    Account("account", R.string.nav_account),
}
