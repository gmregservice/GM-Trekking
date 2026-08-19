package com.gmtrekking.app.data.navigation

import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.routing.RoutingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Contenitore in memoria per la navigazione verso un singolo "luogo utile"
 * (punto 6 dei "Richiesta utente da sviluppare" in docs/PIANO_SVILUPPO.md),
 * distinto da [com.gmtrekking.app.data.gpx.CurrentTrackHolder] che tiene
 * invece il percorso GPX caricato come guida.
 *
 * Tenerli separati (invece di forzare la destinazione dentro un GpxTrack
 * salvato in CurrentTrackHolder) permette a MainMapScreen di sapere sempre
 * se un eventuale percorso GPX caricato è ancora quello "vero" dell'utente:
 * avviare la navigazione verso un luogo utile non lo sovrascrive né lo perde,
 * lo mette solo temporaneamente in secondo piano — terminata la navigazione
 * verso il luogo (svuotando [target]), la navigazione sul percorso GPX
 * originale (se presente) riprende automaticamente, senza bisogno di
 * ricaricare nulla.
 */
object PoiNavigationHolder {

    /**
     * Destinazione verso cui navigare: nome del luogo (per la UI), coordinate,
     * e [routePoints] — percorso reale su sentieri/strade calcolato da
     * OpenRouteService (data/routing/RoutingRepository.kt), quando disponibile.
     *
     * [routePoints] parte sempre `null` (impostato da PlacesScreen.kt
     * all'avvio della navigazione, PRIMA che la richiesta di rete completi:
     * la navigazione parte subito con la linea retta di riserva, senza
     * aspettare) e viene aggiornato in un secondo momento se/quando il
     * percorso reale arriva — se l'utente non ha configurato una chiave API
     * in Impostazioni, o la richiesta fallisce, resta `null` per sempre e si
     * continua a usare la linea retta: mai bloccare la navigazione per un
     * servizio esterno facoltativo.
     */
    data class Target(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val routePoints: List<TrackPoint>? = null,
    )

    val target = MutableStateFlow<Target?>(null)

    private val routingRepository = RoutingRepository()

    // Scope proprio, non legato al ciclo di vita di una schermata Compose:
    // la richiesta di instradamento viene avviata da PlacesScreen.kt, ma
    // l'utente torna subito alla mappa principale (onBack()) — se si usasse
    // lo scope di quella schermata (rememberCoroutineScope()), verrebbe
    // annullato non appena PlacesScreen viene smontata, quasi certamente
    // prima che la richiesta di rete abbia il tempo di completare.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Avvia la navigazione verso il luogo ([name]/[latitude]/[longitude]).
     * Se [apiKey] e la posizione di partenza sono disponibili, prova anche a
     * calcolare in background un percorso reale su sentieri/strade
     * (OpenRouteService, profilo escursionistico) e ad aggiornare
     * [target] con quel percorso quando/se arriva — senza bloccare l'avvio
     * della navigazione, che parte subito con la linea retta di riserva
     * (calcolata a parte in MainMapScreen.kt finché [Target.routePoints]
     * resta `null`).
     */
    fun start(
        name: String,
        latitude: Double,
        longitude: Double,
        startLat: Double?,
        startLon: Double?,
        apiKey: String?,
    ) {
        target.value = Target(name, latitude, longitude)

        if (apiKey == null || startLat == null || startLon == null) return
        scope.launch {
            val route = routingRepository.findHikingRoute(apiKey, startLat, startLon, latitude, longitude)
            if (route == null) return@launch
            // Solo se l'utente non ha già cambiato/terminato la navigazione
            // nel frattempo (es. tornato indietro, scelto un altro luogo):
            // evita di "resuscitare" una navigazione già chiusa o di
            // sovrascrivere quella di un luogo diverso nel frattempo scelto.
            val current = target.value
            if (current != null && current.latitude == latitude && current.longitude == longitude) {
                target.value = current.copy(routePoints = route)
            }
        }
    }
}
