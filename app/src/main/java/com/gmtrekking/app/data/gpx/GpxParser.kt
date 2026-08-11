package com.gmtrekking.app.data.gpx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser minimale per file GPX 1.1.
 *
 * Legge il primo elemento <trk> del file ed estrae tutti i <trkpt> dai suoi
 * <trkseg>, in ordine. Non gestisce (ancora) waypoint isolati, più tracce
 * nello stesso file, o route <rte> — sufficiente per l'MVP, dove l'utente
 * carica un singolo percorso per volta.
 *
 * Implementato con XmlPullParser (incluso in Android) invece di una libreria
 * esterna dedicata al GPX, per tenere sotto controllo il numero di dipendenze
 * nella prima versione dello scheletro.
 */
object GpxParser {

    fun parse(input: InputStream, fallbackName: String = "Percorso"): GpxTrack {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var trackName: String? = null
        val points = mutableListOf<TrackPoint>()

        var eventType = parser.eventType
        var insideTrk = false
        var insideName = false
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "trk" -> insideTrk = true
                    "name" -> if (insideTrk && trackName == null) insideName = true
                    "trkpt" -> {
                        currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        currentEle = null
                    }
                    "ele" -> if (currentLat != null) {
                        val text = if (parser.next() == XmlPullParser.TEXT) parser.text else null
                        currentEle = text?.toDoubleOrNull()
                    }
                }

                XmlPullParser.TEXT -> if (insideName) {
                    trackName = parser.text?.trim()
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "name" -> insideName = false
                    "trkpt" -> {
                        val lat = currentLat
                        val lon = currentLon
                        if (lat != null && lon != null) {
                            points += TrackPoint(lat, lon, currentEle)
                        }
                        currentLat = null
                        currentLon = null
                        currentEle = null
                    }
                }
            }
            eventType = parser.next()
        }

        return GpxTrack(name = trackName?.ifBlank { null } ?: fallbackName, points = points)
    }
}
