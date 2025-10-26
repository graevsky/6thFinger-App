package com.example.a6thfingercontrollapp.ui

import androidx.annotation.StringRes
import com.example.a6thfingercontrollapp.R


enum class NavRoute(val route: String, @StringRes val labelRes: Int) {
    Connect("connect", R.string.connectivity),
    Control("control", R.string.controls),
    Sim("sim", R.string.simulation),
    Account("account", R.string.account),
}
