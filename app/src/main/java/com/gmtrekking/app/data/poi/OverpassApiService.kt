package com.gmtrekking.app.data.poi

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Client per Overpass API (dati OpenStreetMap): gratuita, nessuna chiave richiesta.
 * https://wiki.openstreetmap.org/wiki/Overpass_API
 *
 * La risposta viene letta come stringa grezza (ScalarsConverterFactory) e poi
 * decodificata manualmente con kotlinx.serialization nei repository (Luoghi
 * utili e Sentieri vicini): evita di legare il servizio Retrofit a un parser
 * specifico, più semplice da testare.
 *
 * Un'istanza di questa interfaccia parla con UN SOLO mirror (quello passato a
 * [create]): la logica di tentare più mirror in sequenza se il primo fallisce
 * vive in [OverpassQueryExecutor], non qui.
 */
interface OverpassApiService {

    // User-Agent descrittivo e Accept espliciti: necessari perché l'endpoint
    // ufficiale (overpass-api.de) filtra come "traffico da bot" le richieste
    // con lo User-Agent generico di default di OkHttp e senza un Accept
    // esplicito, rispondendo con HTTP 406 Not Acceptable — bug reale
    // riscontrato su dispositivo (agosto 2026), confermato come problema noto
    // e attuale della piattaforma (non una richiesta malformata da parte
    // nostra: https://github.com/drolbr/Overpass-API/issues/791). Vale per
    // tutti i mirror, non solo per overpass-api.de.
    @Headers(
        "Accept: application/json",
        "User-Agent: GMTrekking/1.0 (Android; https://github.com/gmregservice/GM-Trekking)",
    )
    @FormUrlEncoded
    @POST("interpreter")
    suspend fun query(@Field("data") overpassQl: String): String

    companion object {
        /**
         * Mirror pubblici Overpass API, in ordine di preferenza: il primo è
         * quello provato per primo, i successivi sono di riserva se il
         * precedente fallisce o va in timeout (vedi [OverpassQueryExecutor]).
         *
         * Aggiunto dopo che sia "Luoghi utili" sia "Sentieri vicini"
         * mostravano fallimenti frequenti affidandosi a un unico mirror
         * senza alcuna riserva (segnalato dall'utente, agosto 2026).
         *
         * Ordine stabilito dai dati ufficiali della pagina "Overpass API" del
         * wiki OpenStreetMap (agosto 2026:
         * https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances),
         * combinati con l'esperienza reale già fatta in questo stesso
         * progetto. Non è stato possibile misurare la latenza reale dei
         * mirror dall'ambiente usato per scrivere questo codice (nessun
         * accesso di rete a domini esterni dal sandbox di sviluppo, stesso
         * limite già incontrato per altre verifiche in questo progetto): la
         * classifica si basa quindi su hardware/policy dichiarati e
         * sull'esperienza reale, non su una misura diretta di velocità — una
         * scelta comunque ragionata è meglio di un singolo punto di
         * fallimento.
         */
        val BASE_URLS: List<String> = listOf(
            // Ex overpass.kumi.systems (stesso gestore, dominio rinominato
            // nel 2026): 4 server, 20 core e 256 GB RAM ciascuno, nessun
            // limite di richieste dichiarato dal gestore. È il mirror già in
            // uso in questo progetto dalla v1.6 (con il vecchio dominio),
            // con buoni risultati a parte i timeout/sovraccarichi occasionali
            // che hanno reso necessario questo fallback multi-mirror.
            "https://overpass.private.coffee/api/",
            // Istanza ufficiale principale, gestita da FOSSGIS: 2 server, 16
            // core e 128 GB RAM ciascuno, quota dichiarata generosa (circa
            // 10.000 richieste e 1 GB al giorno, ben oltre l'uso normale di
            // quest'app). Filtrava in passato come "traffico da bot" le
            // richieste senza header espliciti (HTTP 406, v1.6) — mitigato
            // dallo User-Agent/Accept già inviati su ogni richiesta (vedi
            // sopra). Usata come riserva, non come mirror principale, per
            // prudenza dopo quel precedente.
            "https://overpass-api.de/api/",
            // Mirror russo (VK Maps): hardware dichiarato ancora più forte (2
            // server, 56 core e 384 GB RAM ciascuno), nessun limite di
            // richieste dichiarato. Ultima risorsa nell'elenco per la
            // latenza potenzialmente più alta dall'Europa rispetto ai primi
            // due, non per dubbi sull'affidabilità del servizio in sé.
            "https://maps.mail.ru/osm/tools/overpass/api/",
        )

        /**
         * Crea un client per un singolo mirror. [baseUrl] di default è il
         * primo di [BASE_URLS] (il preferito); [OverpassQueryExecutor] ne
         * crea uno per ciascun mirror dell'elenco.
         */
        fun create(baseUrl: String = BASE_URLS.first()): OverpassApiService {
            // Timeout più lunghi del default di OkHttp (10s): le query Overpass
            // dichiarano esse stesse "[timeout:25]" lato server (vedi
            // OverpassQueryBuilder/TrailQueryBuilder) — con il default di 10s,
            // OkHttp abbandonerebbe la richiesta (SocketTimeoutException) ben
            // prima che il server abbia il tempo di rispondere, anche quando la
            // query andrebbe a buon fine. Bug reale riscontrato su dispositivo
            // (agosto 2026). 35s lascia un margine oltre i 25s dichiarati nella
            // query, per qualunque mirror.
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(OverpassApiService::class.java)
        }
    }
}
