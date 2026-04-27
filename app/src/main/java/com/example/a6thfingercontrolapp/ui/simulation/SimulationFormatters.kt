package com.example.a6thfingercontrolapp.ui.simulation

import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG

/** Checks whether a pair should appear in the simulation selector. */
fun pairShouldBeVisibleInSimulation(
    settings: EspSettings,
    telemetry: Telemetry,
    pairIdx: Int
): Boolean {
    val source = settings.pairInputSettings.getOrNull(pairIdx)?.inputSource ?: 0
    val servoSet = settings.servoSettings.getOrNull(pairIdx)?.servoPin != 0xFF
    val flexSet = settings.flexSettings.getOrNull(pairIdx)?.flexPin != 0xFF
    val emgSet = settings.emgSettings.getOrNull(pairIdx)?.activePins()?.all { it != 0xFF } == true

    val telePresent =
        telemetry.servoDeg.getOrNull(pairIdx)?.isFinite() == true ||
                telemetry.flexOhm.getOrNull(pairIdx)?.isFinite() == true ||
                telemetry.emgChannelValue(pairIdx, 0).isFinite() ||
                telemetry.emgChannelValue(pairIdx, 1).isFinite() ||
                telemetry.emgChannelValue(pairIdx, 2).isFinite() ||
                telemetry.emgEvent.getOrNull(pairIdx)?.let { it >= 0 } == true ||
                telemetry.emgAction.getOrNull(pairIdx)?.let { it >= 0 } == true

    return telePresent || servoSet || (source != INPUT_SOURCE_EMG && flexSet) || (source == INPUT_SOURCE_EMG && emgSet)
}

/** Formats floating point telemetry for compact display. */
fun prettySimulationValue(value: Float): String =
    if (value.isFinite()) "%.1f".format(value) else "--"

/** Formats optional integer telemetry for compact display. */
fun prettySimulationIntValue(value: Int): String =
    if (value >= 0) value.toString() else "--"
