package com.omnibuds.android.ui.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.about.AboutScreenState

@Composable
fun AboutScreen(
    state: AboutScreenState = AboutScreenState(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(text = "About ${state.appName}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Version: ${state.appVersion}", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Architecture", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = state.architectureSummary, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Privacy Pledge", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = state.privacyPledge, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Image Credits", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sony WI-C100 product photograph: KKPCW (Kyu3), licensed CC BY-SA 4.0, via Wikimedia Commons. No other product photographs are bundled - OmniBuds does not redistribute manufacturer imagery without a license. Devices without a licensed photo show a drawn category symbol, or your own photo of your device if you add one.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Honest Platform Limitations", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        state.honestLimitations.forEach { limitation ->
            Text(text = "• $limitation", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
