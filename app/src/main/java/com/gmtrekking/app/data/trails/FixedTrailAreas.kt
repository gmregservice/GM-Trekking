package com.gmtrekking.app.data.trails

/**
 * Un'area per cui si possono scaricare in blocco i sentieri vicini (vedi
 * TrailRepository.findNearby), da tenere in locale per l'uso offline (vedi
 * SavedTrailsStorage.kt) — indipendente dalla posizione GPS dell'utente al
 * momento del download, a differenza di "Sentieri vicini" (NearbyTrailsScreen)
 * che cerca invece intorno alla posizione corrente.
 */
data class FixedTrailArea(
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Int,
)

/**
 * Aree fisse per cui è possibile scaricare i sentieri (vedi SettingsScreen,
 * sezione "Sentieri scaricati"). Per ora una sola area, Val di Mello — la
 * scelta libera dell'area (qualunque punto sulla mappa) è pianificata per un
 * secondo momento (richiesto esplicitamente, agosto 2026), stessa scelta già
 * fatta per la mappa offline (vedi data/maps/OfflineRegions.kt).
 */
object FixedTrailAreas {

    /**
     * Centro e raggio scelti per coprire l'intera valle (confini noti: circa
     * 46.236–46.303 N, 9.609–9.740 E) più un margine per i sentieri che si
     * inerpicano sui versanti circostanti (es. verso i rifugi) — un raggio
     * pari alla sola metà della valle rischierebbe di tagliare fuori sentieri
     * reali che iniziano dentro la valle ma proseguono oltre.
     */
    val VAL_DI_MELLO = FixedTrailArea(
        name = "Val di Mello",
        centerLat = 46.269,
        centerLon = 9.674,
        radiusMeters = 7000,
    )
}
