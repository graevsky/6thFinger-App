package com.example.a6thfingercontrollapp.ble

import org.json.JSONArray
import org.json.JSONObject

data class EspSettings(
    val fsrPin: Int = 33,
    val fsrPullupOhm: Int = 10_000,
    val fsrSoftThresholdN: Float = 7.0f,
    val fsrHardMaxN: Float = 10.0f,

    val flexSettings: Array<FlexSettings> = defaultFlexSettings(),
    val servoSettings: Array<ServoSettings> = defaultServoSettings(),

    val vibroPin: Int = 5,
    val vibroMode: Int = 0,
    val vibroFreqHz: Int = 150,
    val vibroMaxDuty: Int = 255,
    val vibroMinDuty: Int = 0,
    val vibroSoftPower: Int = 200,
    val vibroPulseBase: Int = 120,

    val settingsVersion: Int = 1
) {

    fun toJsonString(): String {
        val j = JSONObject().apply {
            put("fsrPin", fsrPin)
            put("fsrPullupOhm", fsrPullupOhm)
            put("fsrSoftThresholdN", fsrSoftThresholdN.toDouble())
            put("fsrHardMaxN", fsrHardMaxN.toDouble())

            put("flexSettings", JSONArray().apply { flexSettings.forEach { put(it.toJson()) } })
            put("servoSettings", JSONArray().apply { servoSettings.forEach { put(it.toJson()) } })


            put("vibroPin", vibroPin)
            put("vibroMode", vibroMode)
            put("vibroFreqHz", vibroFreqHz)
            put("vibroMaxDuty", vibroMaxDuty)
            put("vibroMinDuty", vibroMinDuty)
            put("vibroSoftPower", vibroSoftPower)
            put("vibroPulseBase", vibroPulseBase)

            put("settingsVersion", settingsVersion)
        }
        return j.toString()
    }

    companion object {

        private fun defaultFlexForIndex(idx: Int): FlexSettings =
            if (idx == 0) {
                FlexSettings()
            } else {
                FlexSettings(
                    flexPin = 0xFF,
                    flexPullupOhm = 0,
                    flexStraightOhm = 0,
                    flexBendOhm = 0
                )
            }

        private fun defaultServoForIndex(idx: Int): ServoSettings =
            if (idx == 0) {
                ServoSettings()
            } else {
                ServoSettings(
                    servoPin = 0xFF,
                    servoMinDeg = 40,
                    servoMaxDeg = 180,
                    servoManual = 0,
                    servoManualDeg = 90,
                    servoMaxSpeedDegPerSec = 300.0f
                )
            }

        private fun defaultFlexSettings(): Array<FlexSettings> =
            Array(4) { idx -> defaultFlexForIndex(idx) }

        private fun defaultServoSettings(): Array<ServoSettings> =
            Array(4) { idx -> defaultServoForIndex(idx) }

        private fun parseFlexArray(json: JSONObject): Array<FlexSettings> {
            json.optJSONArray("flexSettings")?.let { arr ->
                return Array(4) { idx ->
                    val obj = arr.optJSONObject(idx)
                    if (obj != null) FlexSettings.fromJson(obj) else defaultFlexForIndex(idx)
                }
            }

            val raw = json.optString("flexSettings", null)
            if (raw != null) {
                try {
                    val arr = JSONArray(raw)
                    return Array(4) { idx ->
                        val obj = arr.optJSONObject(idx)
                        if (obj != null) FlexSettings.fromJson(obj) else defaultFlexForIndex(idx)
                    }
                } catch (_: Throwable) {
                }
            }

            return defaultFlexSettings()
        }

        private fun parseServoArray(json: JSONObject): Array<ServoSettings> {
            json.optJSONArray("servoSettings")?.let { arr ->
                return Array(4) { idx ->
                    val obj = arr.optJSONObject(idx)
                    if (obj != null) ServoSettings.fromJson(obj) else defaultServoForIndex(idx)
                }
            }

            val raw = json.optString("servoSettings", null)
            if (raw != null) {
                try {
                    val arr = JSONArray(raw)
                    return Array(4) { idx ->
                        val obj = arr.optJSONObject(idx)
                        if (obj != null) ServoSettings.fromJson(obj) else defaultServoForIndex(idx)
                    }
                } catch (_: Throwable) {
                }
            }

            return defaultServoSettings()
        }

        fun fromJson(json: JSONObject): EspSettings {
            val def = EspSettings()

            val flexArray = parseFlexArray(json)
            val servoArray = parseServoArray(json)

            return EspSettings(
                fsrPin = json.optInt("fsrPin", def.fsrPin),
                fsrPullupOhm = json.optInt("fsrPullupOhm", def.fsrPullupOhm),
                fsrSoftThresholdN = json
                    .optDouble("fsrSoftThresholdN", def.fsrSoftThresholdN.toDouble())
                    .toFloat(),
                fsrHardMaxN = json
                    .optDouble("fsrHardMaxN", def.fsrHardMaxN.toDouble())
                    .toFloat(),

                flexSettings = flexArray,
                servoSettings = servoArray,

                vibroPin = json.optInt("vibroPin", def.vibroPin),
                vibroMode = json.optInt("vibroMode", def.vibroMode),
                vibroFreqHz = json.optInt("vibroFreqHz", def.vibroFreqHz),
                vibroMaxDuty = json.optInt("vibroMaxDuty", def.vibroMaxDuty),
                vibroMinDuty = json.optInt("vibroMinDuty", def.vibroMinDuty),
                vibroSoftPower = json.optInt("vibroSoftPower", def.vibroSoftPower),
                vibroPulseBase = json.optInt("vibroPulseBase", def.vibroPulseBase),

                settingsVersion = json.optInt("settingsVersion", def.settingsVersion)
            )
        }
    }
}

data class FlexSettings(
    val flexPin: Int = 32,
    val flexPullupOhm: Int = 47_000,
    val flexStraightOhm: Int = 65_000,
    val flexBendOhm: Int = 160_000
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("flexPin", flexPin)
            put("flexPullupOhm", flexPullupOhm)
            put("flexStraightOhm", flexStraightOhm)
            put("flexBendOhm", flexBendOhm)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FlexSettings {
            return FlexSettings(
                flexPin = json.optInt("flexPin", 32),
                flexPullupOhm = json.optInt("flexPullupOhm", 47_000),
                flexStraightOhm = json.optInt("flexStraightOhm", 65_000),
                flexBendOhm = json.optInt("flexBendOhm", 160_000)
            )
        }
    }
}

data class ServoSettings(
    val servoPin: Int = 18,
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
                servoPin = json.optInt("servoPin", 18),
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
