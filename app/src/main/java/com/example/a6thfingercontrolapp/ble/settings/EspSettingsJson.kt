package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON converter for the full ESP settings entry.
 */
internal object EspSettingsJson {
    fun toJsonString(settings: EspSettings): String {
        val json = JSONObject().apply {
            put("fsrPin", settings.fsrPin)
            put("fsrPullupOhm", settings.fsrPullupOhm)
            put("fsrSoftThresholdN", settings.fsrSoftThresholdN.toDouble())
            put("fsrHardMaxN", settings.fsrHardMaxN.toDouble())

            put(
                "flexSettings",
                JSONArray().apply { settings.flexSettings.forEach { put(it.toJson()) } }
            )
            put(
                "servoSettings",
                JSONArray().apply { settings.servoSettings.forEach { put(it.toJson()) } }
            )
            put(
                "pairInputSettings",
                JSONArray().apply { settings.pairInputSettings.forEach { put(it.toJson()) } }
            )
            put(
                "emgSettings",
                JSONArray().apply { settings.emgSettings.forEach { put(it.toJson()) } }
            )

            put("vibroPin", settings.vibroPin)
            put("vibroMode", settings.vibroMode)
            put("vibroFreqHz", settings.vibroFreqHz)
            put("vibroMaxDuty", settings.vibroMaxDuty)
            put("vibroMinDuty", settings.vibroMinDuty)
            put("vibroSoftPower", settings.vibroSoftPower)
            put("vibroPulseBase", settings.vibroPulseBase)

            put("pinCode", settings.pinCode)
            put("settingsVersion", settings.settingsVersion)
        }
        return json.toString()
    }

    fun fromJson(json: JSONObject): EspSettings {
        val defaults = EspSettings()

        val pinCode = json.optInt("pinCode", 0)
        val pinSet = json.optBoolean("pinSet", pinCode != 0)
        val authRequired = json.optBoolean("authRequired", false)

        return EspSettings(
            fsrPin = json.optInt("fsrPin", defaults.fsrPin),
            fsrPullupOhm = json.optInt("fsrPullupOhm", defaults.fsrPullupOhm),
            fsrSoftThresholdN = json
                .optDouble("fsrSoftThresholdN", defaults.fsrSoftThresholdN.toDouble())
                .toFloat(),
            fsrHardMaxN = json
                .optDouble("fsrHardMaxN", defaults.fsrHardMaxN.toDouble())
                .toFloat(),

            flexSettings = parseArray(
                json = json,
                key = "flexSettings",
                parseItem = FlexSettings.Companion::fromJson,
                defaultForIndex = ::espDefaultFlexForIndex,
                defaultArray = ::espDefaultFlexSettings
            ),
            servoSettings = parseArray(
                json = json,
                key = "servoSettings",
                parseItem = ServoSettings.Companion::fromJson,
                defaultForIndex = ::espDefaultServoForIndex,
                defaultArray = ::espDefaultServoSettings
            ),
            pairInputSettings = parseArray(
                json = json,
                key = "pairInputSettings",
                parseItem = PairInputSettings.Companion::fromJson,
                defaultForIndex = ::espDefaultPairInputForIndex,
                defaultArray = ::espDefaultPairInputSettings
            ),
            emgSettings = parseArray(
                json = json,
                key = "emgSettings",
                parseItem = EmgSettings.Companion::fromJson,
                defaultForIndex = ::espDefaultEmgForIndex,
                defaultArray = ::espDefaultEmgSettings
            ),

            vibroPin = json.optInt("vibroPin", defaults.vibroPin),
            vibroMode = json.optInt("vibroMode", defaults.vibroMode),
            vibroFreqHz = json.optInt("vibroFreqHz", defaults.vibroFreqHz),
            vibroMaxDuty = json.optInt("vibroMaxDuty", defaults.vibroMaxDuty),
            vibroMinDuty = json.optInt("vibroMinDuty", defaults.vibroMinDuty),
            vibroSoftPower = json.optInt("vibroSoftPower", defaults.vibroSoftPower),
            vibroPulseBase = json.optInt("vibroPulseBase", defaults.vibroPulseBase),

            pinCode = pinCode,
            pinSet = pinSet,
            authRequired = authRequired,

            settingsVersion = json.optInt("settingsVersion", defaults.settingsVersion)
        )
    }

    private inline fun <reified T> parseArray(
        json: JSONObject,
        key: String,
        noinline parseItem: (JSONObject) -> T,
        noinline defaultForIndex: (Int) -> T,
        noinline defaultArray: () -> Array<T>
    ): Array<T> {
        json.optJSONArray(key)?.let { array ->
            return Array(ESP_PAIR_COUNT) { idx ->
                val obj = array.optJSONObject(idx)
                if (obj != null) parseItem(obj) else defaultForIndex(idx)
            }
        }

        val raw = json.opt(key) as? String
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                return Array(ESP_PAIR_COUNT) { idx ->
                    val obj = array.optJSONObject(idx)
                    if (obj != null) parseItem(obj) else defaultForIndex(idx)
                }
            } catch (_: Throwable) {
            }
        }

        return defaultArray()
    }
}
