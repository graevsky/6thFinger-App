package com.example.a6thfingercontrollapp.ui

import androidx.annotation.StringRes
import com.example.a6thfingercontrollapp.R


enum class NavRoute(val route: String, @StringRes val labelRes: Int) {
    Connect("connect", R.string.nav_connectivity),
    Control("control", R.string.nav_controls),
    Sim("sim", R.string.nav_simulation),
    Account("account", R.string.nav_account),
}
