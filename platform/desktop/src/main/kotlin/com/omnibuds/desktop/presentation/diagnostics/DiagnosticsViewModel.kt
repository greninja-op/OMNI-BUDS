package com.omnibuds.desktop.presentation.diagnostics

import com.omnibuds.core.diagnostics.DiagnosticSeverity
import com.omnibuds.core.diagnostics.DiagnosticStore
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.core.security.LogRedactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Presentation controller for the Diagnostics screen.
 * Respects redaction policies and bounded UI rendering limits (max 256 items).
 */
class DiagnosticsViewModel(
    private val diagnosticStore: DiagnosticStore,
    private val desktopAdapter: DesktopBluetoothAdapter,
    private val platformDescriptor: PlatformDescriptor,
    private val scope: CoroutineScope,
    private val maxUiDisplayLimit: Int = 256,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(
        DiagnosticsScreenState(
            platformDescriptor = platformDescriptor,
        ),
    )
    val state: StateFlow<DiagnosticsScreenState> = _state.asStateFlow()

    init {
        scope.launch {
            refreshState()
        }
    }

    suspend fun refreshState() {
        mutex.withLock {
            val availability = desktopAdapter.checkAvailability()
            val rawEvents = diagnosticStore.snapshot()
            val filter = _state.value.filterSeverity

            val filtered = if (filter != null) {
                rawEvents.filter { it.event.severity >= filter }
            } else {
                rawEvents
            }

            val uiItems = filtered.takeLast(maxUiDisplayLimit).map { stored ->
                val ev = stored.event
                DiagnosticEventItem(
                    sequence = stored.sequence,
                    timestampEpochMillis = ev.timestampEpochMillis,
                    severity = ev.severity,
                    category = ev.category.name,
                    message = LogRedactor.redact(ev.message),
                    correlationId = ev.operationId?.let { LogRedactor.redact(it) },
                )
            }

            _state.update { current ->
                current.copy(
                    platformDescriptor = platformDescriptor,
                    adapterAvailability = availability,
                    events = uiItems,
                    droppedEventsCount = diagnosticStore.droppedCount,
                )
            }
        }
    }

    fun setSeverityFilter(severity: DiagnosticSeverity?) {
        _state.update { it.copy(filterSeverity = severity) }
        scope.launch {
            refreshState()
        }
    }

    suspend fun clearEvents() {
        mutex.withLock {
            diagnosticStore.clear()
            _state.update {
                it.copy(
                    events = emptyList(),
                    droppedEventsCount = 0L,
                    exportResult = null,
                )
            }
        }
    }

    fun exportSanitizedJson(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"applicationVersion\": \"${current.applicationVersion}\",\n")
        sb.append("  \"platform\": \"${current.platformDescriptor.platformType.name}\",\n")
        sb.append("  \"os\": \"${current.platformDescriptor.osName ?: "unknown"}\",\n")
        sb.append("  \"adapterStatus\": \"${current.adapterAvailability.technicalName}\",\n")
        sb.append("  \"events\": [\n")

        val eventStrings = current.events.map { ev ->
            "    {\"seq\": ${ev.sequence}, \"time\": ${ev.timestampEpochMillis}, \"level\": \"${ev.severity}\", \"cat\": \"${ev.category}\", \"msg\": \"${escapeJson(ev.message)}\"}"
        }
        sb.append(eventStrings.joinToString(",\n"))
        sb.append("\n  ]\n}")

        val json = sb.toString()
        _state.update { it.copy(exportResult = "Exported ${current.events.size} sanitized events (${json.length} bytes)") }
        return json
    }

    private fun escapeJson(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    fun clearExportStatus() {
        _state.update { it.copy(exportResult = null) }
    }
}
