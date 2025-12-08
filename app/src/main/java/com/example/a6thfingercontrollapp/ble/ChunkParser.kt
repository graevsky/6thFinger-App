package com.example.a6thfingercontrollapp.ble

import org.json.JSONObject

class ChunkParser {

    private val buffer = StringBuilder()
    private var receiving = false
    private var completeJson: JSONObject? = null

    fun push(chunkRaw: String): Boolean {
        val chunk = chunkRaw.filter { it.code in 32..126 }.trim()

        if (chunk.isEmpty()) return false

        if (chunk.startsWith("[BEGIN]")) {
            receiving = true
            buffer.clear()
            completeJson = null
            return false
        }

        if (chunk.startsWith("[END]")) {
            if (!receiving) return false
            receiving = false

            val data = buffer.toString()
            buffer.clear()

            completeJson =
                    try {
                        JSONObject(data)
                    } catch (_: Throwable) {
                        null
                    }

            return completeJson != null
        }

        if (receiving) {
            buffer.append(chunk)
        }

        return false
    }

    fun jsonOrNull(): JSONObject? {
        val j = completeJson
        completeJson = null
        return j
    }
}
