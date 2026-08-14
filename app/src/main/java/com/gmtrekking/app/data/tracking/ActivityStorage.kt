package com.gmtrekking.app.data.tracking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistenza dei percorsi conclusi: un file JSON nella cartella privata
 * dell'app (kotlinx.serialization, già una dipendenza del progetto — vedi
 * data/poi/PoiRepository.kt per lo stesso pattern usato con Overpass API).
 *
 * Scelta deliberata rispetto a Room: per il volume di dati atteso (l'elenco
 * dei percorsi di un singolo utente, non migliaia di righe con query
 * complesse) un file JSON è sufficiente e molto più semplice da introdurre
 * ora, senza aggiungere un nuovo plugin Gradle (Room richiede KSP o kapt per
 * l'elaborazione delle annotazioni, non ancora presenti in questo progetto).
 * Room resta pianificato per la Fase 2 (docs/PIANO_SVILUPPO.md) se in
 * futuro servissero query più ricche o il volume crescesse molto.
 */
object ActivityStorage {
    private const val FILE_NAME = "trek_activities.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadAll(context: Context): List<CompletedActivity> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<CompletedActivity>>(file.readText())
        }.getOrDefault(emptyList())
    }

    /** Aggiunge [activity] all'elenco già salvato (legge, accoda, riscrive). */
    suspend fun save(context: Context, activity: CompletedActivity) = withContext(Dispatchers.IO) {
        val current = loadAll(context)
        val updated = current + activity
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(updated))
    }

    /**
     * Sostituisce il percorso con lo stesso id di [activity] con la versione
     * aggiornata (usato per modificare la nota generale dal dettaglio — vedi
     * ActivityDetailScreen.kt). Se l'id non esiste più non fa nulla.
     */
    suspend fun update(context: Context, activity: CompletedActivity) = withContext(Dispatchers.IO) {
        val current = loadAll(context)
        val updated = current.map { if (it.id == activity.id) activity else it }
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(updated))
    }

    /**
     * Elimina il percorso con id [activityId] dall'elenco salvato — richiesto
     * esplicitamente (agosto 2026, punto 2 dei "Richiesta utente da
     * sviluppare" in docs/PIANO_SVILUPPO.md). Non elimina le eventuali foto
     * associate: la chiamante (ActivityDetailScreen) se ne occupa a parte,
     * per poter mostrare un unico avviso di conferma prima di entrambe le
     * cancellazioni.
     */
    suspend fun delete(context: Context, activityId: String) = withContext(Dispatchers.IO) {
        val current = loadAll(context)
        val updated = current.filterNot { it.id == activityId }
        File(context.filesDir, FILE_NAME).writeText(json.encodeToString(updated))
    }
}
