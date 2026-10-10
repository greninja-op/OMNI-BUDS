package com.omnibuds.android.presentation.integration

import com.omnibuds.android.notification.NotificationCoordinator
import com.omnibuds.android.tile.QuickSettingsCoordinator
import com.omnibuds.android.widget.WidgetCoordinator
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Surface coordinator ensuring strict state convergence across:
 * 1. Main Android Application UI
 * 2. Quick Settings Tile ([QuickSettingsCoordinator])
 * 3. Notification Controls ([NotificationCoordinator])
 * 4. Home-Screen Widget ([WidgetCoordinator])
 *
 * All surfaces observe the single authoritative [GlobalDeviceStateRepository].
 */
class AndroidSurfaceCoordinator(
    val repository: GlobalDeviceStateRepository,
    val quickSettingsCoordinator: QuickSettingsCoordinator? = null,
    val notificationCoordinator: NotificationCoordinator? = null,
    val widgetCoordinator: WidgetCoordinator? = null,
) {
    private val _selectedDeviceId = MutableStateFlow<GlobalDeviceId?>(null)
    val selectedDeviceId: StateFlow<GlobalDeviceId?> = _selectedDeviceId.asStateFlow()

    fun setSelectedDevice(deviceId: GlobalDeviceId?) {
        _selectedDeviceId.value = deviceId
    }

    suspend fun getDeviceState(deviceId: GlobalDeviceId): GlobalDeviceState? {
        return repository.getDevice(deviceId)
    }

    /**
     * Reconciles target device resolution across tile, notification, and widget surfaces.
     */
    fun resolveActiveTarget(allDevices: Map<GlobalDeviceId, GlobalDeviceState>): GlobalDeviceId? {
        val explicitlySelected = _selectedDeviceId.value
        if (explicitlySelected != null && allDevices.containsKey(explicitlySelected)) {
            return explicitlySelected
        }
        // First connected device
        return allDevices.entries.firstOrNull { it.value.connection is com.omnibuds.core.globalstate.ConnectionState.Connected }?.key
            ?: allDevices.keys.firstOrNull()
    }
}
