package com.gmtrekking.app.data.trails

import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import kotlin.math.roundToInt

/**
 * Un sentiero escursionistico trovato nelle vicinanze (punto 5 del piano),
 * ricostruito dai dati OpenStreetMap: una relazione `route=hiking` più la
 * geometria delle sue way membro, unite in un'unica linea continua (vedi
 * TrailRepository.stitchWays).
 */
data class NearbyTrail(
    val id: Long,
    val name: String,
    val points: List<TrackPoint>,
    /** Lunghezza totale del sentiero, calcolata sommando i segmenti della geometria. */
    val lengthMeters: Double,
    /** Distanza dalla posizione dell'utente al punto più vicino del sentiero. */
    val distanceFromUserMeters: Double,
    /** null se il sentiero non ha il tag `sac_scale` su OpenStreetMap — copertura non uniforme, vedi piano. */
    val difficulty: TrailDifficulty?,
)

/** Corrisponde ai valori del tag OSM `sac_scale` (Swiss Alpine Club hiking scale), dal più facile al più impegnativo. */
enum class TrailDifficulty {
    HIKING,
    MOUNTAIN_HIKING,
    DEMANDING_MOUNTAIN_HIKING,
    ALPINE_HIKING,
    DEMANDING_ALPINE_HIKING,
    DIFFICULT_ALPINE_HIKING,
}

fun NearbyTrail.toGpxTrack(): GpxTrack = GpxTrack(name = name, points = points)

// Velocità media di cammino usata per stimare il tempo di percorrenza. I dati
// Overpass di questa query non includono l'altitudine (solo lat/lon), quindi
// non è possibile una stima alla Naismith (che richiede il dislivello) come
// ipotizzato nel piano — semplificazione dichiarata: velocità piatta, senza
// tenere conto della pendenza. Un miglioramento futuro possibile è recuperare
// l'altitudine da un servizio esterno per i punti del tracciato, se si
// rivelasse importante dopo l'uso reale.
private const val AVERAGE_HIKING_SPEED_KMH = 4.0

/** Stima grezza del tempo di percorrenza in minuti, vedi nota sopra sul limite del calcolo. */
fun NearbyTrail.estimatedMinutes(): Int =
    ((lengthMeters / 1000.0) / AVERAGE_HIKING_SPEED_KMH * 60.0).roundToInt()
