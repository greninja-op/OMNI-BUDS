package com.omnibuds.android.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.view.View
import com.omnibuds.android.R
import com.omnibuds.android.hardware.engine.UniversalTwsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ongoing custom system notification bar controller for earbud controls.
 *
 * Designed with the signature OmniBuds dark & fluorescent yellow (#E5FF36) aesthetic:
 * - Direct noise control toggles (ANC, Ambient Mode, Transparency Mode)
 * - Individual Left, Right, Case battery charge indicators
 * - Reconnect and Disconnect controls
 */
object OmniBudsQuickNotificationManager {
    const val CHANNEL_ID = "omnibuds_quick_controls_v3"
    const val NOTIFICATION_ID = 4001

    const val ACTION_NOISE_CONTROL = "com.omnibuds.action.SET_NOISE_CONTROL"
    const val ACTION_RECONNECT = "com.omnibuds.action.RECONNECT"
    const val ACTION_DISCONNECT = "com.omnibuds.action.DISCONNECT"
    const val ACTION_TEST_NOTIFICATION = "com.omnibuds.action.TEST_NOTIFICATION"

    const val EXTRA_ADDRESS = "device_address"
    const val EXTRA_MODE = "target_mode"

    var onNoiseControlChangeRequested: ((address: String, mode: String) -> Unit)? = null
    var onReconnectRequested: ((address: String) -> Unit)? = null
    var onDisconnectRequested: ((address: String) -> Unit)? = null
    var testModeActive: Boolean = false

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniBuds Earbud Controls",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Ongoing earbud controls (ANC, Ambient, Transparency, Battery)"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        deviceAddress: String,
        deviceName: String?,
        currentMode: String,
        batteryPercent: Int? = null,
        leftBattery: Int? = null,
        rightBattery: Int? = null,
        caseBattery: Int? = null,
    ): Notification {
        ensureChannel(context)

        val title = deviceName?.takeIf { it.isNotBlank() } ?: "OmniBuds"
        val isAnc = currentMode.equals("ANC", ignoreCase = true)
        val isTrans = currentMode.equals("TRANSPARENCY", ignoreCase = true)
        val isAmbient = currentMode.equals("OFF", ignoreCase = true) || currentMode.isBlank()

        val modeStr = when {
            currentMode.equals("Disconnected", ignoreCase = true) -> "Disconnected"
            isAnc -> "ANC Active"
            isTrans -> "Transparency"
            else -> "Ambient"
        }

        // Tapping notification opens main app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else null

        val pAnc = createActionPendingIntent(context, deviceAddress, "ANC", 101)
        val pAmbient = createActionPendingIntent(context, deviceAddress, "OFF", 102)
        val pTrans = createActionPendingIntent(context, deviceAddress, "TRANSPARENCY", 103)
        val pReconnect = createCommandPendingIntent(context, ACTION_RECONNECT, deviceAddress, 201)
        val pDisconnect = createCommandPendingIntent(context, ACTION_DISCONNECT, deviceAddress, 202)

        // Collapsed RemoteViews
        val statusSubtitle = formatStatusSubtitle(modeStr)
        val collapsedSubtitle = formatCollapsedStatusSubtitle(
            modeStr = modeStr,
            left = leftBattery,
            right = rightBattery,
            case = caseBattery,
            aggregate = batteryPercent,
        )
        val collapsed = android.widget.RemoteViews(context.packageName, R.layout.notification_custom_collapsed)
        collapsed.setTextViewText(R.id.notif_device_name, title)
        collapsed.setTextViewText(R.id.notif_status_subtitle, collapsedSubtitle)

        bindModeButton(collapsed, R.id.notif_btn_anc, isAnc, pAnc)
        bindModeButton(collapsed, R.id.notif_btn_ambient, isAmbient, pAmbient)
        bindModeButton(collapsed, R.id.notif_btn_transparency, isTrans, pTrans)

        // Expanded RemoteViews
        val expanded = android.widget.RemoteViews(context.packageName, R.layout.notification_custom_expanded)
        expanded.setTextViewText(R.id.notif_device_name, title)
        expanded.setTextViewText(R.id.notif_status_subtitle, statusSubtitle)
        expanded.setOnClickPendingIntent(R.id.notif_btn_reconnect, pReconnect)
        expanded.setOnClickPendingIntent(R.id.notif_btn_disconnect, pDisconnect)

        // Enforce sleek dark Reconnect with high-contrast white text and vibrant red Disconnect styling for dark mode
        expanded.setInt(R.id.notif_btn_reconnect, "setBackgroundResource", R.drawable.btn_reconnect)
        expanded.setTextColor(R.id.notif_btn_reconnect, Color.WHITE)
        expanded.setInt(R.id.notif_btn_disconnect, "setBackgroundResource", R.drawable.btn_disconnect)
        expanded.setTextColor(R.id.notif_btn_disconnect, Color.WHITE)

        // Battery Telemetry: L, R, Case and dots in white, percentage in dynamic green/orange/red
        if (leftBattery != null || rightBattery != null) {
            expanded.setViewVisibility(R.id.notif_battery_row, View.VISIBLE)
            expanded.setTextViewText(R.id.notif_battery_left, formatBatteryTelemetry("L", leftBattery, showLeadingDot = false))
            expanded.setTextViewText(R.id.notif_battery_right, formatBatteryTelemetry("R", rightBattery, showLeadingDot = true))

            if (caseBattery != null && caseBattery > 0) {
                expanded.setViewVisibility(R.id.notif_battery_case, View.VISIBLE)
                expanded.setTextViewText(R.id.notif_battery_case, formatBatteryTelemetry("Case", caseBattery, showLeadingDot = true))
            } else {
                expanded.setViewVisibility(R.id.notif_battery_case, View.GONE)
            }
        } else if (batteryPercent != null && batteryPercent in 1..100) {
            expanded.setViewVisibility(R.id.notif_battery_row, View.VISIBLE)
            expanded.setTextViewText(R.id.notif_battery_left, formatBatteryTelemetry("Battery", batteryPercent, showLeadingDot = false))
            expanded.setViewVisibility(R.id.notif_battery_right, View.GONE)
            expanded.setViewVisibility(R.id.notif_battery_case, View.GONE)
        } else {
            expanded.setViewVisibility(R.id.notif_battery_row, View.GONE)
        }

        bindModeButton(expanded, R.id.notif_btn_anc, isAnc, pAnc)
        bindModeButton(expanded, R.id.notif_btn_ambient, isAmbient, pAmbient)
        bindModeButton(expanded, R.id.notif_btn_transparency, isTrans, pTrans)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(R.drawable.ic_notif_earbuds)
            .setColor(Color.parseColor("#E5FF36"))
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)

        contentPendingIntent?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    fun updateNotification(
        context: Context,
        deviceAddress: String,
        deviceName: String?,
        currentMode: String,
        batteryPercent: Int? = null,
        leftBattery: Int? = null,
        rightBattery: Int? = null,
        caseBattery: Int? = null,
    ) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notif = buildNotification(
            context = context,
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            currentMode = currentMode,
            batteryPercent = batteryPercent,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
        )
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun bindModeButton(
        views: android.widget.RemoteViews,
        viewId: Int,
        isActive: Boolean,
        pendingIntent: PendingIntent,
    ) {
        if (isActive) {
            views.setInt(viewId, "setBackgroundResource", R.drawable.btn_mode_active)
            views.setTextColor(viewId, Color.BLACK)
        } else {
            views.setInt(viewId, "setBackgroundResource", R.drawable.btn_mode_inactive)
            views.setTextColor(viewId, Color.parseColor("#D1D5DB"))
        }
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    private fun getBatteryPercentageColor(percent: Int?): String {
        val p = percent ?: 0
        return when {
            p > 50 -> "#22E55E" // 100–51% green
            p > 20 -> "#FF9800" // 50–21% orange
            else -> "#FF2222"   // 20–0% red
        }
    }

    private fun formatBatteryTelemetry(
        label: String,
        percent: Int?,
        showLeadingDot: Boolean = false,
    ): CharSequence {
        val dotPrefix = if (showLeadingDot) "<font color='#FFFFFF'>•&nbsp;&nbsp;</font>" else ""
        val html = if (percent == null) {
            // A missing component reading is unknown, not "in the case".
            "$dotPrefix<font color='#FFFFFF'>$label: </font><font color='#B0B3C0'>Unknown</font>"
        } else {
            val color = getBatteryPercentageColor(percent)
            "$dotPrefix<font color='#FFFFFF'>$label: </font><font color='$color'><b>$percent%</b></font>"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    private fun formatCollapsedStatusSubtitle(
        modeStr: String,
        left: Int? = null,
        right: Int? = null,
        case: Int? = null,
        aggregate: Int? = null,
    ): CharSequence {
        val base = if (modeStr == "Disconnected") {
            "<font color='#FF5555'>• Disconnected</font>"
        } else {
            "<font color='#E5FF36'>• $modeStr</font>"
        }
        val battery = if (modeStr == "Disconnected") {
            ""
        } else {
            buildString {
                if (left != null) {
                    append(" <font color='${getBatteryPercentageColor(left)}'>L$left%</font>")
                }
                if (right != null) {
                    append(" <font color='${getBatteryPercentageColor(right)}'>R$right%</font>")
                }
                if (case != null && case > 0) {
                    append(" <font color='${getBatteryPercentageColor(case)}'>C$case%</font>")
                }
                if (left == null && right == null && aggregate != null && aggregate in 1..100) {
                    append(" <font color='#FFFFFF'>·</font> <font color='${getBatteryPercentageColor(aggregate)}'>$aggregate%</font>")
                }
            }
        }
        val html = base + battery
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    private fun formatStatusSubtitle(modeStr: String): CharSequence {
        val html = if (modeStr == "Disconnected") {
            "<font color='#FF5555'>Disconnected</font>"
        } else {
            "<font color='#E5FF36'>Connected</font> <font color='#FFFFFF'>&nbsp;•&nbsp;</font> <font color='#E5FF36'>$modeStr</font>"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    fun cancelNotification(context: Context) {
        if (testModeActive) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
    }

    private fun createActionPendingIntent(
        context: Context,
        deviceAddress: String,
        targetMode: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, QuickNotificationActionReceiver::class.java).apply {
            action = ACTION_NOISE_CONTROL
            putExtra(EXTRA_ADDRESS, deviceAddress)
            putExtra(EXTRA_MODE, targetMode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createCommandPendingIntent(
        context: Context,
        intentAction: String,
        deviceAddress: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, QuickNotificationActionReceiver::class.java).apply {
            action = intentAction
            putExtra(EXTRA_ADDRESS, deviceAddress)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

class QuickNotificationActionReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == OmniBudsQuickNotificationManager.ACTION_TEST_NOTIFICATION) {
            OmniBudsQuickNotificationManager.testModeActive = true
            val address = intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_ADDRESS) ?: "74:D7:13:C4:FF:EF"
            val name = intent.getStringExtra("device_name") ?: "OmniBuds"
            val mode = intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_MODE) ?: "ANC"
            OmniBudsQuickNotificationManager.updateNotification(
                context = context,
                deviceAddress = address,
                deviceName = name,
                currentMode = mode,
                batteryPercent = 85,
                leftBattery = 85,
                rightBattery = 80,
                caseBattery = 100,
            )
            return
        }
        val address = intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_ADDRESS) ?: return

        when (intent.action) {
            OmniBudsQuickNotificationManager.ACTION_NOISE_CONTROL -> {
                val requestedMode = intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_MODE) ?: return
                // Same rule as the app buttons: tapping the active ANC/Transparency button
                // switches to Ambient, but only where the buds report their real mode.
                val mode = if (
                    (requestedMode.equals("ANC", ignoreCase = true) ||
                        requestedMode.equals("TRANSPARENCY", ignoreCase = true)) &&
                    UniversalTwsEngine.hasVerifiedModeReadback(address) &&
                    UniversalTwsEngine.confirmedAncMode.value[address].equals(requestedMode, ignoreCase = true)
                ) {
                    "OFF"
                } else {
                    requestedMode
                }
                // 1. Immediate visual feedback in notification panel
                OmniBudsQuickNotificationManager.updateNotification(
                    context = context,
                    deviceAddress = address,
                    deviceName = null,
                    currentMode = mode,
                )
                // 2. Strictly ONE execution path down to hardware engine
                receiverScope.launch {
                    try {
                        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                        val adapter = bm?.adapter
                        val device = adapter?.getRemoteDevice(address)
                        if (device != null) {
                            UniversalTwsEngine.setNoiseControl(device, mode)
                        }
                    } catch (_: Exception) {}
                }
                OmniBudsQuickNotificationManager.onNoiseControlChangeRequested?.invoke(address, mode)
            }
            OmniBudsQuickNotificationManager.ACTION_RECONNECT -> {
                // Forward service command if foreground service running
                try {
                    val serviceIntent = Intent(intent).apply {
                        setClassName(context.packageName, "com.omnibuds.app.service.OmniBudsForegroundService")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (_: Exception) {}

                receiverScope.launch {
                    try {
                        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                        val adapter = bm?.adapter
                        val device = adapter?.getRemoteDevice(address)
                        if (device != null) {
                            UniversalTwsEngine.connect(context.applicationContext, device)
                        }
                    } catch (_: Exception) {}
                }
                OmniBudsQuickNotificationManager.onReconnectRequested?.invoke(address)
            }
            OmniBudsQuickNotificationManager.ACTION_DISCONNECT -> {
                try {
                    val serviceIntent = Intent(intent).apply {
                        setClassName(context.packageName, "com.omnibuds.app.service.OmniBudsForegroundService")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (_: Exception) {}

                receiverScope.launch {
                    try {
                        UniversalTwsEngine.disconnect(address)
                    } catch (_: Exception) {}
                }
                OmniBudsQuickNotificationManager.onDisconnectRequested?.invoke(address)
            }
        }
    }
}
