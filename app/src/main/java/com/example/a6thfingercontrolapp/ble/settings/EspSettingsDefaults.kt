package com.example.a6thfingercontrolapp.ble.settings

/** Placeholder for pin that is not used. */
internal const val PIN_PLACEHOLDER = 0xFF

/** Default ESP flex setting builder. */
internal fun espDefaultFlexForIndex(idx: Int): FlexSettings =
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

/** Default ESP servo setting builder. */
internal fun espDefaultServoForIndex(idx: Int): ServoSettings =
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

/** Default ESP flex&servo pair builder. */
internal fun espDefaultPairInputForIndex(idx: Int): PairInputSettings =
    PairInputSettings(
        inputSource = INPUT_SOURCE_FLEX
    )

/** Default ESP EMG setting builder. */
internal fun espDefaultEmgForIndex(idx: Int): EmgSettings =
    EmgSettings(
        pin = PIN_PLACEHOLDER,
        bendSnapshotsToBend = 1,
        bendSnapshotsToUnfold = 1,
        snapshotTimeoutSec = 2,
        snapshotSize = 8,
        minUnfoldDelaySec = 2,
        reverseDirection = false
    )

internal fun espDefaultFlexSettings(): Array<FlexSettings> =
    Array(ESP_PAIR_COUNT) { idx -> espDefaultFlexForIndex(idx) }

internal fun espDefaultServoSettings(): Array<ServoSettings> =
    Array(ESP_PAIR_COUNT) { idx -> espDefaultServoForIndex(idx) }

internal fun espDefaultPairInputSettings(): Array<PairInputSettings> =
    Array(ESP_PAIR_COUNT) { idx -> espDefaultPairInputForIndex(idx) }

internal fun espDefaultEmgSettings(): Array<EmgSettings> =
    Array(ESP_PAIR_COUNT) { idx -> espDefaultEmgForIndex(idx) }
