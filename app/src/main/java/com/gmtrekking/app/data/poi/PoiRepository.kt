package com.gmtrekking.app.data.poi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Recupera i luoghi utili da Overpass API e li converte nel modello Poi
 * usato dall'interfaccia (PlacesScreen).
 *
 * Nessuna cache locale in questa versione: ogni chiamata a [findNearby] va in
 * rete. La cache offline (per rendere l'elenco disponibile anche senza
 * connessione dopo un primo download) è pianificata per la Fase 2 del piano
 * di sviluppo, con Room.
 */
class PoiRepository(
    private val api: OverpassApiService = OverpassApiService.create(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findNearby(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1500,
        categories: List<PlaceCategory> = listOf(PlaceCategory.ALL),
    ): List<Poi> = withContext(Dispatchers.IO) {
        val query = OverpassQueryBuilder.buildAroundPointQuery(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            categories = categories,
        )

        val rawResponse = api.query(query)
        val parsed = json.decodeFromString(OverpassResponse.serializer(), rawResponse)

        parsed.elements.mapNotNull { element -> element.toPoiOrNull() }
    }

    private fun OverpassElement.toPoiOrNull(): Poi? {
        val category = OverpassQueryBuilder.classify(tags) ?: return null
        val name = tags["name"] ?: return null // scartiamo i punti senza nome: poco utili in un elenco

        val latitude = lat ?: center?.lat ?: return null
        val longitude = lon ?: center?.lon ?: return null

        return Poi(
            osmId = id,
            name = name,
            category = category,
            latitude = latitude,
            longitude = longitude,
            address = buildAddress(tags),
            openingHours = tags["opening_hours"],
            phone = tags["phone"] ?: tags["contact:phone"],
        )
    }

    private fun buildAddress(tags: Map<String, String>): String? {
        val street = tags["addr:street"]
        val houseNumber = tags["addr:housenumber"]
        val city = tags["addr:city"]
        val parts = listOfNotNull(
            listOfNotNull(street, houseNumber).joinToString(" ").ifBlank { null },
            city,
        )
        return parts.joinToString(", ").ifBlank { null }
    }
}
