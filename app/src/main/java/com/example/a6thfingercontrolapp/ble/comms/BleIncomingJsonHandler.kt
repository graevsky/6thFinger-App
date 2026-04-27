package com.example.a6thfingercontrolapp.ble.comms

import android.os.SystemClock
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import org.json.JSONObject

/**
 * Contract required to apply incoming BLE protocol messages.
 */
internal interface BleIncomingJsonContext {
    var sessionAuthed: Boolean
    var devicePinSet: Boolean
    var seenTelemetry: Boolean
    var lastTeleRxMs: Long

    var authRequired: Boolean
    var authSending: Boolean
    var pinError: String?
    var controlUnlocked: Boolean

    val currentTelemetry: Telemetry

    fun updateTelemetry(value: Telemetry)
    fun updateSettings(value: EspSettings)
    fun updateStatus(text: String)
    fun requestConfig()
    fun sendCfgOkOnce()
    fun maybeCompleteAckWaiter(json: JSONObject)
    fun canTelemetryUnlockControl(): Boolean
}

/**
 * Applies parsed JSON object to the current BLE state.
 */
internal fun BleIncomingJsonContext.handleIncomingJson(json: JSONObject) {
    when (json.optString("type", "")) {
        "auth" -> handleAuthMessage(json)
        "cfg" -> handleConfigMessage(json)
        "tele" -> handleTelemetryMessage(json)
        "ack" -> handleAckMessage(json)
        else -> handleLegacyMessage(json)
    }
}

private fun BleIncomingJsonContext.handleAuthMessage(json: JSONObject) {
    val required = json.optBoolean("required", false)
    if (required && !json.has("ok")) {
        authRequired = true
        controlUnlocked = false
        authSending = false
        return
    }

    val ok = json.optBoolean("ok", false)
    authSending = false

    if (ok) {
        sessionAuthed = true
        pinError = null
        authRequired = false
        controlUnlocked = true
        updateStatus("AUTH OK")
        requestConfig()
    } else {
        sessionAuthed = false
        authRequired = true
        controlUnlocked = false
        pinError = json.optString("err").ifBlank { "pin_wrong" }
        updateStatus("AUTH FAIL")
    }
}

private fun BleIncomingJsonContext.handleConfigMessage(json: JSONObject) {
    val config = try {
        EspSettings.Companion.fromJson(json)
    } catch (_: Throwable) {
        updateStatus("Config parse error")
        return
    }

    updateSettings(config)
    updateStatus("Config updated")

    devicePinSet = config.pinSet || (config.pinCode != 0)

    val mustAuth = (config.authRequired || devicePinSet) && !sessionAuthed
    if (mustAuth) {
        authRequired = true
        controlUnlocked = false
        authSending = false
    } else {
        authRequired = false
        controlUnlocked = true
        sendCfgOkOnce()
    }
}

private fun BleIncomingJsonContext.handleTelemetryMessage(json: JSONObject) {
    val parsed = try {
        Telemetry.Companion.fromJson(json)
    } catch (_: Throwable) {
        updateStatus("Tele parse error")
        return
    }

    val rxTime = SystemClock.elapsedRealtime()
    lastTeleRxMs = rxTime
    seenTelemetry = true

    updateTelemetry(parsed.copy(status = currentTelemetry.status, rxMs = rxTime))

    if (!controlUnlocked && canTelemetryUnlockControl()) {
        authRequired = false
        authSending = false
        pinError = null
        controlUnlocked = true
    }
}

private fun BleIncomingJsonContext.handleAckMessage(json: JSONObject) {
    maybeCompleteAckWaiter(json)
    val ok = json.optBoolean("ok", false)
    updateStatus(if (ok) "ACK OK" else "ACK FAIL")
}

private fun BleIncomingJsonContext.handleLegacyMessage(json: JSONObject) {
    when {
        json.has("servoSettings") -> handleConfigMessage(json)
        json.has("fsr_raw") || json.has("flex_raw_0") -> handleTelemetryMessage(json)
    }
}
