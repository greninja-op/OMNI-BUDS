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

        Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "OmniBuds uses its own dark brand theme — deep black, charcoal, and a fluorescent yellow accent. There is nothing to configure here; it looks the same on every phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
