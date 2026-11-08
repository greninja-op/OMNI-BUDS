package com.omnibuds.android.ui.compose.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omnibuds.android.R
import com.omnibuds.android.notification.OmniBudsQuickNotificationManager
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.theme.OmniBrand

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotifPermission = granted
        if (!granted) {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 80.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Page Title Header
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hardware background services, notifications, and audio engine",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA0A5B1),
            )
        }

        // Section 1: Background Service & Keep Alive (Critical for Bluetooth persistence)
        SettingsCard(
            title = "Background Execution",
            icon = OmniIcons.Bolt,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Keep Alive in Background",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isIgnoringBattery) OmniBrand.Accent.copy(alpha = 0.15f) else Color(0xFF382020),
                        ) {
                            Text(
                                text = if (isIgnoringBattery) "UNRESTRICTED" else "RESTRICTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = if (isIgnoringBattery) OmniBrand.Accent else Color(0xFFFF5252),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isIgnoringBattery) {
                            "Background service and BLE socket are exempt from OS battery termination."
                        } else {
                            "MIUI/HyperOS or Android battery manager may kill earbud background controls when swiped away."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A5B1),
                    )
                }

                if (isIgnoringBattery) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OmniBrand.Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Active",
                            tint = OmniBrand.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniBrand.Accent,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            text = "Allow",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }

        // Section 2: Notifications & Quick Shade Controls
        SettingsCard(
            title = "Quick Controls Notification",
            icon = Icons.Filled.Notifications,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Status Bar Quick Panel",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (hasNotifPermission) {
                            "Shows real-time battery levels (L, R, Case) and ANC/Ambient/Transparency toggle buttons."
                        } else {
                            "Notification permission required to display hardware controls in the status shade."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A5B1),
                    )
                }

                if (hasNotifPermission) {
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (!enabled) {
                                OmniBudsQuickNotificationManager.cancelNotification(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = OmniBrand.Accent,
                            uncheckedThumbColor = Color(0xFF6B7280),
                            uncheckedTrackColor = Color(0xFF26282F),
                        ),
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                try {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        border = BorderStroke(1.dp, OmniBrand.Accent),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OmniBrand.Accent,
                        )
                    }
                }
            }
        }

        // Section 3: Universal Audio DSP Status
        SettingsCard(
            title = "Audio Engine & DSP",
            icon = OmniIcons.SoundWaves,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DspFeatureItem(
                    label = "Global Output Pipeline",
                    status = "Audio Session 0 Active",
                    detail = "High-precision 10-band stage & dynamics processing",
                )
                DspFeatureItem(
                    label = "3D Spatial Audio",
                    status = "360° HRTF Binaural",
                    detail = "Virtualizer with acoustic crossfeed space modeling",
                )
                DspFeatureItem(
                    label = "Volume Enhancer",
                    status = "+5.0 dB Limiter",
                    detail = "Safe post-gain loudness boost with zero clipping",
                )
            }
        }

        // Section 4: Privacy & Diagnostics
        SettingsCard(
            title = "Privacy & Diagnostics",
            icon = OmniIcons.Shield,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Redact Hardware MAC Addresses",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Masks Bluetooth identifiers in logs and diagnostics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA0A5B1),
                        )
                    }
                    Switch(
                        checked = state.redactSensitiveIdentifiers,
                        onCheckedChange = { viewModel.setRedactSensitiveIdentifiers(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = OmniBrand.Accent,
                            uncheckedThumbColor = Color(0xFF6B7280),
                            uncheckedTrackColor = Color(0xFF26282F),
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Widget & Tile Auto-Refresh",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Keep home widget in sync with live battery telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA0A5B1),
                        )
                    }
                    Switch(
                        checked = state.widgetAutoRefresh,
                        onCheckedChange = { viewModel.setWidgetAutoRefresh(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = OmniBrand.Accent,
                            uncheckedThumbColor = Color(0xFF6B7280),
                            uncheckedTrackColor = Color(0xFF26282F),
                        ),
                    )
                }
            }
        }

        // Section 5: About OmniBuds
        SettingsCard(
            title = "About",
            icon = Icons.Filled.Info,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "OmniBuds Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "OmniBuds",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        text = "Universal Bluetooth Earbuds Suite • v2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniBrand.Accent,
                    )
                    Text(
                        text = "High-performance TWS control and DSP for Realme, OnePlus, Nothing, Apple, and all Bluetooth audio devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A5B1),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF16171B),
        border = BorderStroke(1.dp, Color(0xFF26282F)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OmniBrand.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = OmniBrand.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    color = Color.White,
                )
            }
            content()
        }
    }
}

@Composable
private fun DspFeatureItem(
    label: String,
    status: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A5B1),
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF22242B),
            border = BorderStroke(1.dp, Color(0xFF32343E)),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                ),
                color = OmniBrand.Accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
