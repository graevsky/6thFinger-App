package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.AppSettingsIn
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.DeviceCreate
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.network.DeviceSettingsIn
import com.example.a6thfingercontrolapp.network.DeviceUpdate
import com.example.a6thfingercontrolapp.utils.wrapAuthErrors
import retrofit2.HttpException

/**
 * App settings and prothesis sync api controller APIs.
 */
internal class AuthCloudService(
    private val api: BackendApi,
    private val sessionGateway: AuthSessionGateway
) {
    suspend fun pullAppSettings(): Map<String, Any?>? = wrapAuthErrors {
        val result = sessionGateway.withAuthorizedRequest { auth ->
            api.getAppSettings(auth)
        }
        result.payload
    }

    suspend fun pushAppSettings(payload: Map<String, Any?>) = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.putAppSettings(auth, AppSettingsIn(payload))
        }
    }

    suspend fun listDevices(): List<DeviceOut> = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.listDevices(auth)
        }
    }

    suspend fun pushDeviceSettings(
        deviceId: String,
        settings: EspSettings
    ): DeviceSettingsRecord = wrapAuthErrors {
        val payload = AuthCloudMapper.espToPayload(settings)

        val result = sessionGateway.withAuthorizedRequest { auth ->
            api.postDeviceSettings(
                auth = auth,
                deviceId = deviceId,
                body = DeviceSettingsIn(payload = payload)
            )
        }

        DeviceSettingsRecord(
            settings = AuthCloudMapper.payloadToEsp(result.payload) ?: settings,
            version = result.version,
            updatedAt = result.updated_at
        )
    }

    suspend fun getDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? = wrapAuthErrors {
        val response = sessionGateway.withAuthorizedResponse { auth ->
            api.getDeviceSettingsResponse(auth = auth, deviceId = deviceId)
        }

        if (response.code() == 404) return@wrapAuthErrors null
        if (!response.isSuccessful) throw HttpException(response)

        val body = response.body() ?: return@wrapAuthErrors null
        val settings = AuthCloudMapper.payloadToEsp(body.payload) ?: return@wrapAuthErrors null

        DeviceSettingsRecord(
            settings = settings,
            version = body.version,
            updatedAt = body.updated_at
        )
    }

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            val existing = api.listDevices(auth).firstOrNull {
                it.address.equals(address, ignoreCase = true)
            }

            if (existing != null) {
                val desiredAlias = alias?.takeIf { it.isNotBlank() }
                if (desiredAlias != null && existing.alias != desiredAlias) {
                    api.updateDevice(
                        auth = auth,
                        deviceId = existing.id,
                        body = DeviceUpdate(alias = desiredAlias)
                    )
                } else {
                    existing
                }
            } else {
                api.createDevice(
                    auth = auth,
                    body = DeviceCreate(address = address, alias = alias)
                )
            }
        }
    }
}
