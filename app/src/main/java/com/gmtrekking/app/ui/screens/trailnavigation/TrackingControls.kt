package com.gmtrekking.app.ui.screens.trailnavigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.tracking.RecordingSnapshot
import com.gmtrekking.app.data.tracking.RecordingStatus
import kotlin.math.roundToInt

/**
 * Controlli per la registrazione del cammino effettuato (Avvia/Pausa/
 * Riprendi/Termina), sempre visibili in MainMapScreen indipendentemente
 * dal fatto che sia caricato un percorso GPX come guida: la registrazione
 * è un'azione indipendente (vedi TrekRecorder e il punto 1 dei "Richiesta
 * utente da sviluppare" in docs/PIANO_SVILUPPO.md).
 */
@Composable
fun TrackingControls(
    snapshot: RecordingSnapshot,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onAddNote: (String) -> Unit,
    onAddPhotoClick: () -> Unit,
) {
    // Evita di interrompere per errore una registrazione in corso: "Termina"
    // chiede sempre conferma prima di fermare davvero (richiesto esplicitamente,
    // agosto 2026 — prima il tap fermava la registrazione all'istante).
    var showStopConfirmation by remember { mutableStateOf(false) }
    // Dialog per scrivere una nota puntuale (punto 4 del piano); la foto
    // invece delega subito alla fotocamera di sistema tramite onAddPhotoClick
    // (il vero scatto/salvataggio è gestito in MainMapScreen, che ha accesso
    // al launcher della fotocamera e alla posizione corrente).
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        when (snapshot.status) {
            RecordingStatus.IDLE -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.tracking_start), style = MaterialTheme.typography.labelLarge)
                }
            }

            RecordingStatus.RECORDING, RecordingStatus.PAUSED -> {
                val isPaused = snapshot.status == RecordingStatus.PAUSED

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            if (isPaused) R.string.tracking_status_paused else R.string.tracking_status_recording,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatTrackingDistance(snapshot.distanceMeters),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tracking_elapsed_time, formatTrackingDuration(snapshot.movingTimeMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Passi: null finché il sensore non ha ancora dato una prima
                // lettura (o non è disponibile/permesso negato) — vedi TrekRecorder.
                snapshot.stepCount?.let { steps ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tracking_steps, steps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (snapshot.possiblyForgottenPause) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tracking_forgotten_pause_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (snapshot.waypointCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tracking_waypoint_count, snapshot.waypointCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Note puntuali e foto (punti 3 e 4 del piano): disponibili sia
                // durante la registrazione attiva sia in pausa — es. ci si può
                // fermare per scattare una foto al panorama senza dover prima
                // "terminare" nulla.
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { noteText = ""; showNoteDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.tracking_add_note))
                    }
                    OutlinedButton(onClick = onAddPhotoClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.tracking_add_photo))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isPaused) {
                        OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tracking_resume))
                        }
                    } else {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tracking_pause))
                        }
                    }
                    Button(onClick = { showStopConfirmation = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tracking_stop))
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.tracking_add_note_title)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.tracking_add_note_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoteDialog = false
                        onAddNote(noteText)
                    },
                    enabled = noteText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.tracking_add_note_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text(stringResource(R.string.tracking_stop_confirm_cancel))
                }
            },
        )
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(stringResource(R.string.tracking_stop_confirm_title)) },
            text = { Text(stringResource(R.string.tracking_stop_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    onStop()
                }) {
                    Text(stringResource(R.string.tracking_stop_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text(stringResource(R.string.tracking_stop_confirm_cancel))
                }
            },
        )
    }
}

/** Condivisa con MainMapScreen.kt per il messaggio di conferma a fine registrazione. */
fun formatTrackingDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.roundToInt()} m"

/** Condivisa con MainMapScreen.kt per il messaggio di conferma a fine registrazione. */
fun formatTrackingDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "%dh %02dmin".format(hours, minutes) else "%d min".format(minutes)
}
