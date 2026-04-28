package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONObject

/**
 * Servo configuration for one pair.
 */
data class ServoSettings(
    val servoPin: Int = 21,
    val servoMinDeg: Int = 40,
    val servoMaxDeg: Int = 180,
    val servoManual: Int = 0,
    val servoManualDeg: Int = 90,
    val servoMaxSpeedDegPerSec: Float = 300.0f
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("servoPin", servoPin)
            put("servoMinDeg", servoMinDeg)
            put("servoMaxDeg", servoMaxDeg)
            put("servoManual", servoManual)
            put("servoManualDeg", servoManualDeg)
            put("servoMaxSpeedDegPerSec", servoMaxSpeedDegPerSec.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ServoSettings {
            return ServoSettings(
                servoPin = json.optInt("servoPin", 21),
                servoMinDeg = json.optInt("servoMinDeg", 40),
                servoMaxDeg = json.optInt("servoMaxDeg", 180),
                servoManual = json.optInt("servoManual", 0),
                servoManualDeg = json.optInt("servoManualDeg", 90),
                servoMaxSpeedDegPerSec = json
                    .optDouble("servoMaxSpeedDegPerSec", 300.0)
                    .toFloat()
            )
        }
    }
}
