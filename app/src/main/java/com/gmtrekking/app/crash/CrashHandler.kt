package com.gmtrekking.app.crash

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Intercetta le eccezioni non gestite (i crash) e, invece di lasciare che il
 * sistema mostri il solito messaggio generico "l'app si è arrestata", apre
 * CrashReportActivity con il dettaglio dell'errore leggibile a schermo.
 *
 * Va installato una sola volta, il prima possibile — vedi GMTrekkingApp.onCreate.
 */
object CrashHandler {

    fun install(appContext: Context) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))

                val intent = Intent(appContext, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashReportActivity.EXTRA_STACK_TRACE, stringWriter.toString())
                }
                appContext.startActivity(intent)
            } catch (_: Throwable) {
                // Se anche l'apertura della schermata di crash fallisce, non c'è molto
                // altro da fare: si chiude comunque il processo qui sotto.
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }
}
