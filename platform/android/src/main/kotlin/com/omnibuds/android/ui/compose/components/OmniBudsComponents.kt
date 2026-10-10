package com.omnibuds.android.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.battery.BatteryPresentationModel
import com.omnibuds.android.presentation.theme.AndroidTouchTargets
import com.omnibuds.android.presentation.workspace.ControlExecutionStatus
import com.omnibuds.android.presentation.workspace.FeatureCapabilityKind
import com.omnibuds.android.presentation.workspace.HardwareControlModel

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun BatteryCard(
    battery: BatteryPresentationModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = battery.talkBackDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Battery Status",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (battery.isStale) {
                    StatusBadge(text = "Outdated", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (!battery.hasAnyData) {
                Text(
                    text = battery.unavailableReason ?: "No battery telemetry reported by device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    battery.components.forEach { comp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = comp.componentName, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comp.displayString,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureControlCard(
    control: HardwareControlModel,
    onSelectMode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = control.talkBackDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = control.displayName, style = MaterialTheme.typography.titleMedium)
                    control.explanation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val badgeColor = when (control.capabilityKind) {
                    FeatureCapabilityKind.PERSISTENCE_VERIFIED -> Color(0xFF16A34A)
                    FeatureCapabilityKind.PERSISTENT, FeatureCapabilityKind.VOLATILE -> MaterialTheme.colorScheme.primary
                    FeatureCapabilityKind.READ_ONLY -> Color(0xFFD97706)
                    FeatureCapabilityKind.UNSUPPORTED -> MaterialTheme.colorScheme.error
                    FeatureCapabilityKind.UNKNOWN -> MaterialTheme.colorScheme.outline
                }
                StatusBadge(text = control.capabilityKind.name, color = badgeColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (control.executionStatus == ControlExecutionStatus.PENDING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Applying setting...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (control.availableModes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    control.availableModes.forEach { mode ->
                        val isSelected = control.currentValue == mode
                        OutlinedButton(
                            onClick = { onSelectMode(mode) },
                            enabled = control.isActionable,
                            modifier = Modifier
                                .defaultMinSize(minWidth = AndroidTouchTargets.minTouchTargetDp.dp, minHeight = AndroidTouchTargets.minTouchTargetDp.dp)
                                .semantics { role = Role.Button },
                        ) {
                            Text(text = mode, style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            control.failureReason?.let { reason ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Operation failed: $reason",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Alert: $message" },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Row {
                onRetry?.let {
                    Button(
                        onClick = it,
                        modifier = Modifier.defaultMinSize(minWidth = AndroidTouchTargets.minTouchTargetDp.dp, minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
                    ) {
                        Text("Retry")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = AndroidTouchTargets.minTouchTargetDp.dp, minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
