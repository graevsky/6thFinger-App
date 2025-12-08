package com.example.a6thfingercontrollapp.ble

import org.json.JSONObject

data class EspSettings(
        val fsrPin: Int = 33,
        val fsrPullupOhm: Int = 10_000,
        val fsrSoftThresholdN: Float = 7.0f,
        val fsrHardMaxN: Float = 10.0f,
        val flexPin: Int = 32,
        val flexPullupOhm: Int = 47_000,
        val flexStraightOhm: Int = 65_000,
        val flexBendOhm: Int = 160_000,
        val vibroPin: Int = 5,
        val vibroMode: Int = 0,
        val vibroFreqHz: Int = 150,
        val vibroMaxDuty: Int = 255,
        val vibroMinDuty: Int = 0,
        val vibroSoftPower: Int = 200,
        val vibroPulseBase: Int = 120,
        val servoPin: Int = 18,
        val servoMinDeg: Int = 40,
        val servoMaxDeg: Int = 180,
        val servoManual: Int = 0,
        val servoManualDeg: Int = 90,
        val servoMaxSpeedDegPerSec: Float = 300.0f,
        val settingsVersion: Int = 1
) {

    fun toJsonString(): String {
        val j =
                JSONObject().apply {
                    put("fsrPin", fsrPin)
                    put("fsrPullupOhm", fsrPullupOhm)
                    put("fsrSoftThresholdN", fsrSoftThresholdN.toDouble())
                    put("fsrHardMaxN", fsrHardMaxN.toDouble())

                    put("flexPin", flexPin)
                    put("flexPullupOhm", flexPullupOhm)
                    put("flexStraightOhm", flexStraightOhm)
                    put("flexBendOhm", flexBendOhm)

                    put("vibroPin", vibroPin)
                    put("vibroMode", vibroMode)
                    put("vibroFreqHz", vibroFreqHz)
                    put("vibroMaxDuty", vibroMaxDuty)
                    put("vibroMinDuty", vibroMinDuty)
                    put("vibroSoftPower", vibroSoftPower)
                    put("vibroPulseBase", vibroPulseBase)

                    put("servoPin", servoPin)
                    put("servoMinDeg", servoMinDeg)
                    put("servoMaxDeg", servoMaxDeg)
                    put("servoManual", servoManual)
                    put("servoManualDeg", servoManualDeg)
                    put("servoMaxSpeedDegPerSec", servoMaxSpeedDegPerSec.toDouble())

                    put("settingsVersion", settingsVersion)
                }
        return j.toString()
    }

    companion object {
        fun fromJson(json: JSONObject): EspSettings {
            val def = EspSettings()

            return EspSettings(
                    fsrPin = json.optInt("fsrPin", def.fsrPin),
                    fsrPullupOhm = json.optInt("fsrPullupOhm", def.fsrPullupOhm),
                    fsrSoftThresholdN =
                            json.optDouble("fsrSoftThresholdN", def.fsrSoftThresholdN.toDouble())
                                    .toFloat(),
                    fsrHardMaxN =
                            json.optDouble("fsrHardMaxN", def.fsrHardMaxN.toDouble()).toFloat(),
                    flexPin = json.optInt("flexPin", def.flexPin),
                    flexPullupOhm = json.optInt("flexPullupOhm", def.flexPullupOhm),
                    flexStraightOhm = json.optInt("flexStraightOhm", def.flexStraightOhm),
                    flexBendOhm = json.optInt("flexBendOhm", def.flexBendOhm),
                    vibroPin = json.optInt("vibroPin", def.vibroPin),
                    vibroMode = json.optInt("vibroMode", def.vibroMode),
                    vibroFreqHz = json.optInt("vibroFreqHz", def.vibroFreqHz),
                    vibroMaxDuty = json.optInt("vibroMaxDuty", def.vibroMaxDuty),
                    vibroMinDuty = json.optInt("vibroMinDuty", def.vibroMinDuty),
                    vibroSoftPower = json.optInt("vibroSoftPower", def.vibroSoftPower),
                    vibroPulseBase = json.optInt("vibroPulseBase", def.vibroPulseBase),
                    servoPin = json.optInt("servoPin", def.servoPin),
                    servoMinDeg = json.optInt("servoMinDeg", def.servoMinDeg),
                    servoMaxDeg = json.optInt("servoMaxDeg", def.servoMaxDeg),
                    servoManual = json.optInt("servoManual", def.servoManual),
                    servoManualDeg = json.optInt("servoManualDeg", def.servoManualDeg),
                    servoMaxSpeedDegPerSec =
                            json.optDouble(
                                            "servoMaxSpeedDegPerSec",
                                            def.servoMaxSpeedDegPerSec.toDouble()
                                    )
                                    .toFloat(),
                    settingsVersion = json.optInt("settingsVersion", def.settingsVersion)
            )
        }
    }
}
