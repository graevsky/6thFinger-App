package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.utils.isNetworkErrorKey
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Account device list UI state.
 */
internal class AccountDevicesUiState {
    var devices by mutableStateOf<List<DeviceOut>>(emptyList())
    var devicesLoading by mutableStateOf(false)
    var devicesErrorKey by mutableStateOf<String?>(null)

    var showDeviceSettingsDialog by mutableStateOf(false)
    var dialogSelectedKey by mutableStateOf<String?>(null)
    var dialogJson by mutableStateOf("{}")
    var dialogErrorKey by mutableStateOf<String?>(null)
    var dialogBusy by mutableStateOf(false)
    var showConnectWarning by mutableStateOf(false)
    var cloudSettingsByDeviceId by mutableStateOf<Map<String, CloudSettingsState>>(emptyMap())
    var cloudProbeLoading by mutableStateOf(false)

    private var refreshTick by mutableIntStateOf(0)

    internal val refreshKey: Int
        get() = refreshTick

    fun refreshDevices() {
        refreshTick++
    }

    fun cloudStateForChoice(choice: CloudDeviceChoice?): CloudSettingsState? {
        if (choice == null) return null
        val device = choice.device ?: return CloudSettingsState(
            checked = true,
            record = null,
            errorKey = null
        )
        return cloudSettingsByDeviceId[device.id]
    }

    fun openCloudDialog(
        initialKey: String,
        selectableChoices: List<CloudDeviceChoice>
    ) {
        dialogSelectedKey = initialKey
        dialogErrorKey = null
        dialogBusy = false
        val initialChoice = selectableChoices.firstOrNull { it.key == initialKey }
        val initialRecord = cloudStateForChoice(initialChoice)?.record
        dialogJson = initialRecord?.let { settingsToPrettyJson(it.settings) } ?: "{}"
        showDeviceSettingsDialog = true
    }
}

@Composable
internal fun rememberAccountDevicesState(
    username: String?,
    connected: Boolean,
    activeAddress: String,
    activeAlias: String,
    accountVm: AccountViewModel,
    settingsStore: AppSettingsStore
): AccountDevicesUiState {
    val state = remember { AccountDevicesUiState() }
    val cachedDevicesJson by settingsStore.getCachedDevicesJson().collectAsState(initial = null)

    LaunchedEffect(username, connected, activeAddress, activeAlias, state.refreshKey) {
        if (username == null) {
            state.devices = emptyList()
            state.devicesErrorKey = null
            state.cloudSettingsByDeviceId = emptyMap()
            settingsStore.setCachedDevicesJson(null)
            return@LaunchedEffect
        }

        state.devicesLoading = true
        state.devicesErrorKey = null
        try {
            if (connected && activeAddress.isNotEmpty()) {
                runCatching {
                    accountVm.ensureDevice(
                        address = activeAddress,
                        alias = activeAlias.ifBlank { null }
                    )
                }.onFailure { e -> state.devicesErrorKey = e.message }
            }

            val list = accountVm.fetchDevices()
            state.devices = list
            cacheDevices(settingsStore, list)
        } catch (e: Exception) {
            val key = e.message
            state.devicesErrorKey = key
            val cached = readCachedDevices(cachedDevicesJson)
            state.devices =
                if (isNetworkErrorKey(key) && cached.isNotEmpty()) cached else emptyList()
        } finally {
            state.devicesLoading = false
        }
    }

    LaunchedEffect(state.devices.map { it.id }.joinToString("|"), username) {
        if (username == null || state.devices.isEmpty()) {
            state.cloudSettingsByDeviceId = emptyMap()
            state.cloudProbeLoading = false
            return@LaunchedEffect
        }

        state.cloudProbeLoading = true
        val next = mutableMapOf<String, CloudSettingsState>()
        state.devices.forEach { device ->
            next[device.id] = try {
                val record = accountVm.fetchDeviceSettingsRecord(device.id)
                CloudSettingsState(
                    checked = true,
                    record = record,
                    errorKey = null
                )
            } catch (e: Exception) {
                CloudSettingsState(
                    checked = false,
                    record = null,
                    errorKey = e.message
                )
            }
        }
        state.cloudSettingsByDeviceId = next
        state.cloudProbeLoading = false
    }

    LaunchedEffect(state.devicesErrorKey, username) {
        if (!isNetworkErrorKey(state.devicesErrorKey)) return@LaunchedEffect
        while (isNetworkErrorKey(state.devicesErrorKey) && username != null) {
            delay(30_000L)
            state.refreshDevices()
        }
    }

    return state
}

@Composable
internal fun rememberSelectableCloudChoices(
    devices: List<DeviceOut>,
    connected: Boolean,
    activeAddress: String,
    activeAlias: String
): List<CloudDeviceChoice> {
    return remember(devices, connected, activeAddress, activeAlias) {
        val serverChoices =
            devices.sortedWith(
                compareBy<DeviceOut>(
                    { !(connected && it.address.equals(activeAddress, ignoreCase = true)) },
                    { (it.alias ?: it.address).lowercase() }
                )
            ).map { device ->
                CloudDeviceChoice(
                    device = device,
                    address = device.address,
                    alias = device.alias,
                    isConnectedDevice = connected && device.address.equals(
                        activeAddress,
                        ignoreCase = true
                    )
                )
            }

        val localOnlyChoice =
            if (connected && activeAddress.isNotBlank() && serverChoices.none {
                    it.address.equals(activeAddress, ignoreCase = true)
                }
            ) {
                CloudDeviceChoice(
                    device = null,
                    address = activeAddress,
                    alias = activeAlias.ifBlank { null },
                    isConnectedDevice = true
                )
            } else {
                null
            }

        buildList {
            if (localOnlyChoice != null) add(localOnlyChoice)
            addAll(serverChoices)
        }
    }
}

internal suspend fun resolveCloudChoice(
    choice: CloudDeviceChoice,
    accountVm: AccountViewModel,
    state: AccountDevicesUiState,
    settingsStore: AppSettingsStore
): DeviceOut {
    val existing = choice.device
    if (existing != null && existing.id.isNotBlank()) return existing

    val ensured = accountVm.ensureDevice(
        address = choice.address,
        alias = choice.alias
    )
    mergeDeviceIntoList(state, settingsStore, ensured)
    state.dialogSelectedKey = ensured.id
    return ensured
}

internal suspend fun refreshCloudSettingsState(
    device: DeviceOut,
    force: Boolean,
    accountVm: AccountViewModel,
    state: AccountDevicesUiState
): CloudSettingsState {
    val cached = state.cloudSettingsByDeviceId[device.id]
    if (!force && cached != null && (cached.checked || !cached.errorKey.isNullOrBlank())) {
        return cached
    }

    return try {
        val record = accountVm.fetchDeviceSettingsRecord(device.id)
        val next = CloudSettingsState(checked = true, record = record, errorKey = null)
        state.cloudSettingsByDeviceId =
            state.cloudSettingsByDeviceId.toMutableMap().apply { put(device.id, next) }
        next
    } catch (e: Exception) {
        val next = CloudSettingsState(checked = false, record = null, errorKey = e.message)
        state.cloudSettingsByDeviceId =
            state.cloudSettingsByDeviceId.toMutableMap().apply { put(device.id, next) }
        next
    }
}

internal suspend fun mergeDeviceIntoList(
    state: AccountDevicesUiState,
    settingsStore: AppSettingsStore,
    device: DeviceOut
) {
    val updated =
        (state.devices.filterNot {
            it.id == device.id || it.address.equals(device.address, ignoreCase = true)
        } + device).sortedBy { (it.alias ?: it.address).lowercase() }

    state.devices = updated
    cacheDevices(settingsStore, updated)
}

private suspend fun cacheDevices(
    settingsStore: AppSettingsStore,
    devices: List<DeviceOut>
) {
    val array = JSONArray()
    devices.forEach { device ->
        val json = JSONObject()
        json.put("id", device.id)
        json.put("address", device.address)
        json.put("alias", device.alias ?: JSONObject.NULL)
        array.put(json)
    }
    settingsStore.setCachedDevicesJson(array.toString())
}

private fun readCachedDevices(raw: String?): List<DeviceOut> {
    val json = raw ?: return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            DeviceOut(
                id = entry.getString("id"),
                owner_id = "",
                address = entry.getString("address"),
                alias = if (entry.isNull("alias")) null else entry.getString("alias"),
                created_at = ""
            )
        }
    }.getOrDefault(emptyList())
}
