package com.example.a6thfingercontrollapp.ble

import android.app.Application

object BleRepository {
    @Volatile private var client: BleClient? = null

    fun get(app: Application): BleClient {
        return client
                ?: synchronized(this) {
                    client ?: BleClient(app.applicationContext).also { client = it }
                }
    }
}
