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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.theme.AndroidThemeMode
import com.omnibuds.android.presentation.theme.AndroidTouchTargets

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Theme Preference", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AndroidThemeMode.values().forEach { mode ->
                OutlinedButton(
                    onClick = { viewModel.setThemeMode(mode) },
                    modifier = Modifier.defaultMinSize(minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
                ) {
                    Text(mode.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "High Contrast Mode", style = MaterialTheme.typography.titleMedium)
                Text(text = "Enhanced visibility with WCAG AAA contrast ratios", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.isHighContrast,
                onCheckedChange = { viewModel.setHighContrast(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Redact Sensitive Identifiers", style = MaterialTheme.typography.titleMedium)
                Text(text = "Mask MAC addresses in logs and diagnostics", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.redactSensitiveIdentifiers,
                onCheckedChange = { viewModel.setRedactSensitiveIdentifiers(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Quick Settings & Widget Auto-Refresh", style = MaterialTheme.typography.titleMedium)
                Text(text = "Synchronize hardware state with tile and widget", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.widgetAutoRefresh,
                onCheckedChange = { viewModel.setWidgetAutoRefresh(it) },
            )
        }
    }
}
