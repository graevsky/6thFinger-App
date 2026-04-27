package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.ble.settings.EmgSettings
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.FlexSettings
import com.example.a6thfingercontrolapp.ble.settings.PairInputSettings
import com.example.a6thfingercontrolapp.ble.settings.ServoSettings
import org.json.JSONObject

/**
 * Mapper between backend received payloads and local settings models for prothesis.
 */
internal object AuthCloudMapper {
    fun espToPayload(settings: EspSettings): Map<String, Any?> {
        return mapOf(
            "fsrPin" to settings.fsrPin,
            "fsrPullupOhm" to settings.fsrPullupOhm,
            "fsrSoftThresholdN" to settings.fsrSoftThresholdN.toDouble(),
            "fsrHardMaxN" to settings.fsrHardMaxN.toDouble(),

            "flexSettings" to settings.flexSettings.map(::flexToMap),
            "servoSettings" to settings.servoSettings.map(::servoToMap),
            "pairInputSettings" to settings.pairInputSettings.map(::pairInputToMap),
            "emgSettings" to settings.emgSettings.map(::emgToMap),

            "vibroPin" to settings.vibroPin,
            "vibroMode" to settings.vibroMode,
            "vibroFreqHz" to settings.vibroFreqHz,
            "vibroMaxDuty" to settings.vibroMaxDuty,
            "vibroMinDuty" to settings.vibroMinDuty,
            "vibroSoftPower" to settings.vibroSoftPower,
            "vibroPulseBase" to settings.vibroPulseBase,

            "pinCode" to settings.pinCode,

            "pinSet" to settings.pinSet,
            "authRequired" to settings.authRequired,

            "settingsVersion" to settings.settingsVersion
        )
    }

    fun payloadToEsp(payload: Map<String, Any?>): EspSettings? {
        return try {
            val json = JSONObject(payload)
            EspSettings.fromJson(json)
        } catch (_: Throwable) {
            null
        }
    }

    private fun flexToMap(settings: FlexSettings): Map<String, Any?> = mapOf(
        "flexPin" to settings.flexPin,
        "flexPullupOhm" to settings.flexPullupOhm,
        "flexStraightOhm" to settings.flexStraightOhm,
        "flexBendOhm" to settings.flexBendOhm,
        "flexTolerancePct" to settings.flexTolerancePct
    )

    private fun servoToMap(settings: ServoSettings): Map<String, Any?> = mapOf(
        "servoPin" to settings.servoPin,
        "servoMinDeg" to settings.servoMinDeg,
        "servoMaxDeg" to settings.servoMaxDeg,
        "servoManual" to settings.servoManual,
        "servoManualDeg" to settings.servoManualDeg,
        "servoMaxSpeedDegPerSec" to settings.servoMaxSpeedDegPerSec.toDouble()
    )

    private fun pairInputToMap(settings: PairInputSettings): Map<String, Any?> = mapOf(
        "inputSource" to settings.inputSource
    )

    private fun emgToMap(settings: EmgSettings): Map<String, Any?> = mapOf(
        "channels" to settings.channels,
        "pins" to listOf(settings.pin0, settings.pin1, settings.pin2),
        "mode" to settings.mode,
        "bendFullMoves" to settings.bendFullMoves,
        "unfoldFullMoves" to settings.unfoldFullMoves,
        "minSwitchDelaySec" to settings.minSwitchDelaySec,
        "reverseDirection" to settings.reverseDirection
    )
}
