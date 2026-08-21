package com.gmtrekking.app.data.trails

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistenza dei sentieri scaricati per un'area fissa (vedi FixedTrailAreas.kt
 * e SavedTrail.kt): un file JSON nella cartella privata dell'app, stesso
 * pattern già usato per i percorsi conclusi (data/tracking/ActivityStorage.kt)
 * — stesso ragionamento su Room vs file JSON, vedi quel file.
 */
object SavedTrailsStorage {
    private const val FILE_NAME = "saved_trails.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadAll(context: Context): List<SavedTrail> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<SavedTrail>>(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun loadForArea(context: Context, areaName: String): List<SavedTrail> =
        loadAll(context).filter { it.areaName == areaName }

    /**
     * Sostituisce tutte le tracce già salvate per [areaName] con [trails]:
     * un nuovo download della stessa area rimpiazza quello precedente invece
     * di accumulare doppioni (i sentieri OpenStreetMap possono cambiare nel
     * tempo, ha senso che un nuovo download rifletta lo stato più recente).
     */
    suspend fun replaceArea(context: Context, areaName: String, trails: List<SavedTrail>) = withContext(Dispatchers.IO) {
        val current = loadAll(context).filterNot { it.areaName == areaName }
        val updated = current + trails
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(updated))
    }

    suspend fun delete(context: Context, trailId: Long, areaName: String) = withContext(Dispatchers.IO) {
        val current = loadAll(context)
        val updated = current.filterNot { it.id == trailId && it.areaName == areaName }
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(updated))
    }
}
