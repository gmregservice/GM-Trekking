package com.gmtrekking.app.ui.screens.history

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gmtrekking.app.R
import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.tracking.ActivityStorage
import com.gmtrekking.app.data.tracking.ActivityWaypoint
import com.gmtrekking.app.data.tracking.CompletedActivity
import com.gmtrekking.app.data.tracking.PhotoStorage
import com.gmtrekking.app.ui.screens.trailnavigation.TrekMapView
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDistance
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dettaglio di un percorso salvato: il tracciato disegnato sulla mappa (non
 * solo i numeri), riusando TrekMapView.kt in modalità "sola lettura"
 * (showCurrentPosition = false, nessuna freccia di navigazione né avviso
 * fuori percorso) — punto 2 dei "Richiesta utente da sviluppare" in
 * docs/PIANO_SVILUPPO.md. [activityId] arriva come argomento di navigazione
 * (AppNavHost.kt); il percorso viene ritrovato rileggendo ActivityStorage,
 * senza passare l'intero oggetto fra le schermate.
 *
 * Include anche (punti 2, 3 e 4 del piano): eliminazione del percorso, nota
 * generale modificabile, ed elenco delle note/foto puntuali raccolte durante
 * la registrazione, con le loro posizioni indicate sulla mappa.
 */
@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activity by remember { mutableStateOf<CompletedActivity?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = activity != null) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.history_delete))
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

            // Visualizzatore a schermo intero: tocco su una miniatura in
            // WaypointCard — la miniatura in Cronologia (256px) era troppo
            // piccola per vedere bene una foto scattata durante il cammino,
            // richiesto esplicitamente (agosto 2026), insieme al pulsante
            // "Condividi" per esportarla altrove (stesso meccanismo già usato
            // per l'export GPX/WhatsApp: FileProvider + Intent).
            else -> {
                var fullScreenPhotoFileName by remember { mutableStateOf<String?>(null) }
                ActivityDetailContent(
                    context = context,
                    activity = current,
                    padding = padding,
                    onSaveGeneralNote = { updated ->
                        activity = updated
                        coroutineScope.launch { ActivityStorage.update(context, updated) }
                    },
                    onPhotoClick = { fileName -> fullScreenPhotoFileName = fileName },
                )
                fullScreenPhotoFileName?.let { fileName ->
                    FullScreenPhotoDialog(
                        context = context,
                        fileName = fileName,
                        onDismiss = { fullScreenPhotoFileName = null },
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.history_delete_confirm_title)) },
                text = { Text(stringResource(R.string.history_delete_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        val toDelete = current
                        coroutineScope.launch {
                            if (toDelete != null) {
                                ActivityStorage.delete(context, toDelete.id)
                                toDelete.waypoints.forEach { waypoint ->
                                    waypoint.photoFileName?.let { PhotoStorage.delete(context, it) }
                                }
                            }
                            onBack()
                        }
                    }) {
                        Text(stringResource(R.string.history_delete_confirm_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.history_delete_confirm_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ActivityDetailContent(
    context: Context,
    activity: CompletedActivity,
    padding: PaddingValues,
    onSaveGeneralNote: (CompletedActivity) -> Unit,
    onPhotoClick: (fileName: String) -> Unit,
) {
    // CompletedActivity.points (TrackedPoint, con timestamp) va convertito in
    // GpxTrack/TrackPoint (senza timestamp) per riusare lo stesso layer di
    // disegno tracciato di TrekMapView.
    val gpxTrack = remember(activity) {
        GpxTrack(
            name = "activity_${activity.id}",
            points = activity.points.map { TrackPoint(it.latitude, it.longitude, it.elevationMeters) },
        )
    }
    val waypointPositions = remember(activity) {
        activity.waypoints.map { it.latitude to it.longitude }
    }
    var generalNoteText by remember(activity.id) { mutableStateOf(activity.generalNote ?: "") }

    // Column scorrevole invece di LazyColumn: la mappa (una AndroidView pesante)
    // come primo elemento di una lista "lazy" rischierebbe di essere distrutta e
    // ricreata se l'utente scorre oltre e poi torna su — un pattern noto e
    // sconsigliato in Compose. Il numero di note/foto per un singolo percorso è
    // comunque piccolo, quindi non serve la virtualizzazione di LazyColumn.
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            TrekMapView(
                track = gpxTrack,
                currentLat = activity.points.first().latitude,
                currentLon = activity.points.first().longitude,
                autoZoomIn = false,
                showCurrentPosition = false,
                waypoints = waypointPositions,
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.tracking_saved_summary,
                    formatTrackingDistance(activity.distanceMeters),
                    formatTrackingDuration(activity.movingTimeMillis),
                    activity.elevationGainMeters.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = activity.stepCount?.let { stringResource(R.string.history_item_steps, it) }
                    ?: stringResource(R.string.history_item_steps_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.history_general_note_label), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = generalNoteText,
                onValueChange = { generalNoteText = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.history_general_note_placeholder)) },
            )
            Button(
                onClick = { onSaveGeneralNote(activity.copy(generalNote = generalNoteText.ifBlank { null })) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.history_general_note_save))
            }
        }

        if (activity.waypoints.isNotEmpty()) {
            Text(
                text = stringResource(R.string.history_waypoints_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            activity.waypoints.forEach { waypoint ->
                WaypointCard(context = context, waypoint = waypoint, onPhotoClick = onPhotoClick)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WaypointCard(
    context: Context,
    waypoint: ActivityWaypoint,
    onPhotoClick: (fileName: String) -> Unit,
) {
    var thumbnail by remember(waypoint.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(waypoint.id) {
        val fileName = waypoint.photoFileName
        if (fileName != null) {
            thumbnail = withContext(Dispatchers.IO) { PhotoStorage.loadThumbnail(context, fileName) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(formatWaypointTime(waypoint.timestampMillis), style = MaterialTheme.typography.bodySmall)

            thumbnail?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.history_waypoint_photo_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                        .clickable {
                            waypoint.photoFileName?.let { fileName -> onPhotoClick(fileName) }
                        },
                )
            }

            waypoint.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun formatWaypointTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(millis)

/**
 * Visualizzatore a schermo intero per una foto (tocco sulla miniatura in
 * WaypointCard): Dialog invece di una nuova schermata di navigazione, più
 * semplice per un contenuto "usa e getta" che non ha bisogno di un proprio
 * indirizzo/back-stack. Carica la foto in una risoluzione più alta della
 * miniatura (PhotoStorage.loadFullScreen), non la piena risoluzione della
 * fotocamera, per lo stesso motivo già spiegato su quella funzione.
 */
@Composable
private fun FullScreenPhotoDialog(
    context: Context,
    fileName: String,
    onDismiss: () -> Unit,
) {
    var fullBitmap by remember(fileName) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(fileName) { mutableStateOf(true) }

    LaunchedEffect(fileName) {
        fullBitmap = withContext(Dispatchers.IO) { PhotoStorage.loadFullScreen(context, fileName) }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                fullBitmap != null -> Image(
                    bitmap = fullBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.history_waypoint_photo_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                isLoading -> CircularProgressIndicator(color = Color.White)
                else -> Text(
                    text = stringResource(R.string.history_photo_load_error),
                    color = Color.White,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_photo_close), tint = Color.White)
                }
                IconButton(
                    onClick = {
                        val uri = PhotoStorage.shareableUri(context, fileName) ?: return@IconButton
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.history_photo_share), tint = Color.White)
                }
            }
        }
    }
}
