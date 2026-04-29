package com.example.a6thfingercontrolapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.root.AppRootContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Main Android activity that provides Android lifecycle entry points and mounts
 * the root Compose application host.
 */
class MainActivity : ComponentActivity() {
    private val vm by viewModels<BleViewModel>()
    private val authVm by viewModels<AuthViewModel>()
    private val accountVm by viewModels<AccountViewModel>()
    private val appPreferencesVm by viewModels<AppPreferencesViewModel>()
    private var appliedLanguage: String? = null

    /**
     * Applies saved locale before Compose resources are resolved.
     */
    override fun attachBaseContext(newBase: Context) {
        val prefs = AppSettingsStore(newBase)
        val language = runBlocking { prefs.getLanguage().first() }
        appliedLanguage = language
        val localizedContext = LocaleManager.setLocale(newBase, language)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appPreferencesVm.appLanguage.collect { language ->
                    val currentApplied = appliedLanguage
                    if (currentApplied == null) {
                        appliedLanguage = language
                        return@collect
                    }

                    if (language != currentApplied) {
                        appliedLanguage = language
                        recreate()
                    }
                }
            }
        }

        setContent {
            AppRootContent(
                vm = vm,
                authVm = authVm,
                accountVm = accountVm,
                appPreferencesVm = appPreferencesVm
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vm.start()
    }
}
