package com.gmtrekking.app.ui.screens.importtrail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

/**
 * Schermata di import: l'utente sceglie un file .gpx dal dispositivo.
 * Se il parsing va a buon fine, il tracciato viene salvato in
 * [CurrentTrackHolder] e si passa alla schermata di navigazione;
 * altrimenti si mostra un messaggio d'errore comprensibile (non lo stack
 * trace tecnico del parser).
 */
@Composable
fun ImportTrailScreen(
    onTrackLoaded: () -> Unit,
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMessage = null
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val track = GpxParser.parse(stream)
                CurrentTrackHolder.track.value = track
                onTrackLoaded()
            } ?: run {
                errorMessage = "Non riesco ad aprire il file scelto. Riprova."
            }
        } catch (t: Throwable) {
            errorMessage = "Questo file non sembra un tracciato GPX valido. Prova con un altro file."
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_import_gpx)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.nav_import_prompt),
                style = MaterialTheme.typography.bodyLarge,
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.home_import_gpx), style = MaterialTheme.typography.labelLarge)
            }

            errorMessage?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
