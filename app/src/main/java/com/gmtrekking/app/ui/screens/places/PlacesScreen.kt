package com.gmtrekking.app.ui.screens.places

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.CurrentTrackHolder
import com.gmtrekking.app.data.navigation.NavigationEngine
import com.gmtrekking.app.data.navigation.PoiNavigationHolder
import com.gmtrekking.app.data.poi.Poi
import com.gmtrekking.app.location.LocationPermissions
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDistance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Place

/**
 * Schermata "Luoghi utili": elenco di ristoranti, bar, hotel ecc. intorno alla
 * posizione corrente, filtrabile per categoria. Punto 6 del piano: mostra
 * anche la distanza dalla posizione corrente e il telefono cliccabile, e
 * toccando un luogo avvia la navigazione verso di lì (riusando
 * NavigationEngine/TrekMapView già scritti per i tracciati GPX, con un
 * "percorso" di un solo punto — vedi PoiNavigationHolder e MainMapScreen.kt).
 *
 * A differenza della schermata di navigazione, qui basta una posizione "una
 * tantum" (non serve il servizio in foreground): la richiediamo con
 * getCurrentLocation al primo ingresso nella schermata.
 */
@SuppressLint("MissingPermission") // il permesso viene verificato/richiesto prima di ogni chiamata a location
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var locationError by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    // Luogo per cui è stata chiesta la navigazione mentre un percorso GPX è già
    // caricato: mostra un avviso prima di metterlo temporaneamente in pausa
    // (richiesto esplicitamente, agosto 2026 — vedi punto 6 del piano). Null =
    // nessun avviso in sospeso.
    var pendingNavigationPoi by remember { mutableStateOf<Poi?>(null) }

    fun fetchLocationAndLoad() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    viewModel.loadNearby(location.latitude, location.longitude)
                } else {
                    locationError = "Non riesco a determinare la tua posizione. Assicurati che il GPS sia attivo."
                }
            }
            .addOnFailureListener {
                locationError = "Non riesco a determinare la tua posizione. Assicurati che il GPS sia attivo."
            }
    }

    fun startNavigation(poi: Poi) {
        PoiNavigationHolder.target.value = PoiNavigationHolder.Target(poi.name, poi.latitude, poi.longitude)
        onBack()
    }

    fun onPoiClick(poi: Poi) {
        if (CurrentTrackHolder.track.value != null) {
            pendingNavigationPoi = poi
        } else {
            startNavigation(poi)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) fetchLocationAndLoad() else locationError = "Serve il permesso di localizzazione per mostrare i luoghi vicino a te."
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
                title = { Text(stringResource(R.string.home_places_nearby)) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(placeCategoryFilterOrder) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category.displayLabel()) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            // Contenitore con weight(1f): occupa lo spazio rimasto sotto la riga di
            // filtri, invece di fillMaxSize() (che misurerebbe rispetto all'intera
            // Column e spingerebbe il contenuto fuori dallo schermo di quel tanto).
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    locationError != null -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(locationError!!, style = MaterialTheme.typography.bodyLarge) }

                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    uiState.errorMessage != null -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(uiState.errorMessage!!, style = MaterialTheme.typography.bodyLarge) }

                    uiState.visiblePois.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Nessun luogo trovato in questa categoria nelle vicinanze.", style = MaterialTheme.typography.bodyLarge) }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.places_navigate_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        items(uiState.visiblePois, key = { it.osmId }) { poi ->
                            PoiListItem(
                                poi = poi,
                                distanceMeters = currentLocation?.let { loc ->
                                    NavigationEngine.distanceMeters(loc.latitude, loc.longitude, poi.latitude, poi.longitude)
                                },
                                onNavigateClick = { onPoiClick(poi) },
                                onCallClick = { phone ->
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                },
                            )
                        }
                    }
                }
            }
        }

        pendingNavigationPoi?.let { poi ->
            AlertDialog(
                onDismissRequest = { pendingNavigationPoi = null },
                title = { Text(stringResource(R.string.poi_nav_leaving_route_title)) },
                text = { Text(stringResource(R.string.poi_nav_leaving_route_message, poi.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingNavigationPoi = null
                        startNavigation(poi)
                    }) { Text(stringResource(R.string.poi_nav_leaving_route_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingNavigationPoi = null }) {
                        Text(stringResource(R.string.poi_nav_leaving_route_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun PoiListItem(
    poi: Poi,
    distanceMeters: Double?,
    onNavigateClick: () -> Unit,
    onCallClick: (String) -> Unit,
) {
    Card(onClick = onNavigateClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(poi.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                distanceMeters?.let {
                    Text(formatTrackingDistance(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            poi.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            poi.openingHours?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            poi.phone?.let { phone ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable { onCallClick(phone) },
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = stringResource(R.string.places_call_phone),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
