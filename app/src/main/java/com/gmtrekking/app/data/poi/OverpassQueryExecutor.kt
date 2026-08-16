package com.gmtrekking.app.data.poi

/**
 * Esegue una query Overpass QL provando in sequenza tutti i mirror di
 * [OverpassApiService.BASE_URLS], fermandosi al primo che risponde con
 * successo. Condiviso da `PoiRepository` (Luoghi utili) e `TrailRepository`
 * (Sentieri vicini): entrambe le funzionalità si appoggiavano finora a un
 * unico mirror fisso, senza alcun tentativo di riserva — se quel singolo
 * server pubblico era sovraccarico o temporaneamente irraggiungibile,
 * l'unico risultato possibile era l'errore mostrato in UI. Segnalato
 * dall'utente come un fallimento frequente per entrambe le schermate
 * (agosto 2026).
 *
 * **Un solo tentativo per mirror, non ripetuto sullo stesso**: un server
 * pubblico sovraccarico fallisce quasi sempre di nuovo allo stesso modo
 * pochi secondi dopo, quindi ripetere sullo stesso mirror allungherebbe
 * l'attesa peggiore (fino al timeout di 35s per tentativo) senza un
 * beneficio reale — meglio passare subito al mirror successivo. Con tre
 * mirror l'attesa peggiore resta comunque delimitata (al più circa 3×35s se
 * tutti e tre non rispondessero affatto, scenario improbabile: un server
 * davvero sovraccarico o bloccato di solito risponde in fretta con un
 * errore, non resta silenzioso fino al timeout).
 *
 * A questo si aggiunge, lato schermata (`PlacesScreen.kt`/
 * `NearbyTrailsScreen.kt`), un pulsante "Riprova" per un nuovo tentativo
 * manuale completo (tutti i mirror di nuovo) senza dover uscire e rientrare.
 */
object OverpassQueryExecutor {

    private val services: List<OverpassApiService> =
        OverpassApiService.BASE_URLS.map { url -> OverpassApiService.create(url) }

    suspend fun execute(query: String): String {
        var lastError: Throwable? = null
        for (service in services) {
            try {
                return service.query(query)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Nessun mirror Overpass disponibile.")
    }
}
