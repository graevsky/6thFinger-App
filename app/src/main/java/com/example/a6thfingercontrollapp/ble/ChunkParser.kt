package com.example.a6thfingercontrollapp.ble

import android.util.Log
import org.json.JSONObject

class ChunkParser {

    private val buffer = StringBuilder()
    private var receiving = false
    private var completeJson: JSONObject? = null

    fun push(chunkRaw: String): Boolean {
        val chunk = chunkRaw.filter { it.code in 32..126 }.trim()

        if (chunk.isEmpty()) return false

        Log.d("BLE_CHUNK", "push('$chunkRaw') => '$chunk', receiving=$receiving")

        if (chunk.startsWith("[BEGIN]")) {
            Log.d("BLE_CHUNK", "BEGIN")
            receiving = true
            buffer.clear()
            completeJson = null
            return false
        }

        if (chunk.startsWith("[END]")) {
            if (!receiving) {
                Log.w("BLE_CHUNK", "END without BEGIN, ignore")
                return false
            }
            receiving = false

            val data = buffer.toString()
            buffer.clear()

            Log.d("BLE_CHUNK", "END, buffer='$data'")

            completeJson =
                try {
                    JSONObject(data)
                } catch (t: Throwable) {
                    Log.e("BLE_CHUNK", "JSON parse error: '${data}'", t)
                    null
                }

            return completeJson != null
        }

        if (receiving) {
            buffer.append(chunk)
        } else {
            Log.w("BLE_CHUNK", "Chunk outside message: '$chunk'")
        }

        return false
    }

    fun jsonOrNull(): JSONObject? {
        val j = completeJson
        completeJson = null
        return j
    }
}
