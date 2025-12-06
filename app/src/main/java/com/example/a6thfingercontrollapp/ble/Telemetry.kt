package com.example.a6thfingercontrollapp.ble

import org.json.JSONObject


data class Telemetry(
    val flexRawOhm: Float = Float.NaN,
    val flexFilteredOhm: Float = Float.NaN,

    val fsrRawOhm: Float = Float.NaN,
    val fsrFilteredOhm: Float = Float.NaN,
    val fsrForceN: Float = Float.NaN,

    val servoTargetDeg: Float = Float.NaN,
    val servoCurrentDeg: Float = Float.NaN,
    val servoSpeedDps: Float = Float.NaN,

    val vibroDuty: Int = 0,
    val vibroMode: Int = 0,

    val status: String = "Idle"
) {
    val flexOhm: Float
        get() = flexFilteredOhm

    val fsrOhm: Float
        get() = fsrFilteredOhm

    val servoDeg: Float
        get() = servoCurrentDeg

    companion object {
        fun fromJson(json: JSONObject): Telemetry {
            return Telemetry(
                flexRawOhm = json.optDouble("flex_raw", Double.NaN).toFloat(),
                flexFilteredOhm = json.optDouble("flex_filt", Double.NaN).toFloat(),

                fsrRawOhm = json.optDouble("fsr_raw", Double.NaN).toFloat(),
                fsrFilteredOhm = json.optDouble("fsr_filt", Double.NaN).toFloat(),
                fsrForceN = json.optDouble("forceN", Double.NaN).toFloat(),

                servoTargetDeg = json.optDouble("servo_target", Double.NaN).toFloat(),
                servoCurrentDeg = json.optDouble("servo_current", Double.NaN).toFloat(),
                servoSpeedDps = json.optDouble("servo_speed", Double.NaN).toFloat(),

                vibroDuty = json.optInt("vibro_duty", 0),
                vibroMode = json.optInt("vibro_mode", 0),

                status = "Idle"
            )
        }
    }
}
