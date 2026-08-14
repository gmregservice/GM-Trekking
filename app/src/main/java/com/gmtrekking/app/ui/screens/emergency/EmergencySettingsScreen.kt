package com.gmtrekking.app.ui.screens.emergency

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R
import com.gmtrekking.app.data.emergency.EmergencyContact
import com.gmtrekking.app.data.emergency.EmergencyContactsStorage
import kotlinx.coroutines.launch

/**
 * "Impostazioni": elenco dei contatti da avvisare in caso di emergenza (punto
 * 7 del piano). Pensata fin da subito come contenitore generale per qualunque
 * dato da preimpostare in futuro, non solo i numeri di emergenza — per ora
 * contiene solo questo.
 *
 * Cancellazione senza dialog di conferma (a differenza di Cronologia): qui
 * si tratta solo di un'etichetta e un numero di telefono, facile da
 * reinserire per errore — non un intero percorso registrato con foto e note.
 */
@Composable
fun EmergencySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<EmergencyContact>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }

    suspend fun reload() {
        contacts = EmergencyContactsStorage.loadAll(context)
    }

    LaunchedEffect(Unit) { reload() }

    fun saveContact(label: String, phoneNumber: String) {
        val current = contacts.orEmpty()
        val editing = editingContact
        val updated = if (editing != null) {
            current.map { if (it.id == editing.id) it.copy(label = label, phoneNumber = phoneNumber) else it }
        } else {
            current + EmergencyContact(id = System.currentTimeMillis().toString(), label = label, phoneNumber = phoneNumber)
        }
        coroutineScope.launch {
            EmergencyContactsStorage.saveAll(context, updated)
            reload()
        }
        showAddDialog = false
        editingContact = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_to_map))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingContact = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.emergency_settings_add))
            }
        },
    ) { padding ->
        val list = contacts

        when {
            list == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.emergency_settings_empty), style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(list, key = { it.id }) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { editingContact = contact; showAddDialog = true },
                        onDeleteClick = {
                            coroutineScope.launch {
                                EmergencyContactsStorage.saveAll(context, list.filterNot { it.id == contact.id })
                                reload()
                            }
                        },
                    )
                }
            }
        }

        if (showAddDialog) {
            ContactEditDialog(
                initial = editingContact,
                onDismiss = { showAddDialog = false; editingContact = null },
                onSave = { label, phoneNumber -> saveContact(label, phoneNumber) },
            )
        }
    }
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
