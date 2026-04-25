package com.example.a6thfingercontrollapp

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Walks through ContextWrapper layers until an Activity is found.
 */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Restarts the app by scheduling a fresh launcher intent and closing the current process.
 *
 * Used after operations that require a clean Android process state, such as firmware reboot flows.
 */
fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return

    val intent = Intent(launchIntent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    val pending = PendingIntent.getActivity(
        context,
        999,
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setExact(AlarmManager.RTC, System.currentTimeMillis() + 200, pending)

    context.findActivity()?.finishAffinity()
    exitProcess(0)
}
