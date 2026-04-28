package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * EMG configuration for one pair using the single-channel binary classifier.
 */
data class EmgSettings(
    val pin: Int = PIN_PLACEHOLDER,
    val bendSnapshotsToBend: Int = 1,
    val bendSnapshotsToUnfold: Int = 1,
    val snapshotTimeoutSec: Int = 2,
    val snapshotSize: Int = 8,
    val minUnfoldDelaySec: Int = 2,
    val reverseDirection: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("pins", JSONArray().apply { put(pin) })
            put("bendSnapshotsToBend", bendSnapshotsToBend.coerceIn(1, 5))
            put("bendSnapshotsToUnfold", bendSnapshotsToUnfold.coerceIn(1, 8))
            put("snapshotTimeoutSec", snapshotTimeoutSec.coerceIn(1, 15))
            put("snapshotSize", snapshotSize.coerceIn(1, 32))
            put("minUnfoldDelaySec", minUnfoldDelaySec.coerceIn(0, 30))
            put("reverseDirection", reverseDirection)
        }
    }

    /** Returns the single firmware-supported EMG input pin. */
    fun activePins(): List<Int> = listOf(pin)

    /** Validates the active EMG pin using the same rules as the firmware. */
    fun activePinsValid(): Boolean = pin != PIN_PLACEHOLDER && pin != 0

    companion object {
        fun fromJson(json: JSONObject): EmgSettings {
            val pinsArr = json.optJSONArray("pins")
            val parsedPin = pinsArr?.optInt(0, json.optInt("pin", PIN_PLACEHOLDER))
                ?: json.optInt("pin", PIN_PLACEHOLDER)

            return EmgSettings(
                pin = parsedPin,
                bendSnapshotsToBend = json.optInt("bendSnapshotsToBend", 1).coerceIn(1, 5),
                bendSnapshotsToUnfold = json.optInt("bendSnapshotsToUnfold", 1).coerceIn(1, 8),
                snapshotTimeoutSec = json.optInt("snapshotTimeoutSec", 2).coerceIn(1, 15),
                snapshotSize = json.optInt("snapshotSize", 8).coerceIn(1, 32),
                minUnfoldDelaySec = json.optInt("minUnfoldDelaySec", 2).coerceIn(0, 30),
                reverseDirection = json.optBoolean("reverseDirection", false)
            )
        }
    }
}
