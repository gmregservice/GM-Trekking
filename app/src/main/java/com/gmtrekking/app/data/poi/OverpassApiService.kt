package com.gmtrekking.app.data.poi

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun query(@Field("data") overpassQl: String): String

    companion object {
        // Endpoint pubblico principale. In caso di indisponibilità, Overpass
        // pubblica endpoint alternativi (es. overpass.kumi.systems) che si
        // possono aggiungere qui in futuro con un semplice fallback.
        const val BASE_URL = "https://overpass-api.de/api/"

        fun create(): OverpassApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(OverpassApiService::class.java)
        }
    }
}
