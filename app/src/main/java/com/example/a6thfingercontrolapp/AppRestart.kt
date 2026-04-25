package com.example.a6thfingercontrolapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * Walks through ContextWrapper layers until an Activity is found.
 */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Restarts the application task by opening the launcher activity again.
 *
 * This is a soft task-level restart. Android may keep the current process alive,
 * but the old activity stack is cleared and the app returns through its normal
 * launcher entry point.
 */
fun restartApp(context: Context) {
    val appContext = context.applicationContext
    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        ?: return

    val intent = Intent(launchIntent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    appContext.startActivity(intent)
    context.findActivity()?.finishAffinity()
}
