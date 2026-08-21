package com.gmtrekking.app.data.maps

/**
 * Unico posto con l'URL dello stile mappa (OpenFreeMap "liberty", tile
 * vettoriali, dati OpenStreetMap — vedi TrekMapView.kt per il contesto
 * completo). Usato sia dalla mappa online (TrekMapView) sia dal download
 * offline (OfflineMapManager): deve restare lo stesso URL in entrambi i casi,
 * un valore duplicato in due file rischierebbe di disallinearsi in futuro
 * (es. se lo stile cambiasse, si aggiornerebbe solo la mappa online e non il
 * download offline, o viceversa).
 */
object MapStyle {
    const val URL = "https://tiles.openfreemap.org/styles/liberty"
}
