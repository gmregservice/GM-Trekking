package com.gmtrekking.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.emergency.EmergencyContact
import com.gmtrekking.app.data.emergency.EmergencyContactsStorage
import com.gmtrekking.app.data.settings.AppSettingsStorage
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * "Impostazioni": pagina generale dell'app, pensata fin dall'inizio come
 * contenitore per qualunque opzione configurabile presente e futura,
 * raggruppata in sezioni con un titolo chiaro (richiesto esplicitamente,
 * agosto 2026) — non solo un elenco di contatti come nella versione
 * precedente di questa schermata (`EmergencySettingsScreen`, rinominata e
 * ampliata qui).
 *
 * Sezioni attuali:
 *  - **Contatti di emergenza**: stesso contenuto di prima, invariato nella
 *    logica (vedi `data/emergency/EmergencyContactsStorage.kt`).
 *  - **Navigazione**: chiave API di OpenRouteService, in preparazione per
 *    l'instradamento reale (vedi `data/settings/AppSettingsStorage.kt`) —
 *    per ora questa sezione salva solo la chiave, non fa ancora nessuna
 *    chiamata al servizio (deciso esplicitamente di procedere per gradi,
 *    agosto 2026: prima la pagina di configurazione, poi in un passo
 *    successivo l'instradamento vero).
 *
 * Raggiungibile sia da una nuova icona dedicata nella mappa principale
 * (coerente con "raggruppare tutte le opzioni... presenti e future" — deve
 * essere facile da trovare, non nascosta dentro un'altra schermata) sia
 * dalla schermata "Emergenza" (scorciatoia già presente, ora punta qui).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Sezione "Contatti di emergenza" ---
    var contacts by remember { mutableStateOf<List<EmergencyContact>?>(null) }
    // Un solo stato per il dialog di aggiunta/modifica (invece di due variabili
    // separate come nella versione precedente di questa schermata), per
    // escludere di partenza qualunque possibilità che le due restino
    // disallineate — l'utente aveva segnalato di non riuscire più ad
    // aggiungere un secondo contatto dopo il primo.
    var contactDialogState by remember { mutableStateOf<ContactDialogState>(ContactDialogState.Hidden) }

    suspend fun reloadContacts() {
        contacts = EmergencyContactsStorage.loadAll(context)
    }
    LaunchedEffect(Unit) { reloadContacts() }

    fun saveContact(label: String, phoneNumber: String, editingId: String?) {
        val current = contacts.orEmpty()
        val updated = if (editingId != null) {
            current.map { if (it.id == editingId) it.copy(label = label, phoneNumber = phoneNumber) else it }
        } else {
            // UUID invece di System.currentTimeMillis(): esclude del tutto la
            // possibilità (per quanto remota) di due contatti con lo stesso id.
            current + EmergencyContact(id = UUID.randomUUID().toString(), label = label, phoneNumber = phoneNumber)
        }
        contactDialogState = ContactDialogState.Hidden
        coroutineScope.launch {
            EmergencyContactsStorage.saveAll(context, updated)
            reloadContacts()
        }
    }

    // --- Sezione "Navigazione" (chiave API OpenRouteService) ---
    var apiKeyInput by remember { mutableStateOf("") }
    var savedApiKey by remember { mutableStateOf<String?>(null) }

    suspend fun reloadApiKey() {
        val stored = AppSettingsStorage.getOrsApiKey(context)
        savedApiKey = stored
        apiKeyInput = stored.orEmpty()
    }
    LaunchedEffect(Unit) { reloadApiKey() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_to_map))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsSectionTitle(
                title = stringResource(R.string.settings_section_contacts_title),
                description = stringResource(R.string.settings_section_contacts_description),
            )

            when (val list = contacts) {
                null -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    if (list.isEmpty()) {
                        Text(
                            text = stringResource(R.string.emergency_settings_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        list.forEach { contact ->
                            ContactListItem(
                                contact = contact,
                                onClick = { contactDialogState = ContactDialogState.Editing(contact) },
                                onDeleteClick = {
                                    coroutineScope.launch {
                                        EmergencyContactsStorage.saveAll(context, list.filterNot { it.id == contact.id })
                                        reloadContacts()
                                    }
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { contactDialogState = ContactDialogState.Adding },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.emergency_settings_add))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            SettingsSectionTitle(
                title = stringResource(R.string.settings_section_navigation_title),
                description = stringResource(R.string.settings_section_navigation_description),
            )
            Text(
                text = stringResource(R.string.settings_navigation_why_register),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_navigation_signup_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ORS_SIGNUP_URL)))
                    },
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.settings_navigation_api_key_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        AppSettingsStorage.setOrsApiKey(context, apiKeyInput)
                        coroutineScope.launch { reloadApiKey() }
                    },
                    enabled = apiKeyInput.isNotBlank(),
                ) { Text(stringResource(R.string.settings_navigation_api_key_save)) }

                if (savedApiKey != null) {
                    TextButton(
                        onClick = {
                            AppSettingsStorage.clearOrsApiKey(context)
                            apiKeyInput = ""
                            coroutineScope.launch { reloadApiKey() }
                        },
                    ) { Text(stringResource(R.string.settings_navigation_api_key_remove)) }
                }
            }
            Text(
                text = if (savedApiKey != null) {
                    stringResource(R.string.settings_navigation_api_key_status_configured)
                } else {
                    stringResource(R.string.settings_navigation_api_key_status_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_navigation_active_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_navigation_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val dialogState = contactDialogState
        if (dialogState != ContactDialogState.Hidden) {
            ContactEditDialog(
                initial = (dialogState as? ContactDialogState.Editing)?.contact,
                onDismiss = { contactDialogState = ContactDialogState.Hidden },
                onSave = { label, phoneNumber ->
                    val editingId = (dialogState as? ContactDialogState.Editing)?.contact?.id
                    saveContact(label, phoneNumber, editingId)
                },
            )
        }
    }
}

private const val ORS_SIGNUP_URL = "https://openrouteservice.org/sign-up/"

/** Stato del dialog di aggiunta/modifica contatto: vedi commento su contactDialogState sopra. */
private sealed class ContactDialogState {
    object Hidden : ContactDialogState()
    object Adding : ContactDialogState()
    data class Editing(val contact: EmergencyContact) : ContactDialogState()
}

@Composable
private fun SettingsSectionTitle(title: String, description: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ContactListItem(contact: EmergencyContact, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.label, style = MaterialTheme.typography.titleMedium)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.emergency_settings_delete))
            }
        }
    }
}

@Composable
private fun ContactEditDialog(
    initial: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (label: String, phoneNumber: String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var phoneNumber by remember { mutableStateOf(initial?.phoneNumber.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial != null) R.string.emergency_settings_edit_title else R.string.emergency_settings_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.emergency_settings_label_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text(stringResource(R.string.emergency_settings_phone_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim(), phoneNumber.trim()) },
                enabled = label.isNotBlank() && phoneNumber.isNotBlank(),
            ) { Text(stringResource(R.string.emergency_settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracking_stop_confirm_cancel)) }
        },
    )
}
