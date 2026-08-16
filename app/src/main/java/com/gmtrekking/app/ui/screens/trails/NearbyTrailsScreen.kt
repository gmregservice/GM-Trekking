package com.gmtrekking.app.ui.screens.trails

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.CurrentTrackHolder
import com.gmtrekking.app.data.gpx.GpxWriter
import com.gmtrekking.app.data.trails.NearbyTrail
import com.gmtrekking.app.data.trails.TrailDifficulty
import com.gmtrekking.app.data.trails.estimatedMinutes
import com.gmtrekking.app.data.trails.toGpxTrack
import com.gmtrekking.app.location.LocationPermissions
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDistance
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Scoperta di sentieri nelle vicinanze (punto 5 del piano): elenco di
 * relazioni `route=hiking` di OpenStreetMap trovate entro qualche km dalla
 * posizione corrente, con lunghezza, tempo stimato e difficoltà quando nota.
 * Da qui si può usare direttamente un sentiero come percorso guida (sostituisce
 * l'eventuale GPX già caricato, stessa scelta già fatta per "Cambia percorso"
 * in MainMapScreen.kt: nessuna conferma, la sostituzione è un'azione diretta
 * e reversibile) oppure scaricarlo come vero file `.gpx` (GpxWriter.kt).
 */
@SuppressLint("MissingPermission") // il permesso viene verificato/richiesto prima di ogni chiamata a location
@Composable
fun NearbyTrailsScreen(
    onBack: () -> Unit,
    viewModel: NearbyTrailsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var locationError by remember { mutableStateOf<String?>(null) }
    // Sentiero per cui è stato appena premuto "Scarica GPX": tenuto da parte
    // finché il launcher del selettore file non restituisce l'Uri scelto
    // dall'utente (stesso pattern già usato per le foto geolocalizzate, vedi
    // MainMapScreen.kt/pendingPhotoFileName).
    var pendingExportTrail by remember { mutableStateOf<NearbyTrail?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val trail = pendingExportTrail
        pendingExportTrail = null
        if (uri != null && trail != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(GpxWriter.write(trail.toGpxTrack()).toByteArray())
                    }
                }
            }
        }
    }

    fun fetchLocationAndLoad() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.loadNearby(location.latitude, location.longitude)
                } else {
                    locationError = "Non riesco a determinare la tua posizione. Assicurati che il GPS sia attivo."
                }
            }
            .addOnFailureListener {
                locationError = "Non riesco a determinare la tua posizione. Assicurati che il GPS sia attivo."
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) fetchLocationAndLoad() else locationError = "Serve il permesso di localizzazione per cercare i sentieri vicino a te."
    }

    LaunchedEffect(Unit) {
        if (LocationPermissions.hasForegroundLocationPermission(context)) {
            fetchLocationAndLoad()
        } else {
            permissionLauncher.launch(LocationPermissions.foregroundLocationPermissions())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_trails_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_to_map))
                    }
                },
            )
        }
    ) { padding ->
        when {
            locationError != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(locationError!!, style = MaterialTheme.typography.bodyLarge) }

            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.errorMessage!!, style = MaterialTheme.typography.bodyLarge) }

            uiState.trails.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.nearby_trails_empty), style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.nearby_trails_estimated_time_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(uiState.trails, key = { it.id }) { trail ->
                    TrailListItem(
                        trail = trail,
                        onUseAsGuideClick = {
                            CurrentTrackHolder.track.value = trail.toGpxTrack()
                            onBack()
                        },
                        onExportClick = {
                            pendingExportTrail = trail
                            exportLauncher.launch("${trail.name}.gpx")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailListItem(
    trail: NearbyTrail,
    onUseAsGuideClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(trail.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.nearby_trails_distance_from_you, formatTrackingDistance(trail.distanceFromUserMeters)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.nearby_trails_length_label, formatTrackingDistance(trail.lengthMeters)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = stringResource(
                    R.string.nearby_trails_estimated_time_label,
                    formatTrackingDuration(trail.estimatedMinutes() * 60_000L),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = difficultyLabel(trail.difficulty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onUseAsGuideClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nearby_trails_use_as_guide))
                }
                OutlinedButton(onClick = onExportClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nearby_trails_export_gpx))
                }
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: TrailDifficulty?): String = when (difficulty) {
    null -> stringResource(R.string.nearby_trails_difficulty_unknown)
    TrailDifficulty.HIKING -> stringResource(R.string.nearby_trails_difficulty_hiking)
    TrailDifficulty.MOUNTAIN_HIKING -> stringResource(R.string.nearby_trails_difficulty_mountain_hiking)
    TrailDifficulty.DEMANDING_MOUNTAIN_HIKING -> stringResource(R.string.nearby_trails_difficulty_demanding_mountain_hiking)
    TrailDifficulty.ALPINE_HIKING -> stringResource(R.string.nearby_trails_difficulty_alpine_hiking)
    TrailDifficulty.DEMANDING_ALPINE_HIKING -> stringResource(R.string.nearby_trails_difficulty_demanding_alpine_hiking)
    TrailDifficulty.DIFFICULT_ALPINE_HIKING -> stringResource(R.string.nearby_trails_difficulty_difficult_alpine_hiking)
}
