package com.gmtrekking.app.data.tracking

import kotlinx.serialization.Serializable

/**
 * Un punto campionato durante la registrazione di un cammino (diverso da
 * `TrackPoint` in data/gpx: quello viene dalla lettura di un file GPX già
 * pronto, questo da un fix GPS in tempo reale mentre si cammina — per
 * questo porta anche un timestamp, che TrackPoint non ha).
 */
@Serializable
data class TrackedPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val timestampMillis: Long,
)

/**
 * Un punto di interesse segnalato dall'utente durante la registrazione: una
 * nota testuale e/o una foto, associate alla posizione in cui sono state
 * create (vedi punti 3 e 4 dei "Richiesta utente da sviluppare" in
 * docs/PIANO_SVILUPPO.md). Modello unico per entrambi, invece di due sistemi
 * paralleli — scelta in linea con Gaia GPS (vedi "Spunti dalla ricerca
 * competitiva" nel piano): una foto è concettualmente una nota puntuale con
 * un'immagine allegata invece che (solo) testo. [note] e [photoFileName]
 * possono essere presenti insieme (una foto commentata) o uno solo dei due.
 */
@Serializable
data class ActivityWaypoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val note: String? = null,
    // Solo il nome del file, non un percorso assoluto: la foto vive nella
    // cartella privata dell'app (vedi PhotoStorage.kt), il nome basta per
    // ritrovarla — più portabile se in futuro cambiasse la struttura delle
    // cartelle interne.
    val photoFileName: String? = null,
)

/**
 * Un percorso concluso e salvato: registrato camminando, sia seguendo un
 * tracciato GPX caricato come guida sia senza (vedi TrekRecorder — punto 1
 * dei "Richiesta utente da sviluppare" in docs/PIANO_SVILUPPO.md).
 */
@Serializable
data class CompletedActivity(
    val id: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceMeters: Double,
    val totalTimeMillis: Long,
    val movingTimeMillis: Long,
    val elevationGainMeters: Double,
    val points: List<TrackedPoint>,
    // Null se il sensore contapassi non è disponibile sul dispositivo o il
    // permesso ACTIVITY_RECOGNITION non è stato concesso (vedi TrekRecorder).
    // Default a null per restare compatibile con i file salvati dalle
    // versioni precedenti, che non avevano questo campo.
    val stepCount: Int? = null,
    // Nota generale unica del percorso (meteo, compagni, condizioni — punto 4
    // del piano), modificabile in un secondo momento dal dettaglio in
    // Cronologia, non solo al momento della registrazione.
    val generalNote: String? = null,
    // Note puntuali e foto geolocalizzate raccolte durante la registrazione
    // (punti 3 e 4 del piano). Lista vuota, non null, per semplicità di
    // lettura lato UI (niente controlli null in più nelle schermate).
    val waypoints: List<ActivityWaypoint> = emptyList(),
)
