package com.example.a6thfingercontrolapp.data.repositories

import android.app.Application
import com.example.a6thfingercontrolapp.account.AccountSyncCoordinator

/**
 * App wide account sync repo.
 */
object AccountSyncRepository {
    @Volatile
    private var instance: AccountSyncCoordinator? = null

    fun get(app: Application): AccountSyncCoordinator {
        return instance ?: synchronized(this) {
            instance ?: AccountSyncCoordinator(app).also { instance = it }
        }
    }
}