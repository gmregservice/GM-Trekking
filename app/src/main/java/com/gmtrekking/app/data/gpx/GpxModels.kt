package com.gmtrekking.app.data.gpx

/** Un singolo punto del tracciato, così come letto dal file GPX. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
)

/** Un percorso caricato dall'utente, con nome e lista ordinata di punti. */
data class GpxTrack(
    val name: String,
    val points: List<TrackPoint>,
) {
    init {
        require(points.size >= 2) { "Un tracciato deve avere almeno due punti." }
    }
}
