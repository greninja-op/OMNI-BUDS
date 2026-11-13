package com.omnibuds.android.hardware.protocol

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A single noise-control audit event.
 *
 * The point of this log is to correlate what the user tapped with what the app
 * believed was active, what it actually sent, and what the buds acknowledged.
 * Without it, an ACK-only protocol makes a wrong mode look successful.
 */
data class AncAuditEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val stage: String,
    val address: String,
    val activeMode: String? = null,
    val clickedMode: String? = null,
    val requestedMode: String? = null,
    val sentHex: String? = null,
    val mapping: String? = null,
    val responseHex: String? = null,
    val note: String? = null,
)

/**
 * In-memory audit trail for ANC/Transparency/Off interactions, mirrored to
 * logcat under the `AncAudit` tag and shown in the in-app diagnostic drawer.
 */
object AncAuditLog {

    private const val MAX_ENTRIES = 200
    private const val TAG = "AncAudit"

    private val _entries = MutableStateFlow<List<AncAuditEntry>>(emptyList())
    val entries: StateFlow<List<AncAuditEntry>> = _entries.asStateFlow()

    fun record(
        stage: String,
        address: String,
        activeMode: String? = null,
        clickedMode: String? = null,
        requestedMode: String? = null,
        sentHex: String? = null,
        mapping: String? = null,
        responseHex: String? = null,
        note: String? = null,
    ) {
        val entry = AncAuditEntry(
            stage = stage,
            address = address,
            activeMode = activeMode,
            clickedMode = clickedMode,
            requestedMode = requestedMode,
            sentHex = sentHex,
            mapping = mapping,
            responseHex = responseHex,
            note = note,
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        Log.i(
            TAG,
            buildString {
                append(stage)
                append(" addr=").append(address)
                activeMode?.let { append(" active=").append(it) }
                clickedMode?.let { append(" clicked=").append(it) }
                requestedMode?.let { append(" requested=").append(it) }
                sentHex?.let { append(" sent=").append(it) }
                mapping?.let { append(" map=").append(it) }
                responseHex?.let { append(" rx=").append(it) }
                note?.let { append(" note=").append(it) }
            },
        )
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
