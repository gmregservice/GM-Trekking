package com.gmtrekking.app.data.maps

import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Aree scaricabili per l'uso offline della mappa (vedi OfflineMapManager.kt).
 * Per ora una sola area fissa (Lombardia): la scelta libera di un'area
 * qualunque (disegnata dall'utente, o centrata su un percorso caricato) è
 * pianificata per un secondo momento (richiesto esplicitamente, agosto 2026)
 * — qui c'è solo l'infrastruttura per scaricare/gestire un'area, non ancora
 * la scelta di quale.
 */
object OfflineRegions {

    /**
     * Rettangolo che racchiude la Lombardia (confini approssimati: MapLibre
     * scarica sempre un'area rettangolare, non un poligono ritagliato sui
     * confini regionali reali — normale per uno strumento di mappe offline,
     * include quindi anche piccole porzioni delle regioni/paesi confinanti
     * ai bordi). Coordinate scelte con un margine generoso rispetto ai
     * confini reali (circa 44.65–46.65 N, 8.45–11.45 E) per non rischiare di
     * tagliare fuori zone di confine — meglio qualche tile in più ai bordi
     * che un'area incompleta.
     */
    private val LOMBARDIA_BOUNDS = LatLngBounds.from(
        /* latNorth = */ 46.65,
        /* lonEast = */ 11.45,
        /* latSouth = */ 44.65,
        /* lonWest = */ 8.45,
    )

    // Zoom minimo 6 (vista d'insieme, pochissime tile, costo trascurabile) e
    // massimo 14: gli stili a tile vettoriali con schema OpenMapTiles (come
    // OpenFreeMap "liberty" usato da questa app, vedi MapStyle.kt) smettono di
    // generare dati oltre lo zoom 14 — i livelli più ravvicinati si ottengono
    // ingrandendo via software gli stessi dati, senza scaricare altre tile.
    // Andare oltre 14 qui non aggiungerebbe dettaglio reale, solo tile
    // duplicate.
    private const val MIN_ZOOM = 6.0
    private const val MAX_ZOOM = 14.0

    const val LOMBARDIA_NAME = "Lombardia"

    fun lombardiaDefinition(pixelRatio: Float): OfflineTilePyramidRegionDefinition =
        OfflineTilePyramidRegionDefinition(
            /* styleURL = */ MapStyle.URL,
            /* bounds = */ LOMBARDIA_BOUNDS,
            /* minZoom = */ MIN_ZOOM,
            /* maxZoom = */ MAX_ZOOM,
            /* pixelRatio = */ pixelRatio,
        )
}
