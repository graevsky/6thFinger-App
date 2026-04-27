package com.example.a6thfingercontrolapp.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EMG_ACTION_BEND
import com.example.a6thfingercontrolapp.ble.settings.EMG_ACTION_COOLDOWN_IGNORED
import com.example.a6thfingercontrolapp.ble.settings.EMG_ACTION_NONE
import com.example.a6thfingercontrolapp.ble.settings.EMG_ACTION_UNFOLD
import com.example.a6thfingercontrolapp.ble.settings.EMG_EVENT_BEND
import com.example.a6thfingercontrolapp.ble.settings.EMG_EVENT_NONE
import com.example.a6thfingercontrolapp.ble.settings.EMG_EVENT_OTHER
import com.example.a6thfingercontrolapp.ble.settings.EMG_EVENT_UNFOLD
import com.example.a6thfingercontrolapp.ble.settings.EMG_MODE_BEND_OTHER
import com.example.a6thfingercontrolapp.ble.settings.EMG_MODE_DIRECTIONAL
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_FLEX

/**
 * Maps the numeric input source stored in settings/telemetry to localized UI text.
 */
@Composable
fun inputSourceLabel(source: Int): String {
    return when (source) {
        INPUT_SOURCE_EMG -> stringResource(R.string.input_source_emg)
        INPUT_SOURCE_FLEX -> stringResource(R.string.input_source_flex)
        else -> stringResource(R.string.telemetry_placeholder)
    }
}

/**
 * Maps EMG operating mode constants to labels shown in settings and diagnostics.
 */
@Composable
fun emgModeLabel(mode: Int): String {
    return when (mode) {
        EMG_MODE_BEND_OTHER -> stringResource(R.string.emg_mode_bend_other)
        EMG_MODE_DIRECTIONAL -> stringResource(R.string.emg_mode_directional)
        else -> stringResource(R.string.telemetry_placeholder)
    }
}

/**
 * Maps the latest EMG event code from telemetry to localized text.
 */
@Composable
fun emgEventLabel(event: Int): String {
    return when (event) {
        EMG_EVENT_NONE -> stringResource(R.string.emg_event_none)
        EMG_EVENT_OTHER -> stringResource(R.string.emg_event_other)
        EMG_EVENT_BEND -> stringResource(R.string.emg_event_bend)
        EMG_EVENT_UNFOLD -> stringResource(R.string.emg_event_unfold)
        else -> stringResource(R.string.telemetry_placeholder)
    }
}

/**
 * Maps the action currently executed by firmware EMG logic to localized text.
 */
@Composable
fun emgActionLabel(action: Int): String {
    return when (action) {
        EMG_ACTION_NONE -> stringResource(R.string.emg_action_none)
        EMG_ACTION_BEND -> stringResource(R.string.emg_action_bend)
        EMG_ACTION_UNFOLD -> stringResource(R.string.emg_action_unfold)
        EMG_ACTION_COOLDOWN_IGNORED -> stringResource(R.string.emg_action_cooldown_ignored)
        else -> stringResource(R.string.telemetry_placeholder)
    }
}
