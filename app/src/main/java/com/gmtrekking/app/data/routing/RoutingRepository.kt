package com.gmtrekking.app.data.routing

import com.gmtrekking.app.data.gpx.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Calcola un percorso reale (su sentieri/strade, non una linea retta) tra due
 * punti tramite OpenRouteService — usato per la navigazione verso un luogo
 * utile (PlacesScreen.kt/MainMapScreen.kt) quando l'utente ha configurato la
 * propria chiave API in Impostazioni (data/settings/AppSettingsStorage.kt).
 *
 * **Nessuna chiave, o richiesta fallita: null, mai un'eccezione propagata**.
 * Il chiamante interpreta `null` come "usa la linea retta di riserva" (già
 * esistente prima di questa funzione): l'instradamento reale è un
 * miglioramento facoltativo, non deve mai impedire di avviare comunque la
 * navigazione verso il luogo scelto — coerente con lo stesso principio già
 * seguito per Overpass API (mai bloccare l'app per un servizio esterno non
 * disponibile).
 */
class RoutingRepository(
    private val api: RoutingApiService = RoutingApiService.create(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findHikingRoute(
        apiKey: String,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
    ): List<TrackPoint>? = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                RoutingRequest.serializer(),
                // [lon, lat]: ordine richiesto dall'API, vedi RoutingModels.kt.
                RoutingRequest(coordinates = listOf(listOf(startLon, startLat), listOf(endLon, endLat))),
            )
            val rawResponse = api.route(
                profile = RoutingApiService.PROFILE_HIKING,
                apiKey = apiKey,
                body = requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            val parsed = json.decodeFromString(RoutingResponse.serializer(), rawResponse)
            val coordinates = parsed.features.firstOrNull()?.geometry?.coordinates ?: return@runCatching null
            if (coordinates.size < 2) return@runCatching null

            coordinates.map { point ->
                // point[0] = lon, point[1] = lat, point[2] = elevazione (se presente).
                TrackPoint(
                    latitude = point[1],
                    longitude = point[0],
                    elevationMeters = point.getOrNull(2),
                )
            }
        }.getOrNull()
    }
}
