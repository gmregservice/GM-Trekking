package com.gmtrekking.app.data.navigation

import com.gmtrekking.app.data.gpx.TrackPoint
import com.gmtrekking.app.data.routing.RoutingOutcome
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
     * all'avvio della navigazione, PRIMA che la richiesta di rete completi) e
     * viene aggiornato in un secondo momento se/quando il percorso reale
     * arriva. **Nessuna linea retta di riserva** (cambiato in v1.36, richiesto
     * esplicitamente: in un ambiente sconosciuto/potenzialmente ostile una
     * guida in linea d'aria può indicare di attraversare un ostacolo reale —
     * es. un torrente dove un sentiero vero passerebbe da un ponte poco
     * distante): finché [routePoints] resta `null` (richiesta in corso,
     * chiave API non configurata, o richiesta fallita), `MainMapScreen.kt`
     * non calcola né mostra alcuna freccia/distanza, solo un messaggio
     * esplicito che spiega perché manca una guida (vedi [RoutingStatus]).
     */
    data class Target(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val routePoints: List<TrackPoint>? = null,
    )

    /**
     * Stato del tentativo di calcolo del percorso reale per la destinazione
     * corrente — aggiunto (v1.28) dopo una segnalazione su dispositivo reale:
     * con `routePoints` semplicemente `null` non c'era modo di distinguere
     * "chiave non configurata" da "richiesta fallita" da "ancora in corso",
     * quindi nessun modo di capire perché non compariva alcuna indicazione
     * sulla mappa. `MainMapScreen.kt` lo mostra a schermo (stesso principio
     * già usato per gli errori di Overpass in `PlacesViewModel.kt`) — da v1.36
     * non più solo informativo: finché questo stato non è `Success`, non
     * viene mostrata alcuna freccia/distanza (niente più linea retta di
     * riserva), quindi questo testo è l'unica indicazione di navigazione
     * disponibile per l'utente in quei casi.
     */
    sealed class RoutingStatus {
        object Loading : RoutingStatus()
        object Success : RoutingStatus()
        object NoApiKey : RoutingStatus()
        data class Failure(val detail: String) : RoutingStatus()
    }

    val target = MutableStateFlow<Target?>(null)
    val routingStatus = MutableStateFlow<RoutingStatus?>(null)

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
     * (OpenRouteService, profilo escursionistico) e ad aggiornare [target]
     * con quel percorso quando/se arriva. [target] viene impostato subito,
     * ma senza una guida attiva (nessuna linea retta di riserva, vedi
     * [Target.routePoints]) finché il percorso reale non arriva — l'utente
     * vede subito "Stai andando verso: ..." più un messaggio di stato
     * (`MainMapScreen.kt`), non una freccia calcolata su una retta.
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

        if (apiKey == null || startLat == null || startLon == null) {
            routingStatus.value = RoutingStatus.NoApiKey
            return
        }
        routingStatus.value = RoutingStatus.Loading
        scope.launch {
            when (val outcome = routingRepository.findHikingRoute(apiKey, startLat, startLon, latitude, longitude)) {
                is RoutingOutcome.Success -> {
                    // Solo se l'utente non ha già cambiato/terminato la
                    // navigazione nel frattempo (es. tornato indietro, scelto
                    // un altro luogo): evita di "resuscitare" una navigazione
                    // già chiusa o di sovrascrivere quella di un luogo diverso
                    // nel frattempo scelto.
                    val current = target.value
                    if (current != null && current.latitude == latitude && current.longitude == longitude) {
                        target.value = current.copy(routePoints = outcome.points)
                        routingStatus.value = RoutingStatus.Success
                    }
                }
                is RoutingOutcome.Failure -> {
                    routingStatus.value = RoutingStatus.Failure(outcome.detail)
                }
            }
        }
    }
}
