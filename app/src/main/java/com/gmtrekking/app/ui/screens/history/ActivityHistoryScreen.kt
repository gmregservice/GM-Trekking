package com.gmtrekking.app.ui.screens.history

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.tracking.ActivityStorage
import com.gmtrekking.app.data.tracking.CompletedActivity
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDistance
import com.gmtrekking.app.ui.screens.trailnavigation.formatTrackingDuration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Elenco dei percorsi salvati dalla registrazione del cammino (vedi
 * data/tracking/TrekRecorder.kt e ActivityStorage.kt) — punto 2 dei
 * "Richiesta utente da sviluppare" in docs/PIANO_SVILUPPO.md. Selezionando
 * un percorso si apre il dettaglio con il tracciato sulla mappa
 * (ActivityDetailScreen).
 */
@Composable
fun ActivityHistoryScreen(
    onBack: () -> Unit,
    onActivityClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // null = ancora in caricamento, lista vuota = caricato ma nessun percorso salvato.
    var activities by remember { mutableStateOf<List<CompletedActivity>?>(null) }
    // Id del percorso per cui è aperta la richiesta di conferma eliminazione,
    // null se nessuna (richiesto esplicitamente, agosto 2026 — stesso
    // principio già usato per "Termina": un'azione distruttiva non deve
    // essere a un solo tap per errore).
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        activities = ActivityStorage.loadAll(context).sortedByDescending { it.startTimeMillis }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
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
        val list = activities

        when {
            list == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(list, key = { it.id }) { activity ->
                    ActivityListItem(
                        activity = activity,
                        onClick = { onActivityClick(activity.id) },
                        onDeleteClick = { pendingDeleteId = activity.id },
                    )
                }
            }
        }

        pendingDeleteId?.let { activityId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringResource(R.string.history_delete_confirm_title)) },
                text = { Text(stringResource(R.string.history_delete_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteId = null
                        coroutineScope.launch {
                            ActivityStorage.delete(context, activityId)
                            reload()
                        }
                    }) {
                        Text(stringResource(R.string.history_delete_confirm_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text(stringResource(R.string.history_delete_confirm_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ActivityListItem(activity: CompletedActivity, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatActivityDate(activity.startTimeMillis), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.tracking_saved_summary,
                        formatTrackingDistance(activity.distanceMeters),
                        formatTrackingDuration(activity.movingTimeMillis),
                        activity.elevationGainMeters.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = activity.stepCount?.let { stringResource(R.string.history_item_steps, it) }
                        ?: stringResource(R.string.history_item_steps_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.history_delete))
            }
        }
    }
}

private fun formatActivityDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(millis)
