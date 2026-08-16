package com.gmtrekking.app.data.trails

import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.navigation.NavigationEngine
import com.gmtrekking.app.data.poi.OverpassApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Recupera i sentieri vicini da Overpass API (punto 5 del piano) e li
 * converte in [NearbyTrail]. Riusa lo stesso client Overpass già configurato
 * per i luoghi utili (mirror, timeout, header — vedi data/poi/OverpassApiService.kt),
 * cambia solo la query e il modello di risposta (qui serve la geometria
 * completa delle way, non necessaria per i luoghi utili).
 */
class TrailRepository(
    private val api: OverpassApiService = OverpassApiService.create(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findNearby(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 5000,
    ): List<NearbyTrail> = withContext(Dispatchers.IO) {
        val query = TrailQueryBuilder.buildNearbyTrailsQuery(centerLat, centerLon, radiusMeters)
        val rawResponse = api.query(query)
        val parsed = json.decodeFromString(TrailOverpassResponse.serializer(), rawResponse)

        val relations = parsed.elements.filter { it.type == "relation" }
        val waysById = parsed.elements.filter { it.type == "way" }.associateBy { it.id }

        relations
            .mapNotNull { relation -> relation.toNearbyTrailOrNull(waysById, centerLat, centerLon) }
            .sortedBy { it.distanceFromUserMeters }
    }

    private fun TrailOverpassElement.toNearbyTrailOrNull(
        waysById: Map<Long, TrailOverpassElement>,
        centerLat: Double,
        centerLon: Double,
    ): NearbyTrail? {
        val name = tags["name"] ?: return null // scartiamo i sentieri senza nome: poco utili in un elenco

        val memberWays = members.orEmpty()
            .filter { it.type == "way" }
            .mapNotNull { waysById[it.ref] }
        if (memberWays.isEmpty()) return null

        val points = stitchWays(memberWays)
        if (points.size < 2) return null

        val distanceFromUser = points.minOf {
            NavigationEngine.distanceMeters(centerLat, centerLon, it.latitude, it.longitude)
        }

        return NearbyTrail(
            id = id,
            name = name,
            points = points,
            lengthMeters = totalDistance(points),
            distanceFromUserMeters = distanceFromUser,
            difficulty = TrailQueryBuilder.difficultyFor(tags["sac_scale"]),
        )
    }

    /**
     * Concatena la geometria delle way membro di una relazione in un'unica
     * linea continua. Le relazioni OSM elencano i membri nell'ordine del
     * percorso, ma ogni way può essere digitata in un verso o nell'altro
     * senza garanzia di coerenza tra way consecutive — per questo, per ogni
     * way dopo la prima si sceglie l'orientamento (dritto o invertito) che
     * continua più vicino all'ultimo punto già aggiunto, invece di
     * concatenare alla cieca rischiando un tracciato "a zig-zag" e una
     * lunghezza calcolata gonfiata.
     */
    private fun stitchWays(ways: List<TrailOverpassElement>): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        for (way in ways) {
            val wayPoints = way.geometry.orEmpty().map { TrackPoint(it.lat, it.lon) }
            if (wayPoints.isEmpty()) continue
            val last = result.lastOrNull()
            if (last == null) {
                result += wayPoints
                continue
            }
            val distanceToStart = NavigationEngine.distanceMeters(
                last.latitude, last.longitude, wayPoints.first().latitude, wayPoints.first().longitude,
            )
            val distanceToEnd = NavigationEngine.distanceMeters(
                last.latitude, last.longitude, wayPoints.last().latitude, wayPoints.last().longitude,
            )
            result += if (distanceToEnd < distanceToStart) wayPoints.reversed() else wayPoints
        }
        return result
    }

    private fun totalDistance(points: List<TrackPoint>): Double {
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += NavigationEngine.distanceMeters(
                points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude,
            )
        }
        return total
    }
}
