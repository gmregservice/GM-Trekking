package com.gmtrekking.app.data.poi

/**
 * Categorie di "luoghi utili" mostrate all'utente. Volutamente un elenco
 * piccolo e comprensibile (non i mille tag di OpenStreetMap): ogni categoria
 * è mappata su uno o più tag OSM in OverpassQueryBuilder.
 */
enum class PlaceCategory {
    ALL,
    RESTAURANT,
    BAR,
    TRATTORIA,
    HOTEL,
    HOSTEL,
    ALPINE_HUT,
    CAMP_SITE,
    GUEST_HOUSE,
}
