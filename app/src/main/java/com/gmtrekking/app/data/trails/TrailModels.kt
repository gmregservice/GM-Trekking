package com.gmtrekking.app.data.trails

import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import kotlinx.serialization.Serializable
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
    /**
     * Numero ufficiale del sentiero (tag OSM `ref`, es. numerazione CAI tipo
     * "126") quando presente — spesso il codice breve usato sulla
     * segnaletica reale sul terreno, distinto dal nome descrittivo. null se
     * il tag manca: copertura non uniforme su OpenStreetMap, stesso limite
     * già noto per `sac_scale` (vedi piano) — mai un valore inventato.
     */
    val ref: String?,
    val points: List<TrackPoint>,
    /** Lunghezza totale del sentiero, calcolata sommando i segmenti della geometria. */
    val lengthMeters: Double,
    /** Distanza dalla posizione dell'utente al punto più vicino del sentiero. */
    val distanceFromUserMeters: Double,
    /** null se il sentiero non ha il tag `sac_scale` su OpenStreetMap — copertura non uniforme, vedi piano. */
    val difficulty: TrailDifficulty?,
)

/**
 * Nome mostrato all'utente: "numero · nome" quando il tag `ref` è
 * disponibile (es. "126 · Sentiero dei Contrabbandieri"), altrimenti solo il
 * nome — richiesto esplicitamente (agosto 2026), per poter riconoscere un
 * sentiero dal numero riportato sulla segnaletica reale, non solo dal nome.
 */
fun NearbyTrail.displayName(): String = ref?.let { "$it · $name" } ?: name

/**
 * Corrisponde ai valori del tag OSM `sac_scale` (Swiss Alpine Club hiking
 * scale), dal più facile al più impegnativo. `@Serializable`: necessario per
 * salvare un [NearbyTrail] scaricato in locale (vedi SavedTrail.kt) —
 * aggiunto insieme a quella funzionalità, nessun impatto sull'uso esistente.
 */
@Serializable
enum class TrailDifficulty {
    HIKING,
    MOUNTAIN_HIKING,
    DEMANDING_MOUNTAIN_HIKING,
    ALPINE_HIKING,
    DEMANDING_ALPINE_HIKING,
    DIFFICULT_ALPINE_HIKING,
}

fun NearbyTrail.toGpxTrack(): GpxTrack = GpxTrack(name = displayName(), points = points)

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
