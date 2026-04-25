package com.example.a6thfingercontrollapp.ble

import android.app.Application

/**
 * App-level singleton holder for the BLE client.
 *
 * BLE connection state must survive screen changes, so the app uses
 * one shared BleClient instance instead of creating a new one per screen.
 */
object BleRepository {
    @Volatile
    private var client: BleClient? = null


    /**
     * Returns a single shared BleClient instance.
     */
    fun get(app: Application): BleClient {
        return client
            ?: synchronized(this) {
                client ?: BleClient(app.applicationContext).also { client = it }
            }
    }
}
