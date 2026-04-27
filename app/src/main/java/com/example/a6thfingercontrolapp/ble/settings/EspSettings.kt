package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONObject

/** Input source for a pair: flex sensor mode. */
const val INPUT_SOURCE_FLEX = 0

/** Input source for a pair: EMG sensor mode. */
const val INPUT_SOURCE_EMG = 1

/** EMG mode without directions. */
const val EMG_MODE_BEND_OTHER = 0

/** EMG mode with finger movement direction detection. */
const val EMG_MODE_DIRECTIONAL = 1

/** No EMG event detected. */
const val EMG_EVENT_NONE = 0

/** Generic EMG event. E.g. hand movement. */
const val EMG_EVENT_OTHER = 1

/** EMG event for finger bending action. */
const val EMG_EVENT_BEND = 2

/** EMG event for finger unfolding action. */
const val EMG_EVENT_UNFOLD = 3

/** No EMG action currently executed. */
const val EMG_ACTION_NONE = 0

/** Servo is bending because of EMG logic. */
const val EMG_ACTION_BEND = 1

/** Servo is unfolding because of EMG logic. */
const val EMG_ACTION_UNFOLD = 2

/** EMG action was ignored because cooldown is active. */
const val EMG_ACTION_COOLDOWN_IGNORED = 3

/**
 * Full device configuration exchanged with the ESP32 and backend.
 */
data class EspSettings(
    val fsrPin: Int = 33,
    val fsrPullupOhm: Int = 10_000,
    val fsrSoftThresholdN: Float = 7.0f,
    val fsrHardMaxN: Float = 10.0f,

    val flexSettings: Array<FlexSettings> = espDefaultFlexSettings(),
    val servoSettings: Array<ServoSettings> = espDefaultServoSettings(),
    val pairInputSettings: Array<PairInputSettings> = espDefaultPairInputSettings(),
    val emgSettings: Array<EmgSettings> = espDefaultEmgSettings(),

    val vibroPin: Int = 5,
    val vibroMode: Int = 0,
    val vibroFreqHz: Int = 150,
    val vibroMaxDuty: Int = 255,
    val vibroMinDuty: Int = 0,
    val vibroSoftPower: Int = 200,
    val vibroPulseBase: Int = 120,

    // PIN (0000 == 0 == disabled)
    val pinCode: Int = 0,
    val pinSet: Boolean = false,
    val authRequired: Boolean = false,

    /** Configuration schema version. */
    val settingsVersion: Int = 2
) {
    /**
     * Serializes settings into JSON sent to the board.
     */
    fun toJsonString(): String = EspSettingsJson.toJsonString(this)

    companion object {
        /**
         * Builds EspSettings from JSON.
         */
        fun fromJson(json: JSONObject): EspSettings =
            EspSettingsJson.fromJson(json)
    }
}
