package com.omnibuds.android.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.android.presentation.theme.AndroidTouchTargets

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick = { viewModel.generateLocalExport() },
                modifier = Modifier.defaultMinSize(minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
            ) {
                Text("Export Log")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Platform Telemetry", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Android API Level: ${state.androidApiLevel}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Bluetooth State: ${state.bluetoothStateDescription}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Permissions: ${state.permissionSummary}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Redaction Active: ${state.isRedactionActive}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Recent Diagnostic Events (${state.recentEvents.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (state.recentEvents.isEmpty()) {
            Text(text = "No diagnostic events logged yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.recentEvents) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = event.category, style = MaterialTheme.typography.labelMedium)
                                Text(text = event.correlationId, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = event.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
