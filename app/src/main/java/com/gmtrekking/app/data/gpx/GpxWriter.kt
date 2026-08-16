package com.gmtrekking.app.data.gpx

/**
 * Genera un file GPX 1.1 valido da un [GpxTrack] — l'inverso di [GpxParser],
 * che invece legge un file GPX. Usato per esportare i sentieri scoperti nelle
 * vicinanze (punto 5 del piano, `ui/screens/trails/NearbyTrailsScreen.kt`),
 * così l'utente ottiene un vero file `.gpx` da tenere o riaprire altrove,
 * oltre a poterlo usare direttamente in app.
 */
object GpxWriter {

    fun write(track: GpxTrack): String {
        val trackPoints = track.points.joinToString("\n") { point ->
            val eleTag = point.elevationMeters?.let { "<ele>$it</ele>" } ?: ""
            "      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">$eleTag</trkpt>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="GM-Trekking" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>${escapeXml(track.name)}</name>
                <trkseg>
            $trackPoints
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
