package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.a6thfingercontrollapp.ble.DeviceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "ble_device_settings")

class DeviceSettingsStore(private val context: Context) {
    private fun keyFor(address: String) = stringPreferencesKey("cfg_${address.lowercase()}")

    fun get(address: String): Flow<DeviceSettings?> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyFor(address)] ?: return@map null
            try {
                val j = JSONObject(raw)
                DeviceSettings(
                    fsrPin = j.optInt("fsrPin", 34),
                    fsrPullupOhm = j.optInt("fsrPullupOhm", 4700),
                    fsrStartOhm = j.optInt("fsrStartOhm", 100000),
                    fsrMaxOhm = j.optInt("fsrMaxOhm", 20000),

                    flexPin = j.optInt("flexPin", 35),
                    flexFlatOhm = j.optInt("flexFlatOhm", 45000),
                    flexBendOhm = j.optInt("flexBendOhm", 33400),

                    vibroPin = j.optInt("vibroPin", 25),
                    vibroPulseFreqHz = j.optInt("vibroPulseFreqHz", 10),
                    vibroThreshold = j.optInt("vibroThreshold", 50),
                    vibroPowerPct = j.optInt("vibroPowerPct", 60),

                    servoPin = j.optInt("servoPin", 18),
                    servoMinDeg = j.optInt("servoMinDeg", 40),
                    servoMaxDeg = j.optInt("servoMaxDeg", 180),
                    servoManualMode = j.optBoolean("servoManualMode", false),
                    servoManualDeg = j.optInt("servoManualDeg", 90)
                )
            } catch (_: Throwable) {
                null
            }
        }

    suspend fun set(address: String, s: DeviceSettings) {
        val j = JSONObject().apply {
            put("fsrPin", s.fsrPin)
            put("fsrPullupOhm", s.fsrPullupOhm)
            put("fsrStartOhm", s.fsrStartOhm)
            put("fsrMaxOhm", s.fsrMaxOhm)

            put("flexPin", s.flexPin)
            put("flexFlatOhm", s.flexFlatOhm)
            put("flexBendOhm", s.flexBendOhm)

            put("vibroPin", s.vibroPin)
            put("vibroPulseFreqHz", s.vibroPulseFreqHz)
            put("vibroThreshold", s.vibroThreshold)
            put("vibroPowerPct", s.vibroPowerPct)

            put("servoPin", s.servoPin)
            put("servoMinDeg", s.servoMinDeg)
            put("servoMaxDeg", s.servoMaxDeg)
            put("servoManualMode", s.servoManualMode)
            put("servoManualDeg", s.servoManualDeg)
        }
        context.dataStore.edit { it[keyFor(address)] = j.toString() }
    }
}

