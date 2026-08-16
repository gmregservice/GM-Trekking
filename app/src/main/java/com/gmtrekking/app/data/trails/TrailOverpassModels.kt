package com.gmtrekking.app.data.trails

import kotlinx.serialization.Serializable

/**
 * Modello della risposta Overpass per la query "sentieri vicini" (punto 5 del
 * piano) — diverso dal modello già usato per i luoghi utili (OverpassModels.kt
 * in data/poi/) perché qui servono anche `members` (le way di una relazione)
 * e `geometry` (i punti di una way, restituiti da "out geom"), non presenti
 * nella query dei luoghi utili.
 */
@Serializable
data class TrailOverpassResponse(
    val elements: List<TrailOverpassElement> = emptyList(),
)

@Serializable
data class TrailOverpassElement(
    val id: Long,
    val type: String, // "relation" o "way"
    val tags: Map<String, String> = emptyMap(),
    /** Presente sulle way quando la query usa "out geom". */
    val geometry: List<TrailGeomPoint>? = null,
    /** Presente sulle relation: le way (e altri membri) che le compongono, in ordine. */
    val members: List<TrailMember>? = null,
)

@Serializable
data class TrailGeomPoint(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class TrailMember(
    val type: String, // "way", "node" o "relation"
    val ref: Long,
)
