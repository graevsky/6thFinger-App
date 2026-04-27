package com.example.a6thfingercontrolapp.ble.settings

import org.json.JSONObject

/**
 * Flex sensor calibration/settings for one pair.
 */
data class FlexSettings(
    val flexPin: Int = 32,
    val flexPullupOhm: Int = 47_000,
    val flexStraightOhm: Int = 65_000,
    val flexBendOhm: Int = 160_000,
    val flexTolerancePct: Int = 5
) {
    fun toJson(): JSONObject {
        val pct = flexTolerancePct.coerceIn(1, 50)
        return JSONObject().apply {
            put("flexPin", flexPin)
            put("flexPullupOhm", flexPullupOhm)
            put("flexStraightOhm", flexStraightOhm)
            put("flexBendOhm", flexBendOhm)
            put("flexTolerancePct", pct)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FlexSettings {
            val pct = json.optInt("flexTolerancePct", 5).coerceIn(1, 50)
            return FlexSettings(
                flexPin = json.optInt("flexPin", 32),
                flexPullupOhm = json.optInt("flexPullupOhm", 47_000),
                flexStraightOhm = json.optInt("flexStraightOhm", 65_000),
                flexBendOhm = json.optInt("flexBendOhm", 160_000),
                flexTolerancePct = pct
            )
        }
    }
}
