package com.gmtrekking.app.data.routing

import kotlinx.serialization.Serializable

/**
 * Corpo della richiesta POST verso l'endpoint Directions di OpenRouteService
 * (`/v2/directions/{profile}/geojson`): richiede solo le coordinate, come
 * elenco di coppie `[longitudine, latitudine]` (ordine richiesto dall'API,
 * invertito rispetto alla convenzione lat/lon usata nel resto di questo
 * progetto — attenzione a non invertirlo per errore).
 */
@Serializable
data class RoutingRequest(
    val coordinates: List<List<Double>>,
)

/**
 * Risposta in formato GeoJSON. Modello ridotto al minimo necessario (solo la
 * geometria del primo percorso restituito): OpenRouteService include anche
 * istruzioni testuali, distanza/durata ecc., non usati da questa versione
 * dell'app — `ignoreUnknownKeys` (impostato nel Json usato per decodificare,
 * vedi RoutingRepository.kt) fa sì che quei campi vengano semplicemente
 * ignorati invece di far fallire il parsing.
 */
@Serializable
data class RoutingResponse(
    val features: List<RoutingFeature> = emptyList(),
)

@Serializable
data class RoutingFeature(
    val geometry: RoutingGeometry,
)

/**
 * `coordinates`: elenco di punti `[lon, lat]` o `[lon, lat, elevazione]` che
 * compongono il percorso calcolato, nello stesso ordine richiesto in
 * [RoutingRequest] (lon prima di lat).
 */
@Serializable
data class RoutingGeometry(
    val coordinates: List<List<Double>> = emptyList(),
)
