package com.gmtrekking.app

import android.app.Application
import com.gmtrekking.app.crash.CrashHandler
import org.maplibre.android.MapLibre

/**
 * Punto di ingresso a livello di applicazione.
 *
 * Inizializza MapLibre una sola volta, all'avvio dell'app: è un requisito
 * della libreria, va fatto PRIMA che qualunque schermata crei una MapView
 * (TrekMapView.kt), altrimenti l'app va in crash nel momento in cui si apre
 * la schermata di navigazione — esattamente il bug riscontrato dopo il primo
 * test su dispositivo reale, causato dall'assenza di questa chiamata.
 *
 * Installa anche CrashHandler: l'app viene compilata e distribuita solo
 * tramite GitHub Actions, senza Android Studio, quindi in caso di crash non
 * c'è modo di leggere Logcat. CrashHandler mostra l'errore direttamente a
 * schermo (vedi crash/CrashHandler.kt e crash/CrashReportActivity.kt).
 *
 * Quando in Fase 2 introdurremo Room per la cache dei luoghi utili offline,
 * l'istanza del database andrà inizializzata/esposta da qui (o tramite un
 * framework di dependency injection, se il progetto crescerà abbastanza da
 * giustificarlo).
 */
// Build diagnostico (agosto 2026): commit senza alcuna modifica funzionale,
// solo per generare una nuova run di GitHub Actions e verificare se un APK
// rigenerato da zero risolve un problema di installazione ("Annullamento"
// immediato all'apertura del file) riscontrato sui build precedenti, sia da
// 69 MB che da 37 MB — nessun cambio di manifest, permessi o dipendenze
// nel frattempo, quindi questo test isola se il problema è nel contenuto
// del codice o nel processo di build/packaging stesso.
class GMTrekkingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        MapLibre.getInstance(this)
    }
}
