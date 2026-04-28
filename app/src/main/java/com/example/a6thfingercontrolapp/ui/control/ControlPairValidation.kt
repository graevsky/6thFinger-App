package com.example.a6thfingercontrolapp.ui.control

import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.ESP_PAIR_COUNT
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_FLEX
import com.example.a6thfingercontrolapp.ble.settings.PIN_PLACEHOLDER

private data class PairIssue(
    val pairIdx: Int,
    val missing: MissingPart
)

private enum class MissingPart { Flex, Servo, Emg }

/**
 * Returns the pair indices that should remain visible in the control screen.
 */
internal fun visiblePairIndices(
    s: EspSettings,
    pairsCount: Int
): List<Int> {
    val maxVisibleIndex = maxOf(pairsCount - 1, highestConfiguredPairIndex(s))
    return (0..maxVisibleIndex.coerceIn(0, ESP_PAIR_COUNT - 1)).toList()
}

private fun highestConfiguredPairIndex(s: EspSettings): Int {
    var result = 0
    for (i in 0 until ESP_PAIR_COUNT) {
        if (hasAnyPairData(s, i)) {
            result = i
        }
    }
    return result
}

internal fun calculateVisiblePairsCount(s: EspSettings): Int {
    return (highestConfiguredPairIndex(s) + 1).coerceIn(1, ESP_PAIR_COUNT)
}

private fun hasAnyPairData(s: EspSettings, pairIdx: Int): Boolean {
    val flexSet = isFlexConfigured(s, pairIdx)
    val servoSet = isServoConfigured(s, pairIdx)
    val emgSet = isEmgConfigured(s, pairIdx)
    val source = s.pairInputSettings.getOrNull(pairIdx)?.inputSource ?: INPUT_SOURCE_FLEX
    return flexSet || servoSet || emgSet || source == INPUT_SOURCE_EMG
}

private fun isFlexConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val flexPin = s.flexSettings.getOrNull(pairIdx)?.flexPin ?: PIN_PLACEHOLDER
    return flexPin != PIN_PLACEHOLDER
}

private fun isServoConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val servoPin = s.servoSettings.getOrNull(pairIdx)?.servoPin ?: PIN_PLACEHOLDER
    return servoPin != PIN_PLACEHOLDER
}

private fun isEmgConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val emg = s.emgSettings.getOrNull(pairIdx) ?: return false
    return emg.activePinsValid()
}

internal fun findIncompletePairsIssuesVisibleOnly(
    s: EspSettings,
    pairsCount: Int,
    pairNo: String,
    flexNotSet: String,
    servoNotSet: String,
    emgNotSet: String
): List<String> {
    val issues = mutableListOf<PairIssue>()

    fun isVisible(i: Int): Boolean {
        return i in visiblePairIndices(s, pairsCount)
    }

    for (i in 0 until ESP_PAIR_COUNT) {
        if (!isVisible(i)) continue

        val source = s.pairInputSettings.getOrNull(i)?.inputSource ?: INPUT_SOURCE_FLEX
        val flexSet = isFlexConfigured(s, i)
        val servoSet = isServoConfigured(s, i)
        val emgSet = isEmgConfigured(s, i)

        if (source == INPUT_SOURCE_EMG) {
            if ((emgSet || servoSet) && (emgSet xor servoSet)) {
                issues += PairIssue(
                    pairIdx = i,
                    missing = if (!emgSet) MissingPart.Emg else MissingPart.Servo
                )
            }
        } else {
            if ((flexSet || servoSet) && (flexSet xor servoSet)) {
                issues += PairIssue(
                    pairIdx = i,
                    missing = if (!flexSet) MissingPart.Flex else MissingPart.Servo
                )
            }
        }
    }

    return issues.map { issue ->
        val pairNum = issue.pairIdx + 1
        when (issue.missing) {
            MissingPart.Flex -> "$pairNo $pairNum: $flexNotSet"
            MissingPart.Servo -> "$pairNo $pairNum: $servoNotSet"
            MissingPart.Emg -> "$pairNo $pairNum: $emgNotSet"
        }
    }
}

internal fun hasTelemetryData(t: Telemetry): Boolean {
    if (t.fsrOhm.isFinite()) return true
    if (t.fsrForceN.isFinite()) return true
    if (t.flexOhm.any { it.isFinite() }) return true
    if (t.servoDeg.any { it.isFinite() }) return true
    if (t.emgCh0.any { it.isFinite() }) return true
    if (t.emgEvent.any { it >= 0 }) return true
    if (t.emgAction.any { it >= 0 }) return true
    if (t.emgCooldownMs.any { it >= 0 }) return true
    if (t.emgBendProgress.any { it >= 0 }) return true
    if (t.emgUnfoldProgress.any { it >= 0 }) return true
    return false
}
