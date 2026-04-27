package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONObject

/**
 * Per-pair selector for servo input source.
 */
data class PairInputSettings(
    val inputSource: Int = INPUT_SOURCE_FLEX
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("inputSource", inputSource)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PairInputSettings {
            return PairInputSettings(
                inputSource = json.optInt("inputSource", INPUT_SOURCE_FLEX)
                    .coerceIn(INPUT_SOURCE_FLEX, INPUT_SOURCE_EMG)
            )
        }
    }
}
