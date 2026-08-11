package com.gmtrekking.app.data.gpx

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Contenitore in memoria per il tracciato attualmente caricato, condiviso tra
 * la schermata di import e quella di navigazione.
 *
 * Scelta pragmatica per questo scheletro: evita di dover passare un oggetto
 * complesso come argomento di navigazione (Navigation Compose supporta bene
 * solo tipi semplici). Se il progetto cresce, conviene sostituirlo con uno
 * ViewModel condiviso a livello di grafo di navigazione o con un repository
 * vero e proprio (magari con persistenza, per riaprire l'ultimo percorso
 * caricato anche dopo aver chiuso l'app).
 */
object CurrentTrackHolder {
    val track = MutableStateFlow<GpxTrack?>(null)
}
