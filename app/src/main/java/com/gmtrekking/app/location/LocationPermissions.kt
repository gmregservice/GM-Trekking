package com.gmtrekking.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Helper per verificare i permessi di localizzazione (e notifiche, per il servizio in foreground). */
object LocationPermissions {

    /** Permessi da richiedere all'avvio della navigazione (posizione in primo piano). */
    fun foregroundLocationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /**
     * Permesso di localizzazione in background, necessario perché il
     * tracciamento continui a schermo spento. Su Android va richiesto in un
     * passaggio separato, DOPO che l'utente ha già concesso quello in
     * primo piano (requisito imposto dal sistema operativo dalla versione 11).
     */
    fun backgroundLocationPermission(): String = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    fun notificationPermissionIfNeeded(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    fun hasForegroundLocationPermission(context: Context): Boolean =
        foregroundLocationPermissions().any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBackgroundLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, backgroundLocationPermission()) == PackageManager.PERMISSION_GRANTED
}
