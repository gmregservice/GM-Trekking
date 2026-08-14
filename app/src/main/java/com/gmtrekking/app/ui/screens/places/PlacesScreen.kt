package com.gmtrekking.app.ui.screens.places

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.gmtrekking.app.data.poi.Poi
import com.gmtrekking.app.location.LocationPermissions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place

/**
 * Schermata "Luoghi utili": elenco di ristoranti, bar, hotel ecc. intorno alla
 * posizione corrente, filtrabile per categoria.
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
                        items(uiState.visiblePois, key = { it.osmId }) { poi ->
                            PoiListItem(poi)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiListItem(poi: Poi) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(poi.name, style = MaterialTheme.typography.titleLarge)
            }
            poi.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            poi.openingHours?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
