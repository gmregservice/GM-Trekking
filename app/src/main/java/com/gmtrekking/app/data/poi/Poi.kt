package com.gmtrekking.app.data.poi

/** Un singolo luogo utile (ristorante, hotel, rifugio, ecc.), da OpenStreetMap. */
data class Poi(
    val osmId: Long,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val openingHours: String? = null,
    val phone: String? = null,
)
