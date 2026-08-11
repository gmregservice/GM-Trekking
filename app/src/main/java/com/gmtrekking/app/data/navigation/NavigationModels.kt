package com.gmtrekking.app.data.navigation

/**
 * Stato di navigazione calcolato ad ogni aggiornamento di posizione GPS.
 *
 * È il modello su cui si basa l'intera UI di navigazione (freccia, distanza,
 * zoom automatico, avviso di fuori percorso): tenerlo semplice e con nomi
 * comprensibili è intenzionale, per evitare che concetti tecnici trapelino
 * fino all'interfaccia mostrata all'utente.
 */
data class NavigationState(
    /** Indice del punto del tracciato più vicino alla posizione corrente. */
    val nearestPointIndex: Int,
    /** Distanza in metri tra la posizione corrente e il tracciato. */
    val distanceToTrackMeters: Double,
    /** Direzione (0-360°, 0 = nord) da seguire per raggiungere il prossimo punto del tracciato. */
    val bearingToNextPointDegrees: Double,
    /** Distanza in metri dal prossimo punto "notevole" del tracciato (usata anche per lo zoom automatico). */
    val distanceToNextPointMeters: Double,
    /** Distanza residua in metri fino alla fine del tracciato, seguendo il percorso. */
    val distanceRemainingMeters: Double,
    /** true se l'utente ha superato la soglia di tolleranza ed è considerato fuori percorso. */
    val isOffRoute: Boolean,
    /**
     * true quando ci si avvicina a un tratto "complesso" del tracciato (bivio, cambio di
     * direzione brusco, tracciati ravvicinati) e la UI dovrebbe passare alla vista ravvicinata.
     * Il calcolo effettivo è demandato a versioni successive di NavigationEngine — per ora
     * si basa solo sulla distanza dal prossimo punto.
     */
    val shouldZoomIn: Boolean,
)

/** Soglie di navigazione, pensate per essere calibrabili senza toccare la logica. */
data class NavigationThresholds(
    val offRouteToleranceMeters: Double = 30.0,
    val autoZoomTriggerDistanceMeters: Double = 60.0,
)
