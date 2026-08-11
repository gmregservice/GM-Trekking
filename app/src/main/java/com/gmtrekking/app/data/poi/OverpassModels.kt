package com.gmtrekking.app.data.poi

import kotlinx.serialization.Serializable

/** Modello minimo della risposta JSON di Overpass API (solo i campi che usiamo). */
@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
data class OverpassElement(
    val id: Long,
    val type: String, // "node", "way" o "relation"
    val lat: Double? = null,
    val lon: Double? = null,
    /** Per way/relation, Overpass restituisce il centro geometrico se si chiede "out center". */
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
data class OverpassCenter(
    val lat: Double,
    val lon: Double,
)
