package com.gmtrekking.app.ui.screens.trailnavigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.CurrentTrackHolder
import com.gmtrekking.app.data.navigation.NavigationEngine
import com.gmtrekking.app.location.LocationPermissions
import com.gmtrekking.app.location.LocationTrackingService
import kotlin.math.roundToInt

/**
 * Schermata di navigazione: il cuore dell'app. Mostra la mappa con il
 * tracciato e la posizione corrente, la freccia direzionale grande, la
 * distanza dal prossimo punto e l'avviso di fuori percorso quando serve.
 *
 * Segue il principio guida del piano di sviluppo: freccia grande, zoom
 * automatico nei punti critici, avviso di deviazione azionabile — pensato
 * per un utente non esperto, non per un escursionista che sa già leggere
 * una mappa topografica.
 */
@Composable
fun TrailNavigationScreen() {
    val context = LocalContext.current
    val track by CurrentTrackHolder.track.collectAsState()
    val currentLocation by LocationTrackingService.locationUpdates.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            startTrackingService(context)
        }
    }

    LaunchedEffect(Unit) {
        if (LocationPermissions.hasForegroundLocationPermission(context)) {
            startTrackingService(context)
        } else {
            permissionLauncher.launch(LocationPermissions.foregroundLocationPermissions())
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_start_navigation)) }) }
    ) { padding ->
        val loadedTrack = track
        val location = currentLocation

        when {
            loadedTrack == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Nessun percorso caricato. Torna indietro e scegli un file GPX.") }

            location == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("In attesa del segnale GPS…", style = MaterialTheme.typography.bodyLarge) }

            else -> {
                val engine = remember(loadedTrack) { NavigationEngine(loadedTrack) }
                val navState = remember(location.latitude, location.longitude) {
                    engine.update(location.latitude, location.longitude)
                }

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                        TrekMapView(
                            track = loadedTrack,
                            currentLat = location.latitude,
                            currentLon = location.longitude,
                            autoZoomIn = navState.shouldZoomIn,
                        )
                    }

                    if (navState.isOffRoute) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error)
                                .padding(16.dp),
                        ) {
                            Text(
                                stringResource(R.string.nav_off_route_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onError,
                            )
                            Text(
                                stringResource(R.string.nav_off_route_instruction),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DirectionArrow(
                            bearingDegrees = navState.bearingToNextPointDegrees,
                            isOffRoute = navState.isOffRoute,
                        )

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(
                                R.string.nav_distance_to_next_meters,
                                navState.distanceToNextPointMeters.roundToInt(),
                            ),
                            style = MaterialTheme.typography.headlineLarge,
                        )

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(
                                R.string.nav_distance_remaining,
                                formatDistance(navState.distanceRemainingMeters),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun startTrackingService(context: android.content.Context) {
    val intent = Intent(context, LocationTrackingService::class.java)
    androidx.core.content.ContextCompat.startForegroundService(context, intent)
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.roundToInt()} m"
