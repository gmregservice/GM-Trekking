package com.gmtrekking.app.data.routing

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Client per l'endpoint Directions di OpenRouteService (instradamento reale
 * su sentieri/strade, non una linea retta — vedi RoutingRepository.kt e la
 * sezione "Instradamento reale" in docs/PIANO_SVILUPPO.md).
 *
 * **Dominio**: `api.heigit.org`, non `api.openrouteservice.org` — quest'ultimo
 * è stato deprecato dal team OpenRouteService (annuncio del 28 aprile 2026)
 * con spegnimento definitivo previsto per il 24 agosto 2026, quindi non più
 * un'opzione valida nel momento in cui questa funzione è stata scritta
 * (segnalato dall'utente, agosto 2026). La chiave API resta la stessa su
 * entrambi i domini.
 *
 * **Risposta letta come stringa grezza** (ScalarsConverterFactory), poi
 * decodificata manualmente con kotlinx.serialization in RoutingRepository.kt
 * — stesso pattern già usato per Overpass API (vedi
 * data/poi/OverpassApiService.kt), per non legare il servizio Retrofit a un
 * parser specifico.
 */
interface RoutingApiService {

    // "/geojson" nel path: forza la risposta in formato GeoJSON standard
    // (RoutingResponse.kt), più semplice da leggere della codifica compatta
    // usata di default dall'endpoint senza questo suffisso (una polyline
    // codificata come stringa, che richiederebbe un decoder dedicato).
    @Headers("Accept: application/json")
    @POST("v2/directions/{profile}/geojson")
    suspend fun route(
        @Path("profile") profile: String,
        @Header("Authorization") apiKey: String,
        @Body body: RequestBody,
    ): String

    companion object {
        private const val BASE_URL = "https://api.heigit.org/openrouteservice/"

        // "foot-hiking": profilo pensato per l'escursionismo (privilegia
        // sentieri/tracciati adatti a piedi, tiene conto della pendenza),
        // distinto da "foot-walking" (cammino generico, più adatto a un
        // contesto urbano) — scelta coerente con lo scopo dell'app.
        const val PROFILE_HIKING = "foot-hiking"

        fun create(): RoutingApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(RoutingApiService::class.java)
        }
    }
}
