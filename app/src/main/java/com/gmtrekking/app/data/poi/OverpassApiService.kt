package com.gmtrekking.app.data.poi

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Client per Overpass API (dati OpenStreetMap): gratuita, nessuna chiave richiesta.
 * https://wiki.openstreetmap.org/wiki/Overpass_API
 *
 * La risposta viene letta come stringa grezza (ScalarsConverterFactory) e poi
 * decodificata manualmente con kotlinx.serialization in PoiRepository: evita di
 * legare il servizio Retrofit a un parser specifico, più semplice da testare.
 */
interface OverpassApiService {

    // User-Agent descrittivo e Accept espliciti: necessari perché l'endpoint
    // principale (overpass-api.de) filtra come "traffico da bot" le richieste
    // con lo User-Agent generico di default di OkHttp e senza un Accept
    // esplicito, rispondendo con HTTP 406 Not Acceptable — bug reale
    // riscontrato su dispositivo (agosto 2026), confermato come problema noto
    // e attuale della piattaforma (non una richiesta malformata da parte
    // nostra: https://github.com/drolbr/Overpass-API/issues/791).
    @Headers(
        "Accept: application/json",
        "User-Agent: GMTrekking/1.0 (Android; https://github.com/gmregservice/GM-Trekking)",
    )
    @FormUrlEncoded
    @POST("interpreter")
    suspend fun query(@Field("data") overpassQl: String): String

    companion object {
        // Endpoint: overpass.kumi.systems invece del principale overpass-api.de.
        // Il principale è sempre più aggressivo nel bloccare con HTTP 406 le
        // richieste che sembrano automatizzate (misura anti-scraping AI),
        // colpendo anche client legittimi come questo. Questo mirror
        // pubblico non applica gli stessi filtri, resta gratuito e senza
        // chiave richiesta, stesso protocollo (POST su /interpreter).
        const val BASE_URL = "https://overpass.kumi.systems/api/"

        fun create(): OverpassApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(OverpassApiService::class.java)
        }
    }
}
