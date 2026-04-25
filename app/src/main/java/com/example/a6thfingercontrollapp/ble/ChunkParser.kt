package com.example.a6thfingercontrollapp.ble

//import android.util.Log
import org.json.JSONObject

/**
 * Reassembles JSON payloads transferred in BLE chunks.
 *
 * The firmware sends messages in a very simple framed format:
 * [BEGIN]
 * <json split into arbitrary chunks>
 * [END]
 */
class ChunkParser {

    /**
     * Buffer for the current in-progress payload.
     */
    private val buffer = StringBuilder()

    /**
     * True while if the data is in between [BEGIN] and [END].
     */
    private var receiving = false

    /**
     * Parsed JSON that is ready to be consumed once
     */
    private var completeJson: JSONObject? = null

    /**
     * Pushes one BLE text chunk into the parser.
     *
     * @return true if a full and valid JSON object has just been reconstructed.
     */
    fun push(chunkRaw: String): Boolean {
        val chunk = chunkRaw.filter { it.code in 32..126 }.trim()

        if (chunk.isEmpty()) return false

        //Log.d("BLE_CHUNK", "push('$chunkRaw') => '$chunk', receiving=$receiving")

        if (chunk.startsWith("[BEGIN]")) {
            //Log.d("BLE_CHUNK", "BEGIN")
            receiving = true
            buffer.clear()
            completeJson = null
            return false
        }

        if (chunk.startsWith("[END]")) {
            if (!receiving) {
                //Log.w("BLE_CHUNK", "END without BEGIN, ignore")
                return false
            }
            receiving = false

            val data = buffer.toString()
            buffer.clear()

            //Log.d("BLE_CHUNK", "END, buffer='$data'")

            completeJson =
                try {
                    JSONObject(data)
                } catch (t: Throwable) {
                    //Log.e("BLE_CHUNK", "JSON parse error: '${data}'", t)
                    null
                }

            return completeJson != null
        }

        if (receiving) {
            // Regular chunk inside the current framed message.
            buffer.append(chunk)
        } else {
            //Log.w("BLE_CHUNK", "Chunk outside message: '$chunk'")
        }

        return false
    }

    /**
     * Returns the completed JSON once and clears it from the parser.
     */
    fun jsonOrNull(): JSONObject? {
        val j = completeJson
        completeJson = null
        return j
    }
}
