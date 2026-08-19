package com.gmtrekking.app.data.settings

import android.content.Context

/**
 * Impostazioni semplici dell'app (valori singoli, non elenchi — per gli
 * elenchi si usa il pattern a file JSON già visto altrove, es.
 * data/emergency/EmergencyContactsStorage.kt). `SharedPreferences` è lo
 * strumento standard di Android pensato apposta per poche coppie
 * chiave/valore come queste, più semplice di un file JSON per un singolo
 * valore scalare.
 *
 * Per ora contiene solo la chiave API di OpenRouteService (instradamento
 * reale sulla mappa — funzione in preparazione, vedi
 * `ui/screens/settings/SettingsScreen.kt` e la nota "Instradamento reale"
 * in docs/PIANO_SVILUPPO.md): questa classe salva solo la chiave, non fa
 * ancora nessuna chiamata al servizio. Pensata come contenitore generale
 * per le impostazioni future, non solo per questa.
 */
object AppSettingsStorage {
    private const val PREFS_NAME = "gm_trekking_settings"
    private const val KEY_ORS_API_KEY = "ors_api_key"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** null se non ancora configurata (o se salvata come stringa vuota/solo spazi). */
    fun getOrsApiKey(context: Context): String? =
        prefs(context).getString(KEY_ORS_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setOrsApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_ORS_API_KEY, apiKey.trim()).apply()
    }

    fun clearOrsApiKey(context: Context) {
        prefs(context).edit().remove(KEY_ORS_API_KEY).apply()
    }
}
