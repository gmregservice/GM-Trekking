package com.gmtrekking.app.ui.screens.emergency

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gmtrekking.app.R
import com.gmtrekking.app.data.emergency.EmergencyContact
import com.gmtrekking.app.data.emergency.EmergencyContactsStorage
import com.gmtrekking.app.data.emergency.EmergencyCountryLookup
import com.gmtrekking.app.data.emergency.EmergencyMessenger
import com.gmtrekking.app.data.emergency.ReverseGeocoder
import com.gmtrekking.app.location.LocationTrackingService
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Schermata "Emergenza" (punto 7 del piano): contiene SOLO il pulsante che
 * invia davvero la richiesta di aiuto, apposta separata dalla mappa
 * principale per non rischiare di premerlo per errore. Mostra sempre le
 * coordinate GPS correnti e i numeri di emergenza locali (112 + eventuali
 * supplementari, rilevati offline — vedi EmergencyCountryLookup), oltre ai
 * contatti configurati in "Impostazioni" con scorciatoie per chiamarli o
 * scrivere loro su WhatsApp.
 */
@Composable
fun EmergencyScreen(onBack: () -> Unit, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val location by LocationTrackingService.locationUpdates.collectAsState()

    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var address by remember { mutableStateOf<String?>(null) }
    var showSendConfirmation by remember { mutableStateOf(false) }
    var sendResultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        contacts = EmergencyContactsStorage.loadAll(context)
    }

    // Indirizzo/toponimo: solo un'informazione aggiuntiva, spesso assente in
    // montagna senza dati (vedi ReverseGeocoder). Ricalcolato solo quando la
    // posizione cambia di circa 100 m o più, non ad ogni minimo tremolio del
    // GPS, per non interrogare il geocoder continuamente.
    LaunchedEffect(location?.let { (it.latitude * 1000).toInt() to (it.longitude * 1000).toInt() }) {
        val current = location
        address = if (current != null) ReverseGeocoder.addressFor(context, current.latitude, current.longitude) else null
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showSendConfirmation = true
    }

    fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_to_map))
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            val currentLocation = location

            Text(stringResource(R.string.emergency_position_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (currentLocation != null) {
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f", currentLocation.latitude, currentLocation.longitude),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = address ?: stringResource(R.string.emergency_address_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(stringResource(R.string.emergency_position_unavailable), style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.emergency_numbers_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (currentLocation != null) {
                EmergencyCountryLookup.localNumbersFor(currentLocation.latitude, currentLocation.longitude).forEach { entry ->
                    DialableRow(label = entry.label, phoneNumber = entry.number)
                }
            } else {
                DialableRow(label = stringResource(R.string.emergency_number_european_label), phoneNumber = "112")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (hasSendSmsPermission()) {
                        showSendConfirmation = true
                    } else {
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                },
                enabled = contacts.isNotEmpty() && currentLocation != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.emergency_send_button), style = MaterialTheme.typography.labelLarge)
            }
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.emergency_no_contacts_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            sendResultMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.emergency_contacts_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (contacts.isEmpty()) {
                TextButton(onClick = onSettingsClick) {
                    Text(stringResource(R.string.emergency_go_to_settings))
                }
            } else {
                contacts.forEach { contact ->
                    ContactRow(contact = contact, message = {
                        EmergencyMessenger.buildMessage(
                            currentLocation?.latitude ?: 0.0,
                            currentLocation?.longitude ?: 0.0,
                            address,
                        )
                    })
                }
            }
        }

        if (showSendConfirmation) {
            AlertDialog(
                onDismissRequest = { showSendConfirmation = false },
                title = { Text(stringResource(R.string.emergency_send_confirm_title)) },
                text = { Text(stringResource(R.string.emergency_send_confirm_message, contacts.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSendConfirmation = false
                        val loc = location
                        if (loc != null) {
                            val message = EmergencyMessenger.buildMessage(loc.latitude, loc.longitude, address)
                            coroutineScope.launch {
                                EmergencyMessenger.sendSms(context, contacts, message)
                                sendResultMessage = context.getString(R.string.emergency_sent_confirmation, contacts.size)
                            }
                        }
                    }) { Text(stringResource(R.string.emergency_send_confirm_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSendConfirmation = false }) {
                        Text(stringResource(R.string.tracking_stop_confirm_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun DialableRow(label: String, phoneNumber: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(phoneNumber, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, message: () -> String) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.label, style = MaterialTheme.typography.titleMedium)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}")))
            }) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.emergency_call_contact))
            }
            IconButton(onClick = {
                context.startActivity(EmergencyMessenger.whatsAppIntent(contact.phoneNumber, message()))
            }) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.emergency_whatsapp_contact))
            }
        }
    }
}
