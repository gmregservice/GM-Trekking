package com.gmtrekking.app.ui.screens.trailnavigation

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.CurrentTrackHolder
import com.gmtrekking.app.data.gpx.GpxParser
import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.navigation.NavigationEngine
import com.gmtrekking.app.data.navigation.PoiNavigationHolder
import com.gmtrekking.app.data.tracking.ActivityStorage
import com.gmtrekking.app.data.tracking.PhotoStorage
import com.gmtrekking.app.data.tracking.TrekRecorder
import com.gmtrekking.app.location.LocationPermissions
import com.gmtrekking.app.location.LocationTrackingService
import kotlinx.coroutines.launch
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
    onHistoryClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onNearbyTrailsClick: () -> Unit,
) {
    val context = LocalContext.current
    val loadedTrack by CurrentTrackHolder.track.collectAsState()
    // Navigazione verso un luogo utile selezionato da PlacesScreen (punto 6 del
    // piano): indipendente dal percorso GPX caricato, non lo sovrascrive né lo
    // perde — vedi PoiNavigationHolder.
    val poiTarget by PoiNavigationHolder.target.collectAsState()
    val currentLocation by LocationTrackingService.locationUpdates.collectAsState()
    var gpxError by remember { mutableStateOf<String?>(null) }
    // Contatore incrementato dal pulsante "Ricentra": TrekMapView osserva i
    // cambi di valore per riportare la camera sulla posizione corrente (vedi
    // il commento su recenterRequest in TrekMapView.kt).
    var recenterRequest by remember { mutableStateOf(0) }
    // Aggiornato dopo la richiesta permessi, per (ri)registrare il sensore
    // contapassi non appena concesso (vedi DisposableEffect più sotto).
    var hasActivityRecognition by remember {
        mutableStateOf(LocationPermissions.hasActivityRecognitionPermission(context))
    }

    // Ricentraggio automatico quando l'app torna in primo piano, tipicamente
    // sbloccando lo schermo (richiesto esplicitamente, agosto 2026): mentre lo
    // schermo è spento la mappa non viene ridisegnata, quindi al risveglio
    // sembra "ferma" sull'ultima posizione visibile anche se il puntino si è
    // nel frattempo spostato fuori dall'inquadratura — prima serviva un tap
    // manuale sul pulsante "Ricentra" per accorgersene. ON_RESUME (non
    // ON_START) è l'evento giusto: su Android, spegnere lo schermo con l'app
    // già in primo piano genera ON_PAUSE, e riaccenderlo/sbloccarlo genera di
    // nuovo ON_RESUME, anche senza che l'app sia mai passata in background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recenterRequest++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
        hasActivityRecognition = LocationPermissions.hasActivityRecognitionPermission(context)
    }

    // La posizione corrente serve fin dall'apertura dell'app (per mostrarla
    // sulla mappa), non solo quando è caricato un percorso: il servizio di
    // localizzazione parte appena il permesso è concesso, indipendentemente
    // dalla presenza di un tracciato.
    //
    // BUG REALE CORRETTO (agosto 2026): il permesso per il contapassi veniva
    // richiesto SOLO dentro il ramo "else" (posizione non ancora concessa),
    // quindi chi aveva già dato il permesso di posizione PRIMA che questa
    // funzione esistesse (v1.12) non lo vedeva mai richiesto — il contapassi
    // restava sempre "non disponibile" senza che l'utente potesse saperne il
    // motivo. Corretto controllando/richiedendo i due permessi in modo
    // indipendente: la posizione avvia comunque subito il servizio se già
    // concessa, il contapassi viene richiesto a parte ogni volta che manca,
    // a prescindere dallo stato del permesso di posizione.
    LaunchedEffect(Unit) {
        if (LocationPermissions.hasForegroundLocationPermission(context)) {
            startTrackingService(context)
        }
        val toRequest = mutableListOf<String>()
        if (!LocationPermissions.hasForegroundLocationPermission(context)) {
            toRequest += LocationPermissions.foregroundLocationPermissions()
        }
        if (!hasActivityRecognition) {
            LocationPermissions.activityRecognitionPermissionIfNeeded()?.let { toRequest += it }
        }
        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    // Sensore contapassi (Sensor.TYPE_STEP_COUNTER): letture inoltrate a
    // TrekRecorder, che le usa solo mentre una registrazione è in corso (vedi
    // il punto 1/2 dei "Richiesta utente da sviluppare" in
    // docs/PIANO_SVILUPPO.md). Assente su alcuni dispositivi o senza permesso
    // concesso: in quel caso semplicemente non si registra nulla, senza
    // bloccare il resto dell'app.
    DisposableEffect(hasActivityRecognition) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                TrekRecorder.onStepCountSensorUpdate(event.values[0].toInt())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (hasActivityRecognition && sensorManager != null && stepSensor != null) {
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.home_history))
                    }
                    IconButton(onClick = onPlacesNearbyClick) {
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.home_places_nearby))
                    }
                    IconButton(onClick = onNearbyTrailsClick) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.home_nearby_trails))
                    }
                    IconButton(onClick = onEmergencyClick) {
                        Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.home_emergency))
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

        // Percorso usato per calcolare la navigazione mostrata in questa
        // schermata: quello verso un luogo utile ha la priorità (punto 6 del
        // piano), altrimenti quello GPX caricato come guida. Il primo punto è
        // sempre la posizione corrente stessa (aggiornata ad ogni fix GPS): il
        // punto più vicino del "percorso" è quindi sempre quello, la distanza
        // dal tracciato resta sempre 0 e l'avviso "fuori percorso" (pensato
        // per un vero tracciato da seguire) non scatta mai per questa modalità
        // — un effetto collaterale voluto della scelta di riusare
        // NavigationEngine invece di scriverne uno dedicato per questo caso.
        val navigationTrack = remember(poiTarget, loadedTrack, location.latitude, location.longitude) {
            poiTarget?.let { target ->
                GpxTrack(
                    name = target.name,
                    points = listOf(
                        TrackPoint(location.latitude, location.longitude),
                        TrackPoint(target.latitude, target.longitude),
                    ),
                )
            } ?: loadedTrack
        }
        val engine = remember(navigationTrack) { navigationTrack?.let { NavigationEngine(it) } }
        val navState = remember(engine, location.latitude, location.longitude) {
            engine?.update(location.latitude, location.longitude)
        }

        // Registrazione del cammino effettuato: indipendente dal percorso GPX
        // caricato come guida (funziona anche senza — vedi TrackingControls).
        // Ogni nuovo fix GPS viene inoltrato a TrekRecorder, che internamente
        // ignora l'aggiornamento se non è in corso una registrazione.
        val recordingState by TrekRecorder.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        var activitySavedMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(location) {
            TrekRecorder.onLocationUpdate(location)
        }

        // Foto geolocalizzate (punto 3 del piano): delega lo scatto vero e
        // proprio all'app Fotocamera di sistema tramite intent, invece di
        // implementare una UI di scatto in-app — stessa scelta di semplicità
        // già fatta altrove (Intent.ACTION_DIAL per i luoghi utili). Il nome
        // del file creato da PhotoStorage.newPhotoTarget viene tenuto da
        // parte finché la fotocamera non conferma lo scatto (callback del
        // launcher), per sapere a quale ActivityWaypoint associarlo.
        var pendingPhotoFileName by remember { mutableStateOf<String?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val fileName = pendingPhotoFileName
            pendingPhotoFileName = null
            if (fileName == null) return@rememberLauncherForActivityResult
            if (success) {
                TrekRecorder.addPhotoWaypoint(location, fileName)
            } else {
                // Utente ha annullato lo scatto: elimina il file vuoto creato in anticipo.
                PhotoStorage.delete(context, fileName)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Box(
                modifier = if (navigationTrack == null) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().height(280.dp)
                }
            ) {
                TrekMapView(
                    // Il layer del tracciato/il "fit" automatico della camera restano
                    // legati solo al vero percorso GPX caricato (loadedTrack), MAI al
                    // percorso sintetico verso un luogo utile: quest'ultimo cambia ad
                    // ogni fix GPS (il primo punto è sempre la posizione corrente), e
                    // ri-inquadrare la camera ad ogni aggiornamento sarebbe un fastidioso
                    // "salto" continuo della mappa durante la navigazione verso un luogo.
                    track = loadedTrack,
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    autoZoomIn = navState?.shouldZoomIn ?: false,
                    recenterRequest = recenterRequest,
                    waypoints = poiTarget?.let { listOf(it.latitude to it.longitude) } ?: emptyList(),
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

            TrackingControls(
                snapshot = recordingState,
                onStart = {
                    activitySavedMessage = null
                    TrekRecorder.start(location)
                },
                onPause = { TrekRecorder.pause() },
                onResume = { TrekRecorder.resume() },
                onAddNote = { text -> TrekRecorder.addNoteWaypoint(location, text) },
                onAddPhotoClick = {
                    val (fileName, uri) = PhotoStorage.newPhotoTarget(context)
                    pendingPhotoFileName = fileName
                    cameraLauncher.launch(uri)
                },
                onStop = {
                    val completed = TrekRecorder.stop()
                    if (completed == null) {
                        activitySavedMessage = context.getString(R.string.tracking_discarded_too_short)
                    } else {
                        coroutineScope.launch {
                            ActivityStorage.save(context, completed)
                            activitySavedMessage = context.getString(
                                R.string.tracking_saved_summary,
                                formatTrackingDistance(completed.distanceMeters),
                                formatTrackingDuration(completed.movingTimeMillis),
                                completed.elevationGainMeters.roundToInt(),
                            )
                        }
                    }
                },
            )

            activitySavedMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
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

                poiTarget?.let { target ->
                    Text(
                        text = stringResource(R.string.poi_nav_title, target.name),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    )
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
                        text = if (poiTarget != null) {
                            stringResource(R.string.poi_nav_distance_remaining, formatDistance(navState.distanceRemainingMeters))
                        } else {
                            stringResource(R.string.nav_distance_remaining, formatDistance(navState.distanceRemainingMeters))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                if (poiTarget != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        TextButton(
                            onClick = { PoiNavigationHolder.target.value = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.poi_nav_stop))
                        }
                    }
                } else {
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
