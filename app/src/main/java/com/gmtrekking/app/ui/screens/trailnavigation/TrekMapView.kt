package com.gmtrekking.app.ui.screens.trailnavigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.gmtrekking.app.data.maps.MapStyle
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * Wrapper Compose per una MapView di MapLibre, con:
 *  - il tracciato caricato disegnato come linea, SE presente ([track] è opzionale:
 *    l'app si apre mostrando solo la posizione corrente, senza obbligare a
 *    caricare un percorso);
 *  - la posizione corrente come cerchio, sempre visibile;
 *  - zoom automatico sulla posizione quando [autoZoomIn] è true (vicino al
 *    prossimo punto del tracciato — vedi NavigationEngine.shouldZoomIn; ha
 *    senso solo quando un percorso è caricato, il chiamante passa false
 *    altrimenti), MA sospeso finché l'utente sta scorrendo/zoomando
 *    manualmente la mappa (vedi userIsPanning sotto) — bug reale confermato
 *    su dispositivo (agosto 2026, "mappa bloccata durante la navigazione"):
 *    con punti del tracciato ravvicinati (un GPX vero, o un percorso reale
 *    calcolato verso un luogo) si è quasi sempre entro la soglia dei 60 m dal
 *    prossimo punto, quindi autoZoomIn restava vero in modo pressoché
 *    continuo, e la camera veniva ri-centrata ad ogni fix GPS annullando nel
 *    giro di un secondo qualunque pan/zoom manuale;
 *  - ricentraggio manuale: incrementando [recenterRequest] (es. al tap di un
 *    pulsante "Ricentra" nella schermata chiamante) la camera torna sulla
 *    posizione corrente e riprende lo zoom automatico sospeso sopra. Serve
 *    perché scorrendo la mappa per vedere cosa c'è più avanti lungo il
 *    percorso, altrimenti non c'era modo di tornare sulla propria posizione
 *    senza cercarla manualmente.
 *  - modalità "sola lettura" ([showCurrentPosition] = false): nasconde il
 *    cerchio di posizione, per la Cronologia percorsi (ActivityDetailScreen),
 *    dove si rivede un percorso concluso e non ha senso mostrare "dove sono
 *    ora" mescolato a un tracciato del passato.
 *  - [waypoints]: punti (lat, lon) delle note/foto geolocalizzate raccolte
 *    durante la registrazione (punti 3 e 4 del piano), disegnati come cerchi
 *    di colore diverso dalla posizione corrente — usato dal dettaglio della
 *    Cronologia. Semplificazione deliberata: solo un indicatore "qui c'è una
 *    nota/foto", non un'icona per tipo né un tocco per aprirla — il dettaglio
 *    completo si legge nell'elenco sotto la mappa, non sulla mappa stessa.
 *  - [navigationBearingDegrees]: quando non null (navigazione attiva),
 *    sostituisce il cerchio di posizione con una freccia rossa ruotata di
 *    questo angolo, ancorata alle coordinate GPS reali (non un overlay fisso
 *    al centro schermo: resta allineata alla posizione vera anche scorrendo
 *    o zoomando la mappa). Spostata qui dal pannello sotto la mappa
 *    (richiesto esplicitamente, agosto 2026): prima occupava uno spazio
 *    fisso indipendente dalla mappa, lasciandole poco più di metà schermo.
 *    Il valore dell'angolo va calcolato dal chiamante (MainMapScreen.kt):
 *    qui viene solo disegnato, non interpretato.
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
    showCurrentPosition: Boolean = true,
    waypoints: List<Pair<Double, Double>> = emptyList(),
    navigationBearingDegrees: Double? = null,
    focusOnPoi: Pair<Double, Double>? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // Tiene traccia dell'ultimo tracciato per cui abbiamo già inquadrato la
    // camera, per non "saltare" ad ogni ricomposizione ma solo quando il
    // tracciato cambia davvero (es. l'utente carica un nuovo GPX).
    val lastFittedTrack = remember { mutableStateOf<GpxTrack?>(null) }
    // Stessa idea di lastFittedTrack, ma per [focusOnPoi]: l'ultima
    // destinazione per cui abbiamo già inquadrato posizione+destinazione,
    // così l'inquadratura avviene una volta sola quando si avvia la
    // navigazione verso un luogo (o si cambia luogo), non ad ogni fix GPS —
    // segnalato su dispositivo reale (agosto 2026): senza un percorso reale
    // disegnato come linea, la camera non si spostava mai per includere la
    // destinazione, che poteva restare fuori dall'inquadratura dando
    // l'impressione che la navigazione non avesse fatto nulla.
    val lastFittedPoi = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Ultimo valore di recenterRequest già gestito: un semplice contatore che
    // il chiamante incrementa ad ogni tap sul pulsante "Ricentra". Confrontarlo
    // con l'ultimo valore visto è il modo standard in Compose per reagire a un
    // "evento" (non a un valore continuo) dentro la callback update di AndroidView.
    val lastHandledRecenterRequest = remember { mutableStateOf(0) }
    // Vero da quando l'utente ha spostato/zoomato la mappa con un gesto,
    // finché non tocca "Ricentra" (o non cambia tracciato/destinazione).
    // Mentre è vero, lo zoom automatico (autoZoomIn) resta sospeso — vedi il
    // commento sopra la funzione per il bug che questo flag risolve.
    val userIsPanning = remember { mutableStateOf(false) }

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
                    // Distingue un movimento della camera causato da un gesto
                    // dell'utente (pan/pinch-zoom) da uno causato dal nostro
                    // stesso codice (easeCamera/moveCamera qui sotto): solo il
                    // primo deve sospendere lo zoom automatico, vedi
                    // userIsPanning sopra.
                    maplibreMap.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            userIsPanning.value = true
                        }
                    }
                    maplibreMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                        if (track != null) {
                            addTrackLayer(style, track)
                            fitCameraToTrack(maplibreMap, track)
                            lastFittedTrack.value = track
                        } else if (focusOnPoi != null) {
                            // Nessuna linea da disegnare (nessun percorso reale
                            // ancora disponibile), ma c'è comunque una
                            // destinazione da raggiungere: inquadra posizione
                            // corrente + destinazione, altrimenti il marker
                            // potrebbe restare fuori schermo.
                            fitCameraToPoints(maplibreMap, listOf(LatLng(currentLat, currentLon), LatLng(focusOnPoi.first, focusOnPoi.second)))
                            lastFittedPoi.value = focusOnPoi
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
                        if (showCurrentPosition) {
                            // Aggiunta DOPO il tracciato apposta: i layer più
                            // recenti si disegnano sopra ai precedenti, quindi la
                            // freccia/il cerchio di posizione restano sempre ben
                            // visibili anche nei punti in cui il percorso ci
                            // passa esattamente sotto — prima erano sotto alla
                            // linea e potevano risultare parzialmente coperti.
                            if (navigationBearingDegrees != null) {
                                addNavigationArrowLayer(style, currentLat, currentLon, navigationBearingDegrees)
                            } else {
                                addPositionLayer(style, currentLat, currentLon)
                            }
                        }
                        if (waypoints.isNotEmpty()) {
                            // Aggiunta per ultima: disegnata sopra a tutto il
                            // resto, altrimenti i pallini rischierebbero di
                            // restare nascosti nei punti in cui la linea ci passa sopra.
                            addWaypointsLayer(style, waypoints)
                        }
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { maplibreMap ->
                val style = maplibreMap.style ?: return@getMapAsync
                // Stesso ordine della creazione iniziale (vedi factory sopra):
                // tracciato, poi posizione/freccia, poi waypoint — così, anche
                // se un layer viene tolto e riaggiunto qui (es. un GPX caricato
                // dopo l'apertura della schermata), la freccia resta comunque
                // sopra alla linea del percorso, non sotto.
                syncTrackLayer(style, track)
                if (showCurrentPosition) {
                    syncPositionAndArrowLayers(style, currentLat, currentLon, navigationBearingDegrees)
                }
                syncWaypointsLayer(style, waypoints)

                if (track != null && track != lastFittedTrack.value) {
                    // Il tracciato è cambiato (nuovo GPX caricato, o rimosso e
                    // ricaricato): inquadra il nuovo percorso per intero. Un
                    // nuovo percorso è una scelta esplicita dell'utente, quindi
                    // ha senso che la camera "salti" anche se stava scorrendo
                    // manualmente la mappa un attimo prima — resettiamo anche
                    // userIsPanning, per lo stesso motivo del tap su "Ricentra".
                    fitCameraToTrack(maplibreMap, track)
                    lastFittedTrack.value = track
                    lastFittedPoi.value = null
                    userIsPanning.value = false
                } else if (track == null) {
                    lastFittedTrack.value = null
                    if (focusOnPoi != null && focusOnPoi != lastFittedPoi.value) {
                        // Nuova destinazione senza percorso reale (o percorso
                        // reale non ancora arrivato): inquadra posizione +
                        // destinazione una volta sola, non ad ogni fix GPS
                        // (che farebbe "saltare" continuamente la camera, dato
                        // che currentLat/currentLon cambiano di continuo).
                        fitCameraToPoints(maplibreMap, listOf(LatLng(currentLat, currentLon), LatLng(focusOnPoi.first, focusOnPoi.second)))
                        lastFittedPoi.value = focusOnPoi
                        userIsPanning.value = false
                    } else if (focusOnPoi == null) {
                        lastFittedPoi.value = null
                    }
                }

                if (autoZoomIn && !userIsPanning.value) {
                    easeCameraToPosition(maplibreMap, currentLat, currentLon, ZOOM_DETAIL)
                }

                if (recenterRequest != lastHandledRecenterRequest.value) {
                    // Ricentraggio manuale (pulsante "Ricentra"): stesso livello di
                    // zoom "di dettaglio" usato durante la navigazione attiva se un
                    // percorso è caricato, altrimenti quello "d'insieme" di partenza.
                    // Riprende anche lo zoom automatico sospeso sopra (userIsPanning).
                    val zoom = if (track != null) ZOOM_DETAIL else ZOOM_OVERVIEW
                    easeCameraToPosition(maplibreMap, currentLat, currentLon, zoom)
                    lastHandledRecenterRequest.value = recenterRequest
                    userIsPanning.value = false
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

// Vedi data/maps/MapStyle.kt: unico posto con l'URL, condiviso anche con il
// download offline (OfflineMapManager).
private val MAP_STYLE_URL = MapStyle.URL
private const val SOURCE_TRACK = "gm-trekking-track-source"
private const val LAYER_TRACK = "gm-trekking-track-layer"
private const val SOURCE_POSITION = "gm-trekking-position-source"
private const val LAYER_POSITION = "gm-trekking-position-layer"
private const val SOURCE_WAYPOINTS = "gm-trekking-waypoints-source"
private const val LAYER_WAYPOINTS = "gm-trekking-waypoints-layer"
private const val SOURCE_NAV_ARROW = "gm-trekking-nav-arrow-source"
private const val LAYER_NAV_ARROW = "gm-trekking-nav-arrow-layer"
private const val IMAGE_NAV_ARROW = "gm-trekking-nav-arrow-icon"

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

private fun waypointsFeatureCollection(waypoints: List<Pair<Double, Double>>): FeatureCollection {
    val features = waypoints.map { (lat, lon) -> Feature.fromGeometry(Point.fromLngLat(lon, lat)) }
    return FeatureCollection.fromFeatures(features)
}

private fun addWaypointsLayer(style: Style, waypoints: List<Pair<Double, Double>>) {
    style.addSource(GeoJsonSource(SOURCE_WAYPOINTS, waypointsFeatureCollection(waypoints)))
    style.addLayer(
        CircleLayer(LAYER_WAYPOINTS, SOURCE_WAYPOINTS).withProperties(
            PropertyFactory.circleColor("#E08A00"),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        )
    )
}

/** Idempotente come syncTrackLayer: sicura da chiamare ad ogni ricomposizione. */
private fun syncWaypointsLayer(style: Style, waypoints: List<Pair<Double, Double>>) {
    val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_WAYPOINTS)

    if (waypoints.isEmpty()) {
        if (existingSource != null) {
            style.removeLayer(LAYER_WAYPOINTS)
            style.removeSource(SOURCE_WAYPOINTS)
        }
        return
    }

    if (existingSource != null) {
        existingSource.setGeoJson(waypointsFeatureCollection(waypoints))
    } else {
        addWaypointsLayer(style, waypoints)
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

/**
 * Sceglie tra il cerchio "posizione" e la freccia di navigazione a seconda
 * che [navigationBearingDegrees] sia presente, aggiungendo/rimuovendo i
 * layer secondo necessità — chiamata ad ogni ricomposizione dalla `update`
 * di AndroidView, quindi deve restare sicura da richiamare ripetutamente
 * (idempotente), come syncTrackLayer/syncWaypointsLayer.
 */
private fun syncPositionAndArrowLayers(
    style: Style,
    lat: Double,
    lon: Double,
    navigationBearingDegrees: Double?,
) {
    if (navigationBearingDegrees != null) {
        removePositionCircleLayer(style)
        syncNavigationArrowLayer(style, lat, lon, navigationBearingDegrees)
    } else {
        removeNavigationArrowLayer(style)
        syncPositionCircleLayer(style, lat, lon)
    }
}

private fun syncPositionCircleLayer(style: Style, lat: Double, lon: Double) {
    val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
    if (existingSource != null) {
        existingSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    } else {
        addPositionLayer(style, lat, lon)
    }
}

private fun removePositionCircleLayer(style: Style) {
    if (style.getSourceAs<GeoJsonSource>(SOURCE_POSITION) != null) {
        style.removeLayer(LAYER_POSITION)
        style.removeSource(SOURCE_POSITION)
    }
}

/**
 * Freccia di navigazione ancorata alle coordinate GPS reali (richiesto
 * esplicitamente, agosto 2026, al posto del blocco fisso sotto la mappa):
 * un'icona disegnata a runtime (nessuna risorsa immagine nel progetto,
 * bitmap generata via Canvas — vedi createArrowBitmap), ruotata secondo
 * [bearingDegrees] tramite la proprietà data-driven `iconRotate` di
 * SymbolLayer. [bearingDegrees] è già calcolato dal chiamante
 * (MainMapScreen.kt) rispetto al senso di marcia, non al nord: qui viene
 * solo applicato, senza ulteriore interpretazione.
 */
private fun addNavigationArrowLayer(style: Style, lat: Double, lon: Double, bearingDegrees: Double) {
    style.addImage(IMAGE_NAV_ARROW, createArrowBitmap())
    style.addSource(GeoJsonSource(SOURCE_NAV_ARROW, Feature.fromGeometry(Point.fromLngLat(lon, lat))))
    style.addLayer(
        SymbolLayer(LAYER_NAV_ARROW, SOURCE_NAV_ARROW).withProperties(
            PropertyFactory.iconImage(IMAGE_NAV_ARROW),
            PropertyFactory.iconRotate(bearingDegrees.toFloat()),
            // Senza questi due, l'icona rischierebbe di non essere disegnata
            // affatto: di default i SymbolLayer partecipano al sistema
            // "anti-sovrapposizione" delle etichette della mappa (pensato per
            // i nomi di città/vie), che qui non ha senso per un singolo
            // indicatore sempre visibile.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    )
}

private fun syncNavigationArrowLayer(style: Style, lat: Double, lon: Double, bearingDegrees: Double) {
    val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_NAV_ARROW)
    if (existingSource != null) {
        existingSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        (style.getLayer(LAYER_NAV_ARROW) as? SymbolLayer)?.setProperties(
            PropertyFactory.iconRotate(bearingDegrees.toFloat()),
        )
    } else {
        addNavigationArrowLayer(style, lat, lon, bearingDegrees)
    }
}

private fun removeNavigationArrowLayer(style: Style) {
    if (style.getSourceAs<GeoJsonSource>(SOURCE_NAV_ARROW) != null) {
        style.removeLayer(LAYER_NAV_ARROW)
        style.removeSource(SOURCE_NAV_ARROW)
    }
}

/**
 * Disegna una freccia (non un triangolo pieno: la base ha un rientro
 * centrale, forma più leggibile a colpo d'occhio su una mappa) rossa con
 * bordo bianco, a runtime via Canvas — evita di aggiungere una risorsa
 * immagine al progetto solo per questa icona. Rosso scelto deliberatamente
 * (richiesto esplicitamente): deve risaltare rispetto ai colori naturali
 * della mappa (verdi dei boschi, azzurri dell'acqua), non mimetizzarsi come
 * farebbe invece il verde già usato per tracciato/posizione in
 * quest'app.
 */
private fun createArrowBitmap(): Bitmap {
    val sizePx = 140
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val path = Path().apply {
        moveTo(sizePx * 0.5f, sizePx * 0.05f)
        lineTo(sizePx * 0.85f, sizePx * 0.9f)
        lineTo(sizePx * 0.5f, sizePx * 0.65f)
        lineTo(sizePx * 0.15f, sizePx * 0.9f)
        close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.06f
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)
    return bitmap
}

private fun fitCameraToTrack(map: MapLibreMap, track: GpxTrack) {
    val boundsBuilder = LatLngBounds.Builder()
    track.points.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
    }
}

/**
 * Come [fitCameraToTrack], ma per un insieme di punti qualunque invece che
 * per un [GpxTrack] — usata per inquadrare posizione corrente + destinazione
 * quando si naviga verso un luogo utile senza (ancora) un percorso reale da
 * disegnare come linea (vedi [focusOnPoi] sopra).
 */
private fun fitCameraToPoints(map: MapLibreMap, points: List<LatLng>) {
    val boundsBuilder = LatLngBounds.Builder()
    points.forEach { boundsBuilder.include(it) }
    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
    }
}
