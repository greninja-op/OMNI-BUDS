package com.omnibuds.android.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.presentation.workspace.WorkspaceTab
import com.omnibuds.android.ui.compose.components.BatteryCard
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.FeatureControlCard
import com.omnibuds.android.ui.compose.components.StatusBadge

@Composable
fun WorkspaceScreen(
    viewModel: DeviceWorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        state.errorBanner?.let { banner ->
            ErrorBanner(
                message = banner,
                onDismiss = { viewModel.dismissError() },
                onRetry = if (state.isRecoverableError) { { viewModel.retryFailedOperations() } } else null,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        state.overview?.let { overview ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = overview.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "ID: ${overview.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(text = overview.connectionState.toString(), color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScrollableTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            WorkspaceTab.values().forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.name) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state.selectedTab) {
            WorkspaceTab.OVERVIEW -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.battery?.let { item { BatteryCard(battery = it) } }
                    items(state.controls.filter { it.isActionable }) { ctrl ->
                        FeatureControlCard(
                            control = ctrl,
                            onSelectMode = { mode -> viewModel.submitFeatureControl(ctrl.featureId, mode) },
                        )
                    }
                }
            }
            WorkspaceTab.CONTROLS -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.controls) { ctrl ->
                        FeatureControlCard(
                            control = ctrl,
                            onSelectMode = { mode -> viewModel.submitFeatureControl(ctrl.featureId, mode) },
                        )
                    }
                }
            }
            WorkspaceTab.BATTERY -> {
                state.battery?.let { BatteryCard(battery = it) }
                    ?: Text(text = "No battery information reported.", style = MaterialTheme.typography.bodyMedium)
            }
            WorkspaceTab.AUDIO -> {
                state.audio?.let { audio ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = audio.talkBackDescription },
                    ) {
                        Text(text = "Audio & Codec Telemetry", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (audio.isCodecObservable && audio.activeCodecName != null) {
                                "Active Codec: ${audio.activeCodecName}"
                            } else {
                                audio.unavailableReason ?: "Active codec is not observable on Android."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            WorkspaceTab.DIAGNOSTICS -> {
                Column {
                    Text(text = "Device Limitations & Telemetry", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.limitations.isEmpty()) {
                        Text(text = "No hardware limitations flagged.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.limitations.forEach { limitation ->
                            Text(text = "• $limitation", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
