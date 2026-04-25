package com.example.a6thfingercontrolapp.ble

import org.json.JSONObject

/** Marker for integer telemetry fields that are currently missing. */
private const val EMG_INT_MISSING = -1

/**
 * Runtime telemetry snapshot received from the ESP32.
 */
data class Telemetry(
    val flexRawOhm: Array<Float> = Array(4) { Float.NaN },
    val flexFilteredOhm: Array<Float> = Array(4) { Float.NaN },
    val fsrRawOhm: Float = Float.NaN,
    val fsrFilteredOhm: Float = Float.NaN,
    val fsrForceN: Float = Float.NaN,
    val servoTargetDeg: Array<Float> = Array(4) { Float.NaN },
    val servoCurrentDeg: Array<Float> = Array(4) { Float.NaN },
    val servoSpeedDps: Array<Float> = Array(4) { Float.NaN },

    val emgSource: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgMode: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgChannelCount: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgEvent: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgAction: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgCooldownMs: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgBendProgress: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgUnfoldProgress: IntArray = IntArray(4) { EMG_INT_MISSING },
    val emgCh0: Array<Float> = Array(4) { Float.NaN },
    val emgCh1: Array<Float> = Array(4) { Float.NaN },
    val emgCh2: Array<Float> = Array(4) { Float.NaN },

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
            1 -> emgCh1.getOrNull(pairIdx) ?: Float.NaN
            2 -> emgCh2.getOrNull(pairIdx) ?: Float.NaN
            else -> Float.NaN
        }
    }

    companion object {
        /**
         * Parses a telemetry snapshot from firmware JSON.
         */
        fun fromJson(json: JSONObject): Telemetry {
            return Telemetry(
                flexRawOhm = Array(4) { json.optDouble("flex_raw_$it", Double.NaN).toFloat() },
                flexFilteredOhm = Array(4) {
                    json.optDouble("flex_filt_$it", Double.NaN).toFloat()
                },
                fsrRawOhm = json.optDouble("fsr_raw", Double.NaN).toFloat(),
                fsrFilteredOhm = json.optDouble("fsr_filt", Double.NaN).toFloat(),
                fsrForceN = json.optDouble("forceN", Double.NaN).toFloat(),
                servoTargetDeg = Array(4) {
                    json.optDouble("servo_target_$it", Double.NaN).toFloat()
                },
                servoCurrentDeg = Array(4) {
                    json.optDouble("servo_current_$it", Double.NaN).toFloat()
                },
                servoSpeedDps = Array(4) {
                    json.optDouble("servo_speed_$it", Double.NaN).toFloat()
                },

                emgSource = IntArray(4) {
                    if (json.has("emg_source_$it")) json.optInt("emg_source_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgMode = IntArray(4) {
                    if (json.has("emg_mode_$it")) json.optInt("emg_mode_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgChannelCount = IntArray(4) {
                    if (json.has("emg_channels_$it")) json.optInt(
                        "emg_channels_$it",
                        EMG_INT_MISSING
                    )
                    else EMG_INT_MISSING
                },
                emgEvent = IntArray(4) {
                    if (json.has("emg_event_$it")) json.optInt("emg_event_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgAction = IntArray(4) {
                    if (json.has("emg_action_$it")) json.optInt("emg_action_$it", EMG_INT_MISSING)
                    else EMG_INT_MISSING
                },
                emgCooldownMs = IntArray(4) {
                    if (json.has("emg_cooldown_ms_$it")) {
                        json.optInt("emg_cooldown_ms_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgBendProgress = IntArray(4) {
                    if (json.has("emg_bend_progress_$it")) {
                        json.optInt("emg_bend_progress_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgUnfoldProgress = IntArray(4) {
                    if (json.has("emg_unfold_progress_$it")) {
                        json.optInt("emg_unfold_progress_$it", EMG_INT_MISSING)
                    } else {
                        EMG_INT_MISSING
                    }
                },
                emgCh0 = Array(4) { json.optDouble("emg_ch0_$it", Double.NaN).toFloat() },
                emgCh1 = Array(4) { json.optDouble("emg_ch1_$it", Double.NaN).toFloat() },
                emgCh2 = Array(4) { json.optDouble("emg_ch2_$it", Double.NaN).toFloat() },

                vibroDuty = json.optInt("vibro_duty", 0),
                vibroMode = json.optInt("vibro_mode", 0),
                status = "Idle",
                rxMs = 0L
            )
        }
    }
}
