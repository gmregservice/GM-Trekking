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

    /**
     * Permesso per leggere il sensore contapassi (Sensor.TYPE_STEP_COUNTER),
     * richiesto solo da Android 10 (API 29) in su — prima non serviva alcun
     * permesso per questo sensore.
     */
    fun activityRecognitionPermissionIfNeeded(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else null

    fun hasForegroundLocationPermission(context: Context): Boolean =
        foregroundLocationPermissions().any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBackgroundLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, backgroundLocationPermission()) == PackageManager.PERMISSION_GRANTED

    /** true anche su Android < 10, dove questo permesso non esiste/non serve. */
    fun hasActivityRecognitionPermission(context: Context): Boolean =
        activityRecognitionPermissionIfNeeded()?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true

    /**
     * Permesso per scrivere nella galleria pubblica del telefono (backup
     * automatico delle foto del percorso, `data/tracking/PhotoBackup.kt`),
     * necessario solo su Android 9 (API 28) e precedenti: da Android 10 in poi
     * ("scoped storage") un'app può scrivere nuovi file multimediali propri
     * nelle raccolte pubbliche (Pictures, ecc.) senza alcun permesso —
     * dichiarato in AndroidManifest.xml con `maxSdkVersion="28"` per lo
     * stesso motivo, il sistema lo ignora comunque da Android 10 in su.
     */
    fun legacyWriteExternalStoragePermissionIfNeeded(): String? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Manifest.permission.WRITE_EXTERNAL_STORAGE else null

    /** true anche da Android 10 in su, dove questo permesso non serve più. */
    fun hasLegacyWriteExternalStoragePermission(context: Context): Boolean =
        legacyWriteExternalStoragePermissionIfNeeded()?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true
}
