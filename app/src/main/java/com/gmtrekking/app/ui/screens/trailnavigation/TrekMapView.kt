package com.gmtrekking.app.ui.screens.trailnavigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gmtrekking.app.data.gpx.GpxTrack
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature

/**
 * Wrapper Compose per una MapView di MapLibre, con:
 *  - il tracciato caricato disegnato come linea, SE presente ([track] è opzionale:
 *    l'app si apre mostrando solo la posizione corrente, senza obbligare a
 *    caricare un percorso);
 *  - la posizione corrente come cerchio, sempre visibile;
 *  - zoom automatico sulla posizione quando [autoZoomIn] è true (punti critici
 *    del percorso: bivi, tratti ravvicinati — vedi NavigationEngine.shouldZoomIn;
 *    ha senso solo quando un percorso è caricato, il chiamante passa false altrimenti);
 *  - ricentraggio manuale: incrementando [recenterRequest] (es. al tap di un
 *    pulsante "Ricentra" nella schermata chiamante) la camera torna sulla
 *    posizione corrente. Serve perché scorrendo la mappa per vedere cosa c'è
 *    più avanti lungo il percorso, altrimenti non c'era modo di tornare sulla
 *    propria posizione senza cercarla manualmente.
 *
 * Stile mappa: OpenFreeMap ("liberty", tile.openfreemap.org), gratuito e senza
 * chiave API, dati OpenStreetMap. Sostituisce lo stile dimostrativo iniziale
 * di MapLibre (demotiles.maplibre.org), che copre solo confini nazionali a
 * bassissimo dettaglio: fuori città, ai livelli di zoom usati da questa app
 * (15+), non c'era alcun dato da disegnare, quindi la mappa restava vuota,
 * con solo il colore di sfondo dello stile — bug reale confermato con un
 * test all'aperto (schermo verde/vuoto, nessuna mappa, posizione poco
 * visibile senza punti di riferimento intorno).
 */
@Composable
fun TrekMapView(
    track: GpxTrack?,
    currentLat: Double,
    currentLon: Double,
    autoZoomIn: Boolean,
    recenterRequest: Int = 0,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // Tiene traccia dell'ultimo tracciato per cui abbiamo già inquadrato la
    // camera, per non "saltare" ad ogni ricomposizione ma solo quando il
    // tracciato cambia davvero (es. l'utente carica un nuovo GPX).
    val lastFittedTrack = remember { mutableStateOf<GpxTrack?>(null) }
    // Ultimo valore di recenterRequest già gestito: un semplice contatore che
    // il chiamante incrementa ad ogni tap sul pulsante "Ricentra". Confrontarlo
    // con l'ultimo valore visto è il modo standard in Compose per reagire a un
    // "evento" (non a un valore continuo) dentro la callback update di AndroidView.
    val lastHandledRecenterRequest = remember { mutableStateOf(0) }

    // MapView richiede il proprio ciclo di vita Android (onCreate/onStart/...).
    // Lo colleghiamo a quello della schermata Compose inoltrando gli eventi del
    // LifecycleOwner alla MapView, come raccomandato dalla documentazione di
    // MapLibre/Mapbox per l'uso di MapView dentro Compose (via AndroidView).
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                mapViewRef.value = this
                onCreate(Bundle())
                onStart()
                onResume()
                getMapAsync { maplibreMap ->
                    maplibreMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                        addPositionLayer(style, currentLat, currentLon)
                        if (track != null) {
                            addTrackLayer(style, track)
                            fitCameraToTrack(maplibreMap, track)
                            lastFittedTrack.value = track
                        } else {
                            // Nessun percorso ancora caricato: centra semplicemente
                            // sulla posizione corrente, con uno zoom da "sto guardando
                            // la mia zona" piuttosto che il livello ravvicinato usato
                            // in navigazione attiva.
                            maplibreMap.moveCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(currentLat, currentLon))
                                        .zoom(ZOOM_OVERVIEW)
                                        .build()
                                )
                            )
                        }
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { maplibreMap ->
                val style = maplibreMap.style ?: return@getMapAsync
                updatePositionLayer(style, currentLat, currentLon)
                syncTrackLayer(style, track)

                if (track != null && track != lastFittedTrack.value) {
                    // Il tracciato è cambiato (nuovo GPX caricato, o rimosso e
                    // ricaricato): inquadra il nuovo percorso per intero.
                    fitCameraToTrack(maplibreMap, track)
                    lastFittedTrack.value = track
                } else if (track == null) {
                    lastFittedTrack.value = null
                }

                if (autoZoomIn) {
                    easeCameraToPosition(maplibreMap, currentLat, currentLon, ZOOM_DETAIL)
                }

                if (recenterRequest != lastHandledRecenterRequest.value) {
                    // Ricentraggio manuale (pulsante "Ricentra"): stesso livello di
                    // zoom "di dettaglio" usato durante la navigazione attiva se un
                    // percorso è caricato, altrimenti quello "d'insieme" di partenza.
                    val zoom = if (track != null) ZOOM_DETAIL else ZOOM_OVERVIEW
                    easeCameraToPosition(maplibreMap, currentLat, currentLon, zoom)
                    lastHandledRecenterRequest.value = recenterRequest
                }
            }
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.onDestroy()
            mapViewRef.value = null
        }
    }
}

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_TRACK = "gm-trekking-track-source"
private const val LAYER_TRACK = "gm-trekking-track-layer"
private const val SOURCE_POSITION = "gm-trekking-position-source"
private const val LAYER_POSITION = "gm-trekking-position-layer"

// Livelli di zoom standard dell'app: "d'insieme" (nessun percorso caricato,
// o vista di partenza) e "di dettaglio" (navigazione attiva su un percorso,
// punti critici, ricentraggio manuale con un percorso caricato).
private const val ZOOM_OVERVIEW = 15.0
private const val ZOOM_DETAIL = 17.0

private fun easeCameraToPosition(map: MapLibreMap, lat: Double, lon: Double, zoom: Double) {
    map.easeCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(lat, lon))
                .zoom(zoom)
                .build()
        )
    )
}

private fun addTrackLayer(style: Style, track: GpxTrack) {
    val points = track.points.map { Point.fromLngLat(it.longitude, it.latitude) }
    val lineString = LineString.fromLngLats(points)
    style.addSource(GeoJsonSource(SOURCE_TRACK, Feature.fromGeometry(lineString)))
    style.addLayer(
        LineLayer(LAYER_TRACK, SOURCE_TRACK).withProperties(
            PropertyFactory.lineColor("#3D7A64"),
            PropertyFactory.lineWidth(4f),
        )
    )
}

/**
 * Aggiunge, aggiorna o rimuove il layer del tracciato a seconda che [track]
 * sia presente o meno — usata nella `update` di AndroidView, quindi deve
 * essere sicura da chiamare ad ogni ricomposizione (idempotente).
 */
private fun syncTrackLayer(style: Style, track: GpxTrack?) {
    val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_TRACK)

    if (track == null) {
        if (existingSource != null) {
            style.removeLayer(LAYER_TRACK)
            style.removeSource(SOURCE_TRACK)
        }
        return
    }

    if (existingSource != null) {
        val points = track.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        existingSource.setGeoJson(LineString.fromLngLats(points))
    } else {
        addTrackLayer(style, track)
    }
}

private fun addPositionLayer(style: Style, lat: Double, lon: Double) {
    style.addSource(GeoJsonSource(SOURCE_POSITION, Feature.fromGeometry(Point.fromLngLat(lon, lat))))
    style.addLayer(
        CircleLayer(LAYER_POSITION, SOURCE_POSITION).withProperties(
            PropertyFactory.circleColor("#2E5E4E"),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        )
    )
}

private fun updatePositionLayer(style: Style, lat: Double, lon: Double) {
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_POSITION) ?: return
    source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
}

private fun fitCameraToTrack(map: MapLibreMap, track: GpxTrack) {
    val boundsBuilder = LatLngBounds.Builder()
    track.points.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
    }
}
