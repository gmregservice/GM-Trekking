package com.gmtrekking.app.data.trails

import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Punto di un [SavedTrail] — stessi campi di [TrackPoint] (data/gpx), ma
 * ridefiniti qui invece di riusare direttamente quella classe: TrackPoint non
 * è `@Serializable` (pensata solo per la lettura in memoria di un file GPX
 * appena caricato, mai salvata su disco), e non aveva senso aggiungere quella
 * dipendenza a un modulo che se ne occupa solo di persistenza. Stessa
 * separazione già presente tra TrackPoint e TrackedPoint (vedi
 * data/tracking/TrackingModels.kt).
 */
@Serializable
data class SavedTrailPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
)

/**
 * Un [NearbyTrail] scaricato e salvato in locale per l'uso offline (vedi
 * SavedTrailsStorage.kt), associato al nome dell'area da cui proviene
 * ([areaName], es. "Val di Mello" — vedi FixedTrailAreas.kt) per poter
 * scaricare/eliminare le tracce di un'area senza toccare quelle di un'altra.
 * Non porta `distanceFromUserMeters`: quel valore ha senso solo al momento
 * della ricerca (distanza dalla posizione di allora), non ha più significato
 * una volta salvato e riaperto in un momento/luogo diverso.
 */
@Serializable
data class SavedTrail(
    val id: Long,
    val name: String,
    val ref: String?,
    val points: List<SavedTrailPoint>,
    val lengthMeters: Double,
    val difficulty: TrailDifficulty?,
    val areaName: String,
)

/** Stesso criterio di NearbyTrail.displayName(): "numero · nome" quando il tag `ref` è disponibile. */
fun SavedTrail.displayName(): String = ref?.let { "$it · $name" } ?: name

fun NearbyTrail.toSavedTrail(areaName: String): SavedTrail = SavedTrail(
    id = id,
    name = name,
    ref = ref,
    points = points.map { SavedTrailPoint(it.latitude, it.longitude, it.elevationMeters) },
    lengthMeters = lengthMeters,
    difficulty = difficulty,
    areaName = areaName,
)

fun SavedTrail.toGpxTrack(): GpxTrack = GpxTrack(
    name = displayName(),
    points = points.map { TrackPoint(it.latitude, it.longitude, it.elevationMeters) },
)

// Stessa velocità/nota di NearbyTrail.estimatedMinutes() (vedi TrailModels.kt): nessuna altitudine nei dati Overpass, stima piatta.
private const val AVERAGE_HIKING_SPEED_KMH = 4.0

fun SavedTrail.estimatedMinutes(): Int =
    ((lengthMeters / 1000.0) / AVERAGE_HIKING_SPEED_KMH * 60.0).roundToInt()
