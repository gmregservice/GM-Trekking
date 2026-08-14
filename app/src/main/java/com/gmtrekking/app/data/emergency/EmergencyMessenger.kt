package com.gmtrekking.app.data.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import java.util.Locale

/**
 * Invio della richiesta di aiuto (punto 7 del piano): SMS automatico a tutti
 * i contatti configurati, più predisposizione di WhatsApp con lo stesso
 * messaggio già scritto verso un contatto per volta. WhatsApp non offre
 * un'API di invio automatico silenzioso per utenti privati: l'ultimo tocco
 * su "Invia" dentro l'app resta sempre dell'utente (vedi piano — canale
 * secondario/di comodo, l'SMS resta quello affidabile con solo segnale
 * telefonico minimo, senza bisogno di dati).
 */
object EmergencyMessenger {

    /**
     * Messaggio condiviso da SMS e WhatsApp: le coordinate ci sono sempre
     * (derivano dal solo GPS, nessuna rete richiesta), l'indirizzo/toponimo
     * solo quando già disponibile (reverse geocoding, spesso assente in
     * montagna senza connessione dati).
     */
    fun buildMessage(latitude: Double, longitude: Double, address: String?): String {
        val coords = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        val mapsLink = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", latitude, longitude)
        val addressLine = address?.let { "\nZona: $it" } ?: ""
        return "EMERGENZA - Ho bisogno di aiuto. Posizione: $coords$addressLine\n$mapsLink"
    }

    /**
     * Invia [message] via SMS a tutti i numeri di [contacts]. Il permesso
     * SEND_SMS va già verificato/concesso dal chiamante prima di invocarla
     * (vedi EmergencyScreen.kt) — qui non viene richiesto, solo usato.
     */
    @Suppress("MissingPermission")
    fun sendSms(context: Context, contacts: List<EmergencyContact>, message: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        contacts.forEach { contact ->
            runCatching {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
            }
        }
    }

    /**
     * Intent per aprire WhatsApp con [message] già scritto verso [phoneNumber].
     * WhatsApp si aspetta il numero in formato internazionale senza "+" né
     * spazi: la pulizia va fatta qui perché l'utente potrebbe averlo salvato
     * con "+39 ..." o spazi, come lo scriverebbe normalmente in Impostazioni.
     */
    fun whatsAppIntent(phoneNumber: String, message: String): Intent {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri)
    }
}
