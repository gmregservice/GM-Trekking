package com.gmtrekking.app.data.routing

import com.gmtrekking.app.data.gpx.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Esito del calcolo di un percorso reale: non un semplice `List<TrackPoint>?`
 * come nella prima versione (v1.24), perché senza quel dettaglio non c'era
 * alcun modo di distinguere, dal solo comportamento della UI, "chiave
 * mancante" da "richiesta fallita" da "risposta senza percorso valido" — un
 * problema concreto, segnalato su dispositivo reale con chiave API già
 * configurata e verificata funzionante esternamente (agosto 2026, vedi
 * docs/PIANO_SVILUPPO.md punto 15). [Failure.detail] è pensato per essere
 * mostrato direttamente in UI (`MainMapScreen.kt`), stesso principio già
 * usato per gli errori di Overpass API in `PlacesViewModel.kt`: senza
 * accesso a Logcat, il dettaglio dell'eccezione DEVE arrivare a schermo,
 * altrimenti un fallimento resta indistinguibile da un altro.
 */
sealed class RoutingOutcome {
    data class Success(val points: List<TrackPoint>) : RoutingOutcome()
    data class Failure(val detail: String) : RoutingOutcome()
}

/**
 * Calcola un percorso reale (su sentieri/strade, non una linea retta) tra due
 * punti tramite OpenRouteService — usato per la navigazione verso un luogo
 * utile (PlacesScreen.kt/MainMapScreen.kt) quando l'utente ha configurato la
 * propria chiave API in Impostazioni (data/settings/AppSettingsStorage.kt).
 *
 * **Nessuna eccezione propagata al chiamante**: qualunque fallimento (rete,
 * HTTP non 2xx, risposta non valida) diventa un [RoutingOutcome.Failure] con
 * dettaglio, mai un crash o un blocco della navigazione — chi chiama questa
 * funzione decide se e come mostrare l'errore, ma può sempre continuare con
 * la linea retta di riserva (già esistente prima di questa funzione): vedi
 * `PoiNavigationHolder.kt`.
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
    ): RoutingOutcome = withContext(Dispatchers.IO) {
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
            val coordinates = parsed.features.firstOrNull()?.geometry?.coordinates
            if (coordinates == null) {
                return@runCatching RoutingOutcome.Failure(
                    "Risposta senza percorso: nessun elemento \"features\" nel GeoJSON restituito.",
                )
            }
            if (coordinates.size < 2) {
                return@runCatching RoutingOutcome.Failure(
                    "Risposta con un percorso di meno di 2 punti (${coordinates.size}).",
                )
            }

            RoutingOutcome.Success(
                coordinates.map { point ->
                    // point[0] = lon, point[1] = lat, point[2] = elevazione (se presente).
                    TrackPoint(
                        latitude = point[1],
                        longitude = point[0],
                        elevationMeters = point.getOrNull(2),
                    )
                },
            )
        }.getOrElse { throwable -> RoutingOutcome.Failure(describeError(throwable)) }
    }

    /**
     * Messaggio pensato per essere leggibile a schermo, non per lo sviluppatore:
     * per un `HttpException` (richiesta arrivata al server ma risposta non
     * 2xx — es. 401 chiave non valida, 403 chiave non attivata, 429 quota
     * giornaliera esaurita) mostra il codice HTTP, altrimenti il tipo e il
     * messaggio dell'eccezione (timeout, DNS, JSON malformato...), stesso
     * approccio già usato per gli errori di Overpass API in `PlacesViewModel.kt`.
     */
    private fun describeError(throwable: Throwable): String = when (throwable) {
        is HttpException -> "HTTP ${throwable.code()}: ${throwable.message()}"
        else -> "${throwable::class.simpleName}: ${throwable.message ?: "nessun dettaglio"}"
    }
}
