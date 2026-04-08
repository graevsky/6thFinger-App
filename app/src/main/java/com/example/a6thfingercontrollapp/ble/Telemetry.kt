package com.example.a6thfingercontrollapp.ble

import org.json.JSONObject

data class Telemetry(
    val flexRawOhm: Array<Float> = Array(4) { Float.NaN },
    val flexFilteredOhm: Array<Float> = Array(4) { Float.NaN },
    val fsrRawOhm: Float = Float.NaN,
    val fsrFilteredOhm: Float = Float.NaN,
    val fsrForceN: Float = Float.NaN,
    val servoTargetDeg: Array<Float> = Array(4) { Float.NaN },
    val servoCurrentDeg: Array<Float> = Array(4) { Float.NaN },
    val servoSpeedDps: Array<Float> = Array(4) { Float.NaN },
    val vibroDuty: Int = 0,
    val vibroMode: Int = 0,
    val status: String = "Idle",

    val rxMs: Long = 0L
) {
    val flexOhm: Array<Float>
        get() = flexFilteredOhm

    val fsrOhm: Float
        get() = fsrFilteredOhm

    val servoDeg: Array<Float>
        get() = servoCurrentDeg

    companion object {
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
                vibroDuty = json.optInt("vibro_duty", 0),
                vibroMode = json.optInt("vibro_mode", 0),
                status = "Idle",
                rxMs = 0L
            )
        }
    }
}