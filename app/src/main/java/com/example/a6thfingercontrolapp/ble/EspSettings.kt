package com.example.a6thfingercontrolapp.ble

import org.json.JSONArray
import org.json.JSONObject

/** Input source for a pair: flex sensor drives the servo. */
const val INPUT_SOURCE_FLEX = 0

/** Input source for a pair: EMG module drives the servo. */
const val INPUT_SOURCE_EMG = 1

/** EMG mode without directions. */
const val EMG_MODE_BEND_OTHER = 0

/** EMG mode where direction is interpreted explicitly. */
const val EMG_MODE_DIRECTIONAL = 1

/** No EMG event detected. */
const val EMG_EVENT_NONE = 0

/** Generic "other" EMG event. */
const val EMG_EVENT_OTHER = 1

/** EMG event that means bending action. */
const val EMG_EVENT_BEND = 2

/** EMG event that means unfolding action. */
const val EMG_EVENT_UNFOLD = 3

/** No EMG action currently executed. */
const val EMG_ACTION_NONE = 0

/** Servo is bending because of EMG logic. */
const val EMG_ACTION_BEND = 1

/** Servo is unfolding because of EMG logic. */
const val EMG_ACTION_UNFOLD = 2

/** EMG action was ignored because cooldown is active. */
const val EMG_ACTION_COOLDOWN_IGNORED = 3

/**
 * Placeholder used for something that is not used.
 */
private const val PIN_PLACEHOLDER = 0xFF

/**
 * Full device configuration exchanged with the ESP32 and backend.
 *
 * This is the main configuration snapshot used by the app:
 * - UI edits it
 * - BLE sends it to the board
 * - backend stores it in the cloud
 */
data class EspSettings(
    val fsrPin: Int = 33,
    val fsrPullupOhm: Int = 10_000,
    val fsrSoftThresholdN: Float = 7.0f,
    val fsrHardMaxN: Float = 10.0f,

    val flexSettings: Array<FlexSettings> = defaultFlexSettings(),
    val servoSettings: Array<ServoSettings> = defaultServoSettings(),
    val pairInputSettings: Array<PairInputSettings> = defaultPairInputSettings(),
    val emgSettings: Array<EmgSettings> = defaultEmgSettings(),

    val vibroPin: Int = 5,
    val vibroMode: Int = 0,
    val vibroFreqHz: Int = 150,
    val vibroMaxDuty: Int = 255,
    val vibroMinDuty: Int = 0,
    val vibroSoftPower: Int = 200,
    val vibroPulseBase: Int = 120,

    // PIN (0000 == 0 == disabled)
    val pinCode: Int = 0,

    /**
     * These flags are not always part of the normal serialized config,
     * but can arrive from board/backend and affect UI/auth behavior.
     */
    val pinSet: Boolean = false,
    val authRequired: Boolean = false,

    /** Configuration schema version. */
    val settingsVersion: Int = 2
) {

    /**
     * Serializes settings into JSON sent to the board or stored locally.
     *
     * Only actual configuration values are included here.
     * Runtime/helper fields like pinSet/authRequired are handled separately.
     */
    fun toJsonString(): String {
        val j = JSONObject().apply {
            put("fsrPin", fsrPin)
            put("fsrPullupOhm", fsrPullupOhm)
            put("fsrSoftThresholdN", fsrSoftThresholdN.toDouble())
            put("fsrHardMaxN", fsrHardMaxN.toDouble())

            put("flexSettings", JSONArray().apply { flexSettings.forEach { put(it.toJson()) } })
            put("servoSettings", JSONArray().apply { servoSettings.forEach { put(it.toJson()) } })
            put(
                "pairInputSettings",
                JSONArray().apply { pairInputSettings.forEach { put(it.toJson()) } }
            )
            put("emgSettings", JSONArray().apply { emgSettings.forEach { put(it.toJson()) } })

            put("vibroPin", vibroPin)
            put("vibroMode", vibroMode)
            put("vibroFreqHz", vibroFreqHz)
            put("vibroMaxDuty", vibroMaxDuty)
            put("vibroMinDuty", vibroMinDuty)
            put("vibroSoftPower", vibroSoftPower)
            put("vibroPulseBase", vibroPulseBase)

            put("pinCode", pinCode)

            put("settingsVersion", settingsVersion)
        }
        return j.toString()
    }

    companion object {

        /**
         * Pair 0 is the primary pair and gets a real default config.
         */
        fun defaultFlexForIndex(idx: Int): FlexSettings =
            if (idx == 0) {
                FlexSettings()
            } else {
                FlexSettings(
                    flexPin = PIN_PLACEHOLDER,
                    flexPullupOhm = 0,
                    flexStraightOhm = 0,
                    flexBendOhm = 0,
                    flexTolerancePct = 5
                )
            }

        fun defaultServoForIndex(idx: Int): ServoSettings =
            if (idx == 0) {
                ServoSettings()
            } else {
                ServoSettings(
                    servoPin = PIN_PLACEHOLDER,
                    servoMinDeg = 40,
                    servoMaxDeg = 180,
                    servoManual = 0,
                    servoManualDeg = 90,
                    servoMaxSpeedDegPerSec = 300.0f
                )
            }

        fun defaultPairInputForIndex(idx: Int): PairInputSettings =
            PairInputSettings(
                inputSource = INPUT_SOURCE_FLEX
            )

        fun defaultEmgForIndex(idx: Int): EmgSettings =
            EmgSettings(
                channels = 1,
                pin0 = PIN_PLACEHOLDER,
                pin1 = PIN_PLACEHOLDER,
                pin2 = PIN_PLACEHOLDER,
                mode = EMG_MODE_BEND_OTHER,
                bendFullMoves = 1,
                unfoldFullMoves = 1,
                minSwitchDelaySec = 1,
                reverseDirection = false
            )

        private fun defaultFlexSettings(): Array<FlexSettings> =
            Array(4) { idx -> defaultFlexForIndex(idx) }

        private fun defaultServoSettings(): Array<ServoSettings> =
            Array(4) { idx -> defaultServoForIndex(idx) }

        private fun defaultPairInputSettings(): Array<PairInputSettings> =
            Array(4) { idx -> defaultPairInputForIndex(idx) }

        private fun defaultEmgSettings(): Array<EmgSettings> =
            Array(4) { idx -> defaultEmgForIndex(idx) }

        /**
         * The backend or board may return arrays either as real JSON arrays
         * or as stringified JSON. These helpers support both formats.
         */
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

        private fun parsePairInputArray(json: JSONObject): Array<PairInputSettings> {
            json.optJSONArray("pairInputSettings")?.let { arr ->
                return Array(4) { idx ->
                    val obj = arr.optJSONObject(idx)
                    if (obj != null) PairInputSettings.fromJson(obj) else defaultPairInputForIndex(
                        idx
                    )
                }
            }

            val raw = json.optString("pairInputSettings", null)
            if (raw != null) {
                try {
                    val arr = JSONArray(raw)
                    return Array(4) { idx ->
                        val obj = arr.optJSONObject(idx)
                        if (obj != null) PairInputSettings.fromJson(obj) else defaultPairInputForIndex(
                            idx
                        )
                    }
                } catch (_: Throwable) {
                }
            }

            return defaultPairInputSettings()
        }

        private fun parseEmgArray(json: JSONObject): Array<EmgSettings> {
            json.optJSONArray("emgSettings")?.let { arr ->
                return Array(4) { idx ->
                    val obj = arr.optJSONObject(idx)
                    if (obj != null) EmgSettings.fromJson(obj) else defaultEmgForIndex(idx)
                }
            }

            val raw = json.optString("emgSettings", null)
            if (raw != null) {
                try {
                    val arr = JSONArray(raw)
                    return Array(4) { idx ->
                        val obj = arr.optJSONObject(idx)
                        if (obj != null) EmgSettings.fromJson(obj) else defaultEmgForIndex(idx)
                    }
                } catch (_: Throwable) {
                }
            }

            return defaultEmgSettings()
        }

        /**
         * Builds EspSettings from JSON from the board, local storage or backend.
         *
         * Missing fields fall back to defaults, making parsing fairly tolerant
         * to partial responses and schema changes.
         */
        fun fromJson(json: JSONObject): EspSettings {
            val def = EspSettings()

            val flexArray = parseFlexArray(json)
            val servoArray = parseServoArray(json)
            val pairInputArray = parsePairInputArray(json)
            val emgArray = parseEmgArray(json)

            val pinCode = json.optInt("pinCode", 0)
            val pinSet = json.optBoolean("pinSet", pinCode != 0)
            val authRequired = json.optBoolean("authRequired", false)

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
                pairInputSettings = pairInputArray,
                emgSettings = emgArray,

                vibroPin = json.optInt("vibroPin", def.vibroPin),
                vibroMode = json.optInt("vibroMode", def.vibroMode),
                vibroFreqHz = json.optInt("vibroFreqHz", def.vibroFreqHz),
                vibroMaxDuty = json.optInt("vibroMaxDuty", def.vibroMaxDuty),
                vibroMinDuty = json.optInt("vibroMinDuty", def.vibroMinDuty),
                vibroSoftPower = json.optInt("vibroSoftPower", def.vibroSoftPower),
                vibroPulseBase = json.optInt("vibroPulseBase", def.vibroPulseBase),

                pinCode = pinCode,
                pinSet = pinSet,
                authRequired = authRequired,

                settingsVersion = json.optInt("settingsVersion", def.settingsVersion)
            )
        }
    }
}

/**
 * Per-pair selector that tells the firmware which input source controls the servo.
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

/**
 * EMG configuration for one pair.
 *
 * channels determines how many pins are actually active.
 * Unused pins are set to PIN_PLACEHOLDER.
 */
data class EmgSettings(
    val channels: Int = 1,
    val pin0: Int = PIN_PLACEHOLDER,
    val pin1: Int = PIN_PLACEHOLDER,
    val pin2: Int = PIN_PLACEHOLDER,
    val mode: Int = EMG_MODE_BEND_OTHER,
    val bendFullMoves: Int = 1,
    val unfoldFullMoves: Int = 1,
    val minSwitchDelaySec: Int = 1,
    val reverseDirection: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("channels", channels.coerceIn(1, 3))
            put("pins", JSONArray().apply {
                put(pin0)
                put(pin1)
                put(pin2)
            })
            put("mode", mode.coerceIn(EMG_MODE_BEND_OTHER, EMG_MODE_DIRECTIONAL))
            put("bendFullMoves", bendFullMoves.coerceIn(1, 5))
            put("unfoldFullMoves", unfoldFullMoves.coerceIn(1, 5))
            put("minSwitchDelaySec", minSwitchDelaySec.coerceIn(1, 60))
            put("reverseDirection", reverseDirection)
        }
    }

    /** Returns only pins that are relevant for the selected channel count. */
    fun activePins(): List<Int> = listOf(pin0, pin1, pin2).take(channels.coerceIn(1, 3))

    companion object {
        fun fromJson(json: JSONObject): EmgSettings {
            val pinsArr = json.optJSONArray("pins")
            val p0 = pinsArr?.optInt(0, json.optInt("pin0", PIN_PLACEHOLDER))
                ?: json.optInt("pin0", PIN_PLACEHOLDER)
            val p1 = pinsArr?.optInt(1, json.optInt("pin1", PIN_PLACEHOLDER))
                ?: json.optInt("pin1", PIN_PLACEHOLDER)
            val p2 = pinsArr?.optInt(2, json.optInt("pin2", PIN_PLACEHOLDER))
                ?: json.optInt("pin2", PIN_PLACEHOLDER)

            return EmgSettings(
                channels = json.optInt("channels", 1).coerceIn(1, 3),
                pin0 = p0,
                pin1 = p1,
                pin2 = p2,
                mode = json.optInt("mode", EMG_MODE_BEND_OTHER)
                    .coerceIn(EMG_MODE_BEND_OTHER, EMG_MODE_DIRECTIONAL),
                bendFullMoves = json.optInt("bendFullMoves", 1).coerceIn(1, 5),
                unfoldFullMoves = json.optInt("unfoldFullMoves", 1).coerceIn(1, 5),
                minSwitchDelaySec = json.optInt("minSwitchDelaySec", 1).coerceIn(1, 60),
                reverseDirection = json.optBoolean("reverseDirection", false)
            )
        }
    }
}

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

/**
 * Servo configuration for one pair.
 */
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
