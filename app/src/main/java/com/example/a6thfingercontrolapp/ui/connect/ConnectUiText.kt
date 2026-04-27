package com.example.a6thfingercontrolapp.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R

/** Three visible connection states used by the traffic light indicator. */
enum class ConnectionTrafficLightState { Disconnected, Pending, Ready }

/**
 * Calculates the connection traffic light.
 */
fun connectionTrafficLightState(
    hasTargetDevice: Boolean,
    targetConnected: Boolean,
    otherDeviceConnected: Boolean,
    transportConnected: Boolean,
    connecting: Boolean,
    authRequired: Boolean,
    pinSending: Boolean,
    controlReady: Boolean
): ConnectionTrafficLightState {
    if (!hasTargetDevice || otherDeviceConnected) return ConnectionTrafficLightState.Disconnected
    if (targetConnected && controlReady) return ConnectionTrafficLightState.Ready

    val waitingForTarget = connecting || pinSending || authRequired ||
            (targetConnected && !controlReady) ||
            (transportConnected && !targetConnected)

    return if (waitingForTarget) {
        ConnectionTrafficLightState.Pending
    } else {
        ConnectionTrafficLightState.Disconnected
    }
}

/** Low level PIN error mapper. */
@Composable
fun pinErrorUiText(key: String?): String? {
    return when (key) {
        null, "" -> null
        "pin_wrong" -> stringResource(R.string.pin_wrong)
        "pin_send_failed" -> stringResource(R.string.pin_send_failed)
        "pin_timeout" -> stringResource(R.string.pin_timeout)
        "pin_bad_format" -> stringResource(R.string.pin_bad_format)
        else -> key
    }
}
