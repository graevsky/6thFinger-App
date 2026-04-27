package com.example.a6thfingercontrolapp.ble.comms

import android.util.Log
import com.example.a6thfingercontrolapp.ble.ChunkParser
import com.example.a6thfingercontrolapp.ble.NewBleConstants
import org.json.JSONObject
import java.util.UUID

/**
 * Reassembles JSON payloads transferred in BLE chunks.
 *
 * The firmware sends messages in a very simple framed format:
 * [BEGIN]
 * <json split into arbitrary chunks>
 * [END]
 */
internal class BleInboundStreamRouter(
    private val onJson: (JSONObject) -> Unit,
    private val onRawCfgText: (String) -> Unit
) {
    private var teleParser = ChunkParser()
    private var cfgParser = ChunkParser()
    private var ackParser = ChunkParser()

    private var rawCfgInProgress = false
    private val rawCfgBuffer = StringBuilder()

    fun reset() {
        teleParser = ChunkParser()
        cfgParser = ChunkParser()
        ackParser = ChunkParser()
        rawCfgInProgress = false
        rawCfgBuffer.clear()
        onRawCfgText("")
    }

    fun handleNotify(uuid: UUID, bytes: ByteArray) {
        val text = decodeAscii(bytes)
        when (uuid) {
            NewBleConstants.TELE_UUID -> handleTeleStreamChunk(text)
            NewBleConstants.CFG_OUT_UUID -> handleCfgStreamChunk(text)
            NewBleConstants.ACK_UUID -> handleAckStreamChunk(text)
            else -> Log.w(TAG, "notify from unexpected UUID=$uuid")
        }
    }

    private fun handleTeleStreamChunk(chunk: String) {
        if (!teleParser.push(chunk)) return
        val json = teleParser.jsonOrNull() ?: return
        onJson(json)
    }

    private fun handleCfgStreamChunk(chunk: String) {
        pushRawCfgChunk(chunk)
        if (!cfgParser.push(chunk)) return
        val json = cfgParser.jsonOrNull() ?: return
        onJson(json)
    }

    private fun handleAckStreamChunk(chunk: String) {
        if (!ackParser.push(chunk)) return
        val json = ackParser.jsonOrNull() ?: return
        onJson(json)
    }

    private fun decodeAscii(bytes: ByteArray): String {
        val text = StringBuilder()
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value in 32..126) text.append(value.toChar())
        }
        return text.toString().trim()
    }

    private fun pushRawCfgChunk(chunk: String) {
        when (chunk) {
            "[BEGIN]" -> {
                rawCfgInProgress = true
                rawCfgBuffer.clear()
                onRawCfgText("[BEGIN]")
            }

            "[END]" -> {
                rawCfgInProgress = false
                onRawCfgText("[BEGIN]\n${rawCfgBuffer}\n[END]")
            }

            else -> {
                if (rawCfgInProgress) {
                    rawCfgBuffer.append(chunk)
                    onRawCfgText("[BEGIN]\n${rawCfgBuffer}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "BLE_CFG"
    }
}
