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
 *  - il tracciato caricato disegnato come linea;
 *  - la posizione corrente come cerchio;
 *  - zoom automatico sulla posizione quando [autoZoomIn] è true (punti critici
 *    del percorso: bivi, tratti ravvicinati — vedi NavigationEngine.shouldZoomIn).
 *
 * NOTA IMPORTANTE: usa lo stile dimostrativo pubblico di MapLibre
 * (demotiles.maplibre.org), pensato solo per test — ha pochissimo dettaglio
 * cartografico. Prima di qualsiasi uso reale va sostituito con uno stile
 * mappa vero (es. OpenFreeMap, MapTiler, Stadia Maps, o un servizio tile
 * self-hosted), come discusso nell'analisi di fattibilità. Questo è anche il
 * punto del progetto con più probabilità di richiedere aggiustamenti alla
 * prima apertura in Android Studio, perché non è stato possibile compilarlo
 * in questo ambiente per verificarlo.
 */
@Composable
fun TrekMapView(
    track: GpxTrack,
    currentLat: Double,
    currentLon: Double,
    autoZoomIn: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

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
                    maplibreMap.setStyle(Style.Builder().fromUri(DEMO_STYLE_URL)) { style ->
                        addTrackLayer(style, track)
                        addPositionLayer(style, currentLat, currentLon)
                        fitCameraToTrack(maplibreMap, track)
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { maplibreMap ->
                val style = maplibreMap.style ?: return@getMapAsync
                updatePositionLayer(style, currentLat, currentLon)
                if (autoZoomIn) {
                    maplibreMap.easeCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(currentLat, currentLon))
                                .zoom(17.0)
                                .build()
                        )
                    )
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

private const val DEMO_STYLE_URL = "https://demotiles.maplibre.org/style.json"
private const val SOURCE_TRACK = "gm-trekking-track-source"
private const val LAYER_TRACK = "gm-trekking-track-layer"
private const val SOURCE_POSITION = "gm-trekking-position-source"
private const val LAYER_POSITION = "gm-trekking-position-layer"

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
