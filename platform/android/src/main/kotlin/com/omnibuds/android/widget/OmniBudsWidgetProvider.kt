package com.omnibuds.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.omnibuds.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * OmniBuds home-screen widget provider.
 *
 * Phase 27: thin shell. Rendering comes from WidgetCoordinator over
 * authoritative engine state; actions dispatch through
 * WidgetActionDispatcher with full revalidation.
 */
class OmniBudsWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val deps = WidgetDependencies.get()
        appWidgetIds.forEach { widgetId ->
            deps.coordinator.register(widgetId, scope)
            // Render immediately from the latest known snapshot.
            scope.launch {
                deps.refresh(widgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val deps = WidgetDependencies.get()
        appWidgetIds.forEach { widgetId -> deps.coordinator.unregister(widgetId) }
    }

    override fun onDisabled(context: Context) {
        scope.cancel()
        WidgetDependencies.get().coordinator.stop()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != WidgetIds.ACTION_WIDGET_TOGGLE) return

        val request = WidgetActionRequest(
            widgetId = intent.getIntExtra(WidgetIds.EXTRA_WIDGET_ID, -1),
            deviceId = intent.getStringExtra(WidgetIds.EXTRA_DEVICE_ID).orEmpty(),
            sessionId = intent.getStringExtra(WidgetIds.EXTRA_SESSION_ID),
            featureId = intent.getStringExtra(WidgetIds.EXTRA_FEATURE_ID).orEmpty(),
            nonce = intent.getStringExtra(WidgetIds.EXTRA_NONCE).orEmpty(),
        )
        val pendingResult = goAsync()
        scope.launch {
            try {
                WidgetDependencies.get().dispatch(request)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Build an immutable PendingIntent for a widget action.
         *
         * Request codes combine widget id, device, and feature so actions
         * for different instances cannot collide.
         */
        fun actionPendingIntent(
            context: Context,
            state: WidgetState,
            action: WidgetAction,
        ): PendingIntent {
            val intent = Intent(context, OmniBudsWidgetProvider::class.java).apply {
                this.action = WidgetIds.ACTION_WIDGET_TOGGLE
                putExtra(WidgetIds.EXTRA_WIDGET_ID, state.widgetId)
                putExtra(WidgetIds.EXTRA_DEVICE_ID, state.deviceId)
                putExtra(WidgetIds.EXTRA_SESSION_ID, state.sessionId)
                putExtra(WidgetIds.EXTRA_FEATURE_ID, action.featureId)
                putExtra(WidgetIds.EXTRA_NONCE, UUID.randomUUID().toString())
            }
            val requestCode = (state.widgetId.toString() + action.featureId +
                (state.deviceId ?: "")).hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

/**
 * Process-scoped widget dependencies. The host installs these at startup.
 */
object WidgetDependencies {
    private var installed: Installed? = null

    data class Installed(
        val coordinator: WidgetCoordinator,
        val dispatch: suspend (WidgetActionRequest) -> Unit,
        val refresh: suspend (widgetId: Int) -> Unit,
    )

    fun install(
        coordinator: WidgetCoordinator,
        dispatch: suspend (WidgetActionRequest) -> Unit,
        refresh: suspend (widgetId: Int) -> Unit,
    ) {
        installed = Installed(coordinator, dispatch, refresh)
    }

    fun get(): Installed =
        installed ?: throw IllegalStateException("WidgetDependencies not installed")

    fun resetForTests() {
        installed = null
    }
}

/**
 * Stable widget identifiers.
 */
object WidgetIds {
    /** Action for widget hardware toggles. */
    const val ACTION_WIDGET_TOGGLE = "com.omnibuds.android.widget.TOGGLE_FEATURE"

    /** Intent extra keys. */
    const val EXTRA_WIDGET_ID = "widget_id"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_FEATURE_ID = "feature_id"
    const val EXTRA_NONCE = "nonce"
}

/**
 * Renders [WidgetState] into RemoteViews.
 *
 * Layout IDs come from the widget layout XML (see res/layout/). The
 * renderer is a thin binding layer; mapping logic stays in
 * WidgetStateMapper.
 */
class AndroidWidgetRenderer(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetRenderer {

    override fun render(state: WidgetState) {
        val views = RemoteViews(context.packageName, layoutFor(state))
        views.setTextViewText(R.id.widget_status, state.statusText)
        views.setContentDescription(R.id.widget_status, state.statusText)

        val batteryText = batteryText(state.battery)
        views.setTextViewText(R.id.widget_battery, batteryText)
        views.setViewVisibility(
            R.id.widget_battery,
            if (batteryText.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE,
        )

        // Up to two action buttons; hide unused ones.
        val actionIds = intArrayOf(R.id.widget_action_1, R.id.widget_action_2)
        actionIds.forEachIndexed { index, viewId ->
            val action = state.actions.getOrNull(index)
            if (action != null) {
                views.setTextViewText(viewId, action.label)
                views.setContentDescription(viewId, action.contentDescription)
                views.setViewVisibility(viewId, android.view.View.VISIBLE)
                views.setOnClickPendingIntent(
                    viewId,
                    OmniBudsWidgetProvider.actionPendingIntent(context, state, action),
                )
            } else {
                views.setViewVisibility(viewId, android.view.View.GONE)
            }
        }

        appWidgetManager.updateAppWidget(state.widgetId, views)
    }

    override fun remove(widgetId: Int) {
        // The launcher removes the views; nothing to clean up here.
    }

    private fun layoutFor(state: WidgetState): Int = when (state.kind) {
        WidgetKind.CONTROLS -> R.layout.widget_standard
        else -> R.layout.widget_compact
    }

    private fun batteryText(battery: WidgetBattery?): String {
        if (battery == null) return ""
        val parts = mutableListOf<String>()
        battery.leftPercent?.let { parts.add("L $it%") }
        battery.rightPercent?.let { parts.add("R $it%") }
        battery.casePercent?.let { parts.add("Case $it%") }
        return parts.joinToString(" · ")
    }
}
