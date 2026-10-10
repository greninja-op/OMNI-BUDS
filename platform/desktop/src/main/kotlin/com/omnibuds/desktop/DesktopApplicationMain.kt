package com.omnibuds.desktop

import com.omnibuds.core.diagnostics.DiagnosticStore
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.platform.PlatformType
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.core.platform.desktop.LinuxBlueZDesktopAdapter
import com.omnibuds.core.platform.desktop.MacOsCoreBluetoothDesktopAdapter
import com.omnibuds.core.platform.desktop.UnsupportedDesktopBluetoothAdapter
import com.omnibuds.core.platform.desktop.WindowsWinRtDesktopAdapter
import com.omnibuds.desktop.platform.DesktopPlatformDescriptor
import com.omnibuds.desktop.platform.DesktopPlatformLifecycleSource
import com.omnibuds.desktop.platform.DesktopPlatformStoragePort
import com.omnibuds.desktop.shell.DesktopApplicationShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Factory for creating a production desktop application shell.
 */
fun createDesktopApplicationShell(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    customAdapter: DesktopBluetoothAdapter? = null,
): DesktopApplicationShell {
    val platformDescriptor = DesktopPlatformDescriptor.detectCurrent()
    val desktopAdapter = customAdapter ?: when (platformDescriptor.platformType) {
        PlatformType.LINUX -> LinuxBlueZDesktopAdapter()
        PlatformType.MACOS -> MacOsCoreBluetoothDesktopAdapter()
        PlatformType.WINDOWS -> WindowsWinRtDesktopAdapter()
        else -> UnsupportedDesktopBluetoothAdapter(platformDescriptor)
    }

    val stateRepository = GlobalDeviceStateRepository()
    val diagnosticStore = DiagnosticStore()
    val storagePort = DesktopPlatformStoragePort()
    val lifecycleSource = DesktopPlatformLifecycleSource()

    return DesktopApplicationShell(
        desktopAdapter = desktopAdapter,
        stateRepository = stateRepository,
        diagnosticStore = diagnosticStore,
        storagePort = storagePort,
        lifecycleSource = lifecycleSource,
        platformDescriptor = platformDescriptor,
        scope = scope,
    )
}

/**
 * Desktop application entry point.
 */
fun main(args: Array<String>) {
    val shell = createDesktopApplicationShell()
    println("[OmniBuds Desktop] Initialized application shell: ${shell.aboutState.appName} v${shell.aboutState.appVersion}")
    println("[OmniBuds Desktop] Platform: ${shell.aboutState.platformDescriptor.osName ?: "unknown"} (${shell.aboutState.platformDescriptor.platformType})")
    println("[OmniBuds Desktop] Navigation ready at destination: ${shell.shellState.value.currentDestination}")
}
