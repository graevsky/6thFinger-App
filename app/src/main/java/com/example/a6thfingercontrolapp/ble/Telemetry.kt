package com.example.a6thfingercontrolapp.ble

import com.example.a6thfingercontrolapp.ble.settings.ESP_PAIR_COUNT
import org.json.JSONObject

/** Marker for integer telemetry fields that are currently missing. */
private const val EMG_INT_MISSING = -1

/**
 * Runtime telemetry snapshot received from the ESP32.
 */
data class Telemetry(
    val flexRawOhm: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },
    val flexFilteredOhm: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },
    val fsrRawOhm: Float = Float.NaN,
    val fsrFilteredOhm: Float = Float.NaN,
    val fsrForceN: Float = Float.NaN,
    val servoTargetDeg: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },
    val servoCurrentDeg: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },
    val servoSpeedDps: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },

    val emgSource: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgEvent: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgAction: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgCooldownMs: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgBendProgress: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgUnfoldProgress: IntArray = IntArray(ESP_PAIR_COUNT) { EMG_INT_MISSING },
    val emgCh0: Array<Float> = Array(ESP_PAIR_COUNT) { Float.NaN },

    val vibroDuty: Int = 0,
    val vibroMode: Int = 0,

    /**
     * Human-readable / normalized BLE status key.
     */
    val status: String = "Idle",

    /**
     * Monotonic timestamp of the last telemetry packet received by the app.
     * Used to detect stale telemetry.
     */
    val rxMs: Long = 0L
) {
    /**
     * Convenience aliases used by the UI.
     *
     * The board provides both raw and filtered values, but most screens display
     * filtered values, so these accessors keep UI code cleaner.
     */
    val flexOhm: Array<Float>
        get() = flexFilteredOhm

    val fsrOhm: Float
        get() = fsrFilteredOhm

    val servoDeg: Array<Float>
        get() = servoCurrentDeg

    /**
     * Safe channel accessor for a specific pair/channel combination.
     */
    fun emgChannelValue(pairIdx: Int, channelIdx: Int): Float {
        return when (channelIdx) {
            0 -> emgCh0.getOrNull(pairIdx) ?: Float.NaN
            else -> Float.NaN
        }
    }

    companion object {
        /**
         * Parses a telemetry snapshot from firmware JSON.
         */
        fun fromJson(json: JSONObject): Telemetry {
            return Telemetry(
                flexRawOhm = Array(ESP_PAIR_COUNT) {
                    json.optDouble("flex_raw_$it", Double.NaN).toFloat()
                },
                flexFilteredOhm = Array(ESP_PAIR_COUNT) {
                    json.optDouble("flex_filt_$it", Double.NaN).toFloat()
                },
                fsrRawOhm = json.optDouble("fsr_raw", Double.NaN).toFloat(),
                fsrFilteredOhm = json.optDouble("fsr_filt", Double.NaN).toFloat(),
                fsrForceN = json.optDouble("forceN", Double.NaN).toFloat(),
                servoTargetDeg = Array(ESP_PAIR_COUNT) {
                    json.optDouble("servo_target_$it", Double.NaN).toFloat()
                },
                servoCurrentDeg = Array(ESP_PAIR_COUNT) {
                    json.optDouble("servo_current_$it", Double.NaN).toFloat()
                },
                servoSpeedDps = Array(ESP_PAIR_COUNT) {
                    json.optDouble("servo_speed_$it", Double.NaN).toFloat()
                },

                emgSource = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_source_$it")) json.optInt("emg_source_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgEvent = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_event_$it")) json.optInt("emg_event_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgAction = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_action_$it")) json.optInt("emg_action_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgCooldownMs = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_cooldown_ms_$it")) {
                        json.optInt("emg_cooldown_ms_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgBendProgress = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_bend_progress_$it")) {
                        json.optInt("emg_bend_progress_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgUnfoldProgress = IntArray(ESP_PAIR_COUNT) {
                    if (json.has("emg_unfold_progress_$it")) {
                        json.optInt("emg_unfold_progress_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgCh0 = Array(ESP_PAIR_COUNT) {
                    json.optDouble("emg_ch0_$it", Double.NaN).toFloat()
                },

                vibroDuty = json.optInt("vibro_duty", 0),
                vibroMode = json.optInt("vibro_mode", 0),
                status = "Idle",
                rxMs = 0L
            )
        }
    }
}
