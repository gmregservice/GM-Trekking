package com.gmtrekking.app.ui.screens.trailnavigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.CurrentTrackHolder
import com.gmtrekking.app.data.gpx.GpxParser
import com.gmtrekking.app.data.navigation.NavigationEngine
import com.gmtrekking.app.location.LocationPermissions
import com.gmtrekking.app.location.LocationTrackingService
import kotlin.math.roundToInt

/**
 * Schermata principale dell'app: si apre mostrando la posizione corrente sulla
 * mappa, SENZA richiedere di caricare un percorso. Caricare un file GPX è
 * un'azione opzionale, disponibile da qui in ogni momento tramite il pulsante
 * "Carica un percorso GPX". Una volta caricato un percorso, questa stessa
 * schermata mostra anche la navigazione (freccia direzionale, distanza,
 * avviso di fuori percorso — vedi il principio guida nel piano di sviluppo).
 */
@Composable
fun MainMapScreen(
    onPlacesNearbyClick: () -> Unit,
) {
    val context = LocalContext.current
    val track by CurrentTrackHolder.track.collectAsState()
    val currentLocation by LocationTrackingService.locationUpdates.collectAsState()
    var gpxError by remember { mutableStateOf<String?>(null) }
    // Contatore incrementato dal pulsante "Ricentra": TrekMapView osserva i
    // cambi di valore per riportare la camera sulla posizione corrente (vedi
    // il commento su recenterRequest in TrekMapView.kt).
    var recenterRequest by remember { mutableStateOf(0) }

    val gpxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        gpxError = null
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                CurrentTrackHolder.track.value = GpxParser.parse(stream)
            } ?: run {
                gpxError = context.getString(R.string.map_gpx_open_error)
            }
        } catch (_: Throwable) {
            gpxError = context.getString(R.string.map_gpx_load_error)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            startTrackingService(context)
        }
    }

    // La posizione corrente serve fin dall'apertura dell'app (per mostrarla
    // sulla mappa), non solo quando è caricato un percorso: il servizio di
    // localizzazione parte appena il permesso è concesso, indipendentemente
    // dalla presenza di un tracciato.
    LaunchedEffect(Unit) {
        if (LocationPermissions.hasForegroundLocationPermission(context)) {
            startTrackingService(context)
        } else {
            permissionLauncher.launch(LocationPermissions.foregroundLocationPermissions())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onPlacesNearbyClick) {
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.home_places_nearby))
                    }
                },
            )
        }
    ) { padding ->
        val location = currentLocation

        if (location == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.map_waiting_for_gps), style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@Scaffold
        }

        val engine = remember(track) { track?.let { NavigationEngine(it) } }
        val navState = remember(engine, location.latitude, location.longitude) {
            engine?.update(location.latitude, location.longitude)
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Box(
                modifier = if (track == null) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().height(280.dp)
                }
            ) {
                TrekMapView(
                    track = track,
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    autoZoomIn = navState?.shouldZoomIn ?: false,
                    recenterRequest = recenterRequest,
                )

                // "Ricentra": scorrendo la mappa per vedere cosa c'è più avanti
                // lungo il percorso, altrimenti non ci sarebbe modo di tornare
                // sulla propria posizione senza cercarla manualmente.
                FloatingActionButton(
                    onClick = { recenterRequest++ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_recenter))
                }
            }

            gpxError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            if (navState != null) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.nav_distance_to_next_meters,
                            navState.distanceToNextPointMeters.roundToInt(),
                        ),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.nav_distance_remaining,
                            formatDistance(navState.distanceRemainingMeters),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { gpxPicker.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.map_change_gpx))
                    }
                    TextButton(onClick = { CurrentTrackHolder.track.value = null }) {
                        Text(stringResource(R.string.map_remove_track))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = { gpxPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.map_load_gpx), style = MaterialTheme.typography.labelLarge)
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
