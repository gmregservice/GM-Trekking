package com.gmtrekking.app.data.navigation

import com.gmtrekking.app.data.gpx.GpxTrack
import com.gmtrekking.app.data.gpx.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motore di navigazione: dato un tracciato caricato e la posizione GPS corrente,
 * calcola lo stato da mostrare all'utente (NavigationState).
 *
 * Approccio (lo stesso usato da app come Gaia GPS, OsmAnd, GPX Viewer):
 * 1. Trova il punto del tracciato più vicino alla posizione corrente.
 * 2. Calcola la distanza tra la posizione e quel punto: se supera la soglia
 *    di tolleranza, l'utente è "fuori percorso".
 * 3. Calcola direzione (bearing) e distanza verso il punto successivo del
 *    tracciato, per la freccia direzionale mostrata in MainMapScreen.
 *
 * Le formule usano l'approssimazione equirettangolare (accurata a queste scale
 * di distanza, molto più leggera della formula di Haversine completa) e sono
 * pensate per girare ad ogni fix GPS, quindi devono restare economiche.
 */
class NavigationEngine(
    private val track: GpxTrack,
    private val thresholds: NavigationThresholds = NavigationThresholds(),
) {

    fun update(currentLat: Double, currentLon: Double): NavigationState {
        val points = track.points

        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        for (i in points.indices) {
            val d = distanceMeters(currentLat, currentLon, points[i].latitude, points[i].longitude)
            if (d < nearestDistance) {
                nearestDistance = d
                nearestIndex = i
            }
        }

        val nextIndex = (nearestIndex + 1).coerceAtMost(points.lastIndex)
        val nextPoint = points[nextIndex]

        val bearing = bearingDegrees(currentLat, currentLon, nextPoint.latitude, nextPoint.longitude)
        val distanceToNext = distanceMeters(currentLat, currentLon, nextPoint.latitude, nextPoint.longitude)
        val remaining = remainingDistanceMeters(points, nearestIndex, currentLat, currentLon)

        return NavigationState(
            nearestPointIndex = nearestIndex,
            distanceToTrackMeters = nearestDistance,
            bearingToNextPointDegrees = bearing,
            distanceToNextPointMeters = distanceToNext,
            distanceRemainingMeters = remaining,
            isOffRoute = nearestDistance > thresholds.offRouteToleranceMeters,
            shouldZoomIn = distanceToNext <= thresholds.autoZoomTriggerDistanceMeters,
        )
    }

    private fun remainingDistanceMeters(
        points: List<TrackPoint>,
        fromIndex: Int,
        currentLat: Double,
        currentLon: Double,
    ): Double {
        if (fromIndex >= points.lastIndex) return 0.0
        var total = distanceMeters(currentLat, currentLon, points[fromIndex].latitude, points[fromIndex].longitude)
        for (i in fromIndex until points.lastIndex) {
            total += distanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude,
            )
        }
        return total
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Distanza approssimata in metri tra due coordinate, valida per tratti di poche decine di km. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val avgLatRad = Math.toRadians((lat1 + lat2) / 2.0)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val x = dLon * cos(avgLatRad)
            val y = dLat
            return sqrt(x * x + y * y) * EARTH_RADIUS_METERS
        }

        /** Direzione (0-360°, 0 = nord, in senso orario) dal punto 1 al punto 2. */
        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val dLonRad = Math.toRadians(lon2 - lon1)

            val y = sin(dLonRad) * cos(lat2Rad)
            val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
            val bearingRad = atan2(y, x)
            return (Math.toDegrees(bearingRad) + 360.0) % 360.0
        }
    }
}
