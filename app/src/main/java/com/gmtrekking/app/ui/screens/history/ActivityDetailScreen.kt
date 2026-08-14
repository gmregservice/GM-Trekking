package com.gmtrekking.app.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.tracking.ActivityStorage
import com.gmtrekking.app.data.tracking.CompletedActivity
import com.gmtrekking.app.ui.screens.trailnavigation.TrekMapView
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDistance
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDuration
import kotlin.math.roundToInt

/**
 * Dettaglio di un percorso salvato: il tracciato disegnato sulla mappa (non
 * solo i numeri), riusando TrekMapView.kt in modalità "sola lettura"
 * (showCurrentPosition = false, nessuna freccia di navigazione né avviso
 * fuori percorso) — punto 2 dei "Richiesta utente da sviluppare" in
 * docs/PIANO_SVILUPPO.md. [activityId] arriva come argomento di navigazione
 * (AppNavHost.kt); il percorso viene ritrovato rileggendo ActivityStorage,
 * senza passare l'intero oggetto fra le schermate.
 */
@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var activity by remember { mutableStateOf<CompletedActivity?>(null) }
    var notFound by remember { mutableStateOf(false) }

    LaunchedEffect(activityId) {
        val found = ActivityStorage.loadAll(context).firstOrNull { it.id == activityId }
        activity = found
        notFound = found == null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back_to_map),
                        )
                    }
                },
            )
        }
    ) { padding ->
        val current = activity

        when {
            notFound -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.history_detail_not_found), style = MaterialTheme.typography.bodyLarge) }

            current == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> {
                // CompletedActivity.points (TrackedPoint, con timestamp) va
                // convertito in GpxTrack/TrackPoint (senza timestamp) per
                // riusare lo stesso layer di disegno tracciato di TrekMapView.
                val gpxTrack = remember(current) {
                    GpxTrack(
                        name = "activity_${current.id}",
                        points = current.points.map { TrackPoint(it.latitude, it.longitude, it.elevationMeters) },
                    )
                }

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        TrekMapView(
                            track = gpxTrack,
                            currentLat = current.points.first().latitude,
                            currentLon = current.points.first().longitude,
                            autoZoomIn = false,
                            showCurrentPosition = false,
                        )
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.tracking_saved_summary,
                                formatTrackingDistance(current.distanceMeters),
                                formatTrackingDuration(current.movingTimeMillis),
                                current.elevationGainMeters.roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = current.stepCount?.let { stringResource(R.string.history_item_steps, it) }
                                ?: stringResource(R.string.history_item_steps_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
