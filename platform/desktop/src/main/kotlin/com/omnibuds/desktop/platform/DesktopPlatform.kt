package com.omnibuds.desktop.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformDiagnosticSink
import com.omnibuds.core.platform.PlatformIdentifierSource
import com.omnibuds.core.platform.PlatformLifecycleSource
import com.omnibuds.core.platform.PlatformLifecycleState
import com.omnibuds.core.platform.PlatformStoragePort
import com.omnibuds.core.platform.PlatformType
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Platform descriptor factory and helpers for desktop hosts.
 */
object DesktopPlatformDescriptor {
    fun forLinux(
        osVersion: String? = "Linux 6.x",
        architecture: String? = "x86_64",
    ): PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.LINUX,
        osName = "Linux",
        osVersion = osVersion,
        architecture = architecture,
        candidateTransports = setOf(
            TransportKind.CLASSIC_BLUETOOTH,
            TransportKind.BLE,
            TransportKind.RFCOMM,
        ),
    )

    fun forMacOs(
        osVersion: String? = "macOS 14.x",
        architecture: String? = "aarch64",
    ): PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.MACOS,
        osName = "macOS",
        osVersion = osVersion,
        architecture = architecture,
        candidateTransports = setOf(
            TransportKind.CLASSIC_BLUETOOTH,
            TransportKind.BLE,
        ),
    )

    fun forWindows(
        osVersion: String? = "Windows 11",
        architecture: String? = "x86_64",
    ): PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.WINDOWS,
        osName = "Windows",
        osVersion = osVersion,
        architecture = architecture,
        candidateTransports = setOf(
            TransportKind.CLASSIC_BLUETOOTH,
            TransportKind.BLE,
        ),
    )

    fun detectCurrent(): PlatformDescriptor {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val osVersion = System.getProperty("os.version")
        val arch = System.getProperty("os.arch")
        return when {
            os.contains("linux") -> forLinux(osVersion, arch)
            os.contains("mac") || os.contains("darwin") -> forMacOs(osVersion, arch)
            os.contains("win") -> forWindows(osVersion, arch)
            else -> PlatformDescriptor(
                platformType = PlatformType.UNKNOWN,
                osName = os,
                osVersion = osVersion,
                architecture = arch,
                candidateTransports = emptySet(),
            )
        }
    }
}

/**
 * Desktop implementation of [PlatformIdentifierSource].
 */
class DesktopPlatformIdentifierSource(
    private val prefix: String = "desktop-op",
) : PlatformIdentifierSource {
    private var sequence = 1000L

    override fun generateOperationId(prefix: String?): String {
        val effective = prefix?.takeIf { it.isNotBlank() } ?: this.prefix
        val id = "$effective-${sequence++}"
        return id
    }

    override fun generateNonce(): String {
        val nonce = "desk-nonce-${sequence++}-${System.currentTimeMillis()}"
        return nonce
    }
}

/**
 * Desktop implementation of [PlatformLifecycleSource].
 * Tracks host window/app transitions (Foreground, Background, Suspended, Terminating).
 */
class DesktopPlatformLifecycleSource(
    initialState: PlatformLifecycleState = PlatformLifecycleState.FOREGROUND,
) : PlatformLifecycleSource {
    private var state: PlatformLifecycleState = initialState
    private val events = MutableSharedFlow<PlatformLifecycleState>(replay = 1)

    init {
        events.tryEmit(initialState)
    }

    override val currentState: PlatformLifecycleState get() = state

    override val lifecycleEvents: Flow<PlatformLifecycleState> = events.asSharedFlow()

    suspend fun updateState(newState: PlatformLifecycleState) {
        if (state == newState) return
        state = newState
        events.emit(newState)
    }
}

/**
 * Thread-safe desktop key-value storage implementation for [PlatformStoragePort].
 * Backed by an in-memory map with thread-safe Mutex synchronization.
 */
class DesktopPlatformStoragePort(
    initialEntries: Map<String, String> = emptyMap(),
) : PlatformStoragePort {
    private val mutex = Mutex()
    private val store = initialEntries.toMutableMap()

    override suspend fun get(key: String): OperationOutcome<String?> = mutex.withLock {
        if (key.isBlank()) {
            return OperationOutcome.Failure(
                com.omnibuds.core.common.OmniBudsError(
                    category = com.omnibuds.core.common.OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "storage.get",
                    detail = "key must not be blank",
                ),
            )
        }
        OperationOutcome.Success(store[key])
    }

    override suspend fun set(key: String, value: String): OperationOutcome<Unit> = mutex.withLock {
        if (key.isBlank()) {
            return OperationOutcome.Failure(
                com.omnibuds.core.common.OmniBudsError(
                    category = com.omnibuds.core.common.OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "storage.set",
                    detail = "key must not be blank",
                ),
            )
        }
        store[key] = value
        OperationOutcome.Success(Unit)
    }

    override suspend fun remove(key: String): OperationOutcome<Unit> = mutex.withLock {
        if (key.isBlank()) {
            return OperationOutcome.Failure(
                com.omnibuds.core.common.OmniBudsError(
                    category = com.omnibuds.core.common.OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "storage.remove",
                    detail = "key must not be blank",
                ),
            )
        }
        store.remove(key)
        OperationOutcome.Success(Unit)
    }

    override suspend fun contains(key: String): OperationOutcome<Boolean> = mutex.withLock {
        if (key.isBlank()) {
            return OperationOutcome.Failure(
                com.omnibuds.core.common.OmniBudsError(
                    category = com.omnibuds.core.common.OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "storage.contains",
                    detail = "key must not be blank",
                ),
            )
        }
        OperationOutcome.Success(store.containsKey(key))
    }

    override suspend fun clear(): OperationOutcome<Unit> = mutex.withLock {
        store.clear()
        OperationOutcome.Success(Unit)
    }

    suspend fun snapshot(): Map<String, String> = mutex.withLock {
        store.toMap()
    }
}

/**
 * Desktop diagnostic sink buffering logs in memory with optional stdout output.
 */
class DesktopPlatformDiagnosticSink(
    private val minLevel: VerificationLevel = VerificationLevel.INFERRED,
    private val maxCapacity: Int = 512,
) : PlatformDiagnosticSink {
    data class Entry(
        val timestampEpochMillis: Long,
        val level: VerificationLevel,
        val tag: String,
        val message: String,
    )

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()

    override fun isEnabled(level: VerificationLevel): Boolean =
        level.ordinal >= minLevel.ordinal

    override fun emit(
        level: VerificationLevel,
        tag: String,
        message: String,
    ): OperationOutcome<Unit> {
        if (!isEnabled(level)) return OperationOutcome.Success(Unit)

        val entry = Entry(
            timestampEpochMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )

        synchronized(lock) {
            if (buffer.size >= maxCapacity) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
        }

        return OperationOutcome.Success(Unit)
    }

    fun entries(): List<Entry> = synchronized(lock) {
        buffer.toList()
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
    }
}
