package com.gmtrekking.app.data.emergency

import kotlinx.serialization.Serializable

/**
 * Un contatto da avvisare in caso di emergenza (punto 7 del piano): un'etichetta
 * libera (es. "Moglie", "Rifugio base") e un numero di telefono. Configurati
 * dalla schermata "Impostazioni", usati dalla schermata "Emergenza" per
 * l'invio automatico dell'SMS e per l'apertura mirata di WhatsApp.
 */
@Serializable
data class EmergencyContact(
    val id: String,
    val label: String,
    val phoneNumber: String,
)

/** Un numero di emergenza da mostrare in pagina, con l'etichetta del servizio a cui corrisponde. */
data class EmergencyNumberEntry(
    val label: String,
    val number: String,
)
