package com.gmtrekking.app.data.emergency

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistenza dei contatti d'emergenza configurati dall'utente: stesso
 * pattern già usato per i percorsi registrati (vedi
 * data/tracking/ActivityStorage.kt) — un file JSON nella cartella privata
 * dell'app, niente Room, niente cloud. L'elenco è tipicamente cortissimo
 * (pochi numeri), quindi non serve niente di più granulare di un
 * "leggi tutto / riscrivi tutto" ad ogni modifica dalla schermata Impostazioni.
 */
object EmergencyContactsStorage {
    private const val FILE_NAME = "emergency_contacts.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadAll(context: Context): List<EmergencyContact> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<EmergencyContact>>(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun saveAll(context: Context, contacts: List<EmergencyContact>) = withContext(Dispatchers.IO) {
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(contacts))
    }
}
