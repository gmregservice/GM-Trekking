package com.gmtrekking.app.data.emergency

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Toponimo/indirizzo più vicino a un punto, per la pagina "Emergenza" (punto 7
 * del piano) — un dato aggiuntivo, mai un requisito: le coordinate GPS restano
 * sempre mostrabili senza bisogno di questo. Usa il Geocoder di sistema, che
 * su molti dispositivi si appoggia a un servizio esterno e richiede quindi
 * connessione dati per funzionare: in montagna, senza dati, fallirà
 * semplicemente e la UI mostrerà "indirizzo non disponibile" — un caso
 * normale e atteso (vedi piano), non un errore da segnalare.
 */
object ReverseGeocoder {

    @Suppress("DEPRECATION")
    suspend fun addressFor(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let { address ->
                listOfNotNull(address.locality ?: address.subAdminArea, address.adminArea)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            }
        }.getOrNull()
    }
}
