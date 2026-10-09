package com.omnibuds.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives notification action intents.
 *
 * Phase 26 (OB-P26-REQ-009/010): every intent is treated as external input.
 * The receiver validates the request through [NotificationActionDispatcher]
 * and never trusts it merely because OmniBuds created it.
 *
 * Manifest-declared, exported=false — only the system's PendingIntent
 * delivery can reach it.
 */
class OmniBudsNotificationReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationIds.ACTION_TOGGLE_FEATURE) return

        val request = ActionRequest(
            actionId = intent.getStringExtra(NotificationIds.EXTRA_ACTION_ID).orEmpty(),
            deviceId = intent.getStringExtra(NotificationIds.EXTRA_DEVICE_ID).orEmpty(),
            sessionId = intent.getStringExtra(NotificationIds.EXTRA_SESSION_ID),
            featureId = intent.getStringExtra(NotificationIds.EXTRA_FEATURE_ID).orEmpty(),
            nonce = intent.getStringExtra(NotificationIds.EXTRA_NONCE).orEmpty(),
        )

        val pendingResult = goAsync()
        scope.launch {
            try {
                NotificationDependencies.dispatch(request)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Process-scoped dependencies for notification actions.
 *
 * The host application installs these at startup. Tests replace them.
 */
object NotificationDependencies {
    private var dispatchFn: (suspend (ActionRequest) -> Unit)? = null

    fun install(dispatch: suspend (ActionRequest) -> Unit) {
        dispatchFn = dispatch
    }

    suspend fun dispatch(request: ActionRequest) {
        dispatchFn?.invoke(request)
            ?: throw IllegalStateException("NotificationDependencies not installed")
    }

    fun resetForTests() {
        dispatchFn = null
    }
}
