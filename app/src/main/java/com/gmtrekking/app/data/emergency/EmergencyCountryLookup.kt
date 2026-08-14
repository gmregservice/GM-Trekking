package com.gmtrekking.app.data.emergency

/**
 * Rilevamento OFFLINE del paese in cui ci si trova, per mostrare in pagina
 * "Emergenza" i numeri di soccorso locali oltre al 112 — richiesto
 * esplicitamente (agosto 2026, vedi punto 7 in docs/PIANO_SVILUPPO.md), con
 * il vincolo che deve funzionare senza connessione (in montagna il dato
 * mobile spesso manca proprio quando servirebbe di più).
 *
 * **Semplificazione deliberata rispetto al piano originale**: il piano
 * ipotizzava un confronto punto-poligono con i confini nazionali reali. Non
 * è stato possibile scaricare un dataset di confini offline in questo
 * ambiente di sviluppo (nessun accesso di rete a GitHub/CDN dal sandbox usato
 * per scrivere il codice), quindi qui si usa un rettangolo (bounding box)
 * approssimativo per ciascun paese invece del confine reale. Vicino a un
 * confine tra due paesi il rilevamento può quindi sbagliare (es. un punto in
 * territorio francese ma dentro il rettangolo svizzero verrebbe segnalato
 * come Svizzera). Impatto limitato: il **112 resta sempre il numero
 * principale mostrato**, funziona ovunque in Europa a prescindere dal paese
 * rilevato — i numeri "supplementari" locali qui sotto sono un'informazione
 * aggiuntiva, non l'unica via di soccorso. Se in futuro sarà disponibile un
 * dataset di confini reali da includere come risorsa dell'app, questa
 * classe è il solo punto da sostituire (l'interfaccia pubblica,
 * [localNumbersFor], non cambierebbe).
 *
 * Quando più rettangoli si sovrappongono (es. Svizzera dentro l'area più
 * ampia di Francia/Germania/Italia), vince quello con l'area più piccola:
 * euristica semplice ma efficace, dato che i paesi piccoli citati qui hanno
 * quasi sempre un'area molto minore dei vicini più grandi.
 *
 * Numeri verificati (agosto 2026) da fonti pubbliche sui numeri di emergenza
 * europei; copre i paesi più rilevanti per l'escursionismo in Europa.
 * Se il punto non ricade in nessun rettangolo noto, si mostra solo il 112.
 */
object EmergencyCountryLookup {

    private data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    ) {
        fun contains(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon
        val area: Double get() = (maxLat - minLat) * (maxLon - minLon)
    }

    private data class CountryProfile(
        val displayName: String,
        val bounds: Bounds,
        val police: String? = null,
        val ambulance: String? = null,
        val fire: String? = null,
    )

    private val countries = listOf(
        CountryProfile("Austria", Bounds(46.4, 49.0, 9.5, 17.2), police = "133", ambulance = "144", fire = "122"),
        CountryProfile("Belgio", Bounds(49.5, 51.5, 2.5, 6.4), police = "101", ambulance = "100", fire = "100"),
        CountryProfile("Croazia", Bounds(42.4, 46.5, 13.5, 19.4), police = "192", ambulance = "194", fire = "193"),
        CountryProfile("Cechia", Bounds(48.5, 51.1, 12.1, 18.9), police = "158", ambulance = "155", fire = "150"),
        CountryProfile("Danimarca", Bounds(54.5, 57.8, 8.0, 15.2)),
        CountryProfile("Finlandia", Bounds(59.7, 70.1, 20.5, 31.6)),
        CountryProfile("Francia", Bounds(41.3, 51.1, -5.2, 9.6), police = "17", ambulance = "15", fire = "18"),
        CountryProfile("Germania", Bounds(47.3, 55.1, 5.9, 15.0), police = "110"),
        CountryProfile("Grecia", Bounds(34.8, 41.8, 19.3, 29.7), police = "100", ambulance = "166", fire = "199"),
        CountryProfile("Ungheria", Bounds(45.7, 48.6, 16.1, 22.9), police = "107", ambulance = "104", fire = "105"),
        CountryProfile("Irlanda", Bounds(51.4, 55.4, -10.5, -6.0), police = "999", ambulance = "999", fire = "999"),
        CountryProfile("Italia", Bounds(36.5, 47.1, 6.6, 18.6), police = "113", ambulance = "118", fire = "115"),
        CountryProfile("Paesi Bassi", Bounds(50.7, 53.6, 3.3, 7.2)),
        CountryProfile("Norvegia", Bounds(57.9, 71.2, 4.5, 31.3), ambulance = "113", fire = "110"),
        CountryProfile("Polonia", Bounds(49.0, 54.9, 14.1, 24.2), police = "997", ambulance = "999", fire = "998"),
        CountryProfile("Portogallo", Bounds(36.9, 42.2, -9.5, -6.2)),
        CountryProfile("Slovacchia", Bounds(47.7, 49.6, 16.8, 22.6), police = "158", ambulance = "155", fire = "150"),
        CountryProfile("Slovenia", Bounds(45.4, 46.9, 13.4, 16.6), police = "113"),
        CountryProfile("Spagna", Bounds(36.0, 43.8, -9.3, 3.3), police = "091", fire = "080"),
        CountryProfile("Svezia", Bounds(55.3, 69.1, 11.1, 24.2)),
        CountryProfile("Svizzera", Bounds(45.8, 47.9, 5.9, 10.5), police = "117", ambulance = "144", fire = "118"),
        CountryProfile("Regno Unito", Bounds(49.9, 60.9, -8.6, 1.8), police = "999", ambulance = "999", fire = "999"),
    )

    private const val EUROPEAN_EMERGENCY_NUMBER = "112"

    /**
     * Numeri da mostrare in pagina "Emergenza" per il punto ([lat],[lon]) dato:
     * sempre il 112 per primo, seguito dagli eventuali numeri locali
     * supplementari del paese rilevato (nessuno se il punto non ricade in
     * nessun rettangolo noto, o se nel paese rilevato si usa comunque solo
     * il 112 per tutti i servizi — es. Danimarca, Svezia, Portogallo).
     */
    fun localNumbersFor(lat: Double, lon: Double): List<EmergencyNumberEntry> {
        val entries = mutableListOf(EmergencyNumberEntry("Numero unico d'emergenza europeo", EUROPEAN_EMERGENCY_NUMBER))

        val country = countries
            .filter { it.bounds.contains(lat, lon) }
            .minByOrNull { it.bounds.area }
            ?: return entries

        // Raggruppa i servizi per numero (es. Irlanda: polizia/ambulanza/vigili
        // del fuoco condividono lo stesso 999), scartando quelli già coperti dal
        // 112 unificato — LinkedHashMap (mutableMapOf di Kotlin) mantiene
        // l'ordine di inserimento, quindi l'ordine mostrato resta prevedibile.
        val byNumber = mutableMapOf<String, MutableList<String>>()
        country.police?.let { if (it != EUROPEAN_EMERGENCY_NUMBER) byNumber.getOrPut(it) { mutableListOf() }.add("Polizia") }
        country.ambulance?.let { if (it != EUROPEAN_EMERGENCY_NUMBER) byNumber.getOrPut(it) { mutableListOf() }.add("Ambulanza") }
        country.fire?.let { if (it != EUROPEAN_EMERGENCY_NUMBER) byNumber.getOrPut(it) { mutableListOf() }.add("Vigili del fuoco") }

        byNumber.forEach { (number, roles) ->
            entries += EmergencyNumberEntry("${roles.joinToString(" / ")} (${country.displayName})", number)
        }

        return entries
    }
}
