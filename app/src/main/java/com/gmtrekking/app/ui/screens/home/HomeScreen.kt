package com.gmtrekking.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R

/**
 * Schermata iniziale: due azioni principali, senza altro rumore visivo.
 * Coerente col principio guida "interfaccia essenziale" del piano di sviluppo.
 */
@Composable
fun HomeScreen(
    onImportGpxClick: () -> Unit,
    onPlacesNearbyClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onImportGpxClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.home_import_gpx), style = MaterialTheme.typography.labelLarge)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onPlacesNearbyClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.home_places_nearby), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
