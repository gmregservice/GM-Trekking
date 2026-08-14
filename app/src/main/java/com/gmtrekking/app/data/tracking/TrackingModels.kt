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
)
