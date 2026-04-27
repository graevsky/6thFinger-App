package com.example.a6thfingercontrolapp.data.repositories

import android.app.Application
import com.example.a6thfingercontrolapp.ble.BleClient

/**
 * App wide BLE client repo.
 */
object BleRepository {
    @Volatile
    private var client: BleClient? = null


    fun get(app: Application): BleClient {
        return client
            ?: synchronized(this) {
                client ?: BleClient(app.applicationContext).also { client = it }
            }
    }
}