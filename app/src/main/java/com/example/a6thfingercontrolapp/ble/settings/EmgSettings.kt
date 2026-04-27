package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * EMG configuration for one pair.
 *
 * Channels determines EMG sensor channels (independent connected devices).
 */
data class EmgSettings(
    val channels: Int = 1,
    val pin0: Int = PIN_PLACEHOLDER,
    val pin1: Int = PIN_PLACEHOLDER,
    val pin2: Int = PIN_PLACEHOLDER,
    val mode: Int = EMG_MODE_BEND_OTHER,
    val bendFullMoves: Int = 1,
    val unfoldFullMoves: Int = 1,
    val minSwitchDelaySec: Int = 1,
    val reverseDirection: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("channels", channels.coerceIn(1, 3))
            put("pins", JSONArray().apply {
                put(pin0)
                put(pin1)
                put(pin2)
            })
            put("mode", mode.coerceIn(EMG_MODE_BEND_OTHER, EMG_MODE_DIRECTIONAL))
            put("bendFullMoves", bendFullMoves.coerceIn(1, 5))
            put("unfoldFullMoves", unfoldFullMoves.coerceIn(1, 5))
            put("minSwitchDelaySec", minSwitchDelaySec.coerceIn(1, 60))
            put("reverseDirection", reverseDirection)
        }
    }

    /** Returns only pins that are relevant for the selected channel. */
    fun activePins(): List<Int> = listOf(pin0, pin1, pin2).take(channels.coerceIn(1, 3))

    companion object {
        fun fromJson(json: JSONObject): EmgSettings {
            val pinsArr = json.optJSONArray("pins")
            val p0 = pinsArr?.optInt(0, json.optInt("pin0", PIN_PLACEHOLDER))
                ?: json.optInt("pin0", PIN_PLACEHOLDER)
            val p1 = pinsArr?.optInt(1, json.optInt("pin1", PIN_PLACEHOLDER))
                ?: json.optInt("pin1", PIN_PLACEHOLDER)
            val p2 = pinsArr?.optInt(2, json.optInt("pin2", PIN_PLACEHOLDER))
                ?: json.optInt("pin2", PIN_PLACEHOLDER)

            return EmgSettings(
                channels = json.optInt("channels", 1).coerceIn(1, 3),
                pin0 = p0,
                pin1 = p1,
                pin2 = p2,
                mode = json.optInt("mode", EMG_MODE_BEND_OTHER)
                    .coerceIn(EMG_MODE_BEND_OTHER, EMG_MODE_DIRECTIONAL),
                bendFullMoves = json.optInt("bendFullMoves", 1).coerceIn(1, 5),
                unfoldFullMoves = json.optInt("unfoldFullMoves", 1).coerceIn(1, 5),
                minSwitchDelaySec = json.optInt("minSwitchDelaySec", 1).coerceIn(1, 60),
                reverseDirection = json.optBoolean("reverseDirection", false)
            )
        }
    }
}
