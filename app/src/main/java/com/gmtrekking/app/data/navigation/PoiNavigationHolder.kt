package com.gmtrekking.app.data.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Contenitore in memoria per la navigazione verso un singolo "luogo utile"
 * (punto 6 dei "Richiesta utente da sviluppare" in docs/PIANO_SVILUPPO.md),
 * distinto da [com.gmtrekking.app.data.gpx.CurrentTrackHolder] che tiene
 * invece il percorso GPX caricato come guida.
 *
 * Tenerli separati (invece di forzare la destinazione dentro un GpxTrack
 * salvato in CurrentTrackHolder) permette a MainMapScreen di sapere sempre
 * se un eventuale percorso GPX caricato è ancora quello "vero" dell'utente:
 * avviare la navigazione verso un luogo utile non lo sovrascrive né lo perde,
 * lo mette solo temporaneamente in secondo piano — terminata la navigazione
 * verso il luogo (svuotando [target]), la navigazione sul percorso GPX
 * originale (se presente) riprende automaticamente, senza bisogno di
 * ricaricare nulla.
 */
object PoiNavigationHolder {

    /** Destinazione verso cui navigare: nome del luogo (per la UI) e coordinate. */
    data class Target(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    val target = MutableStateFlow<Target?>(null)
}
