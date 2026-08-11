package com.gmtrekking.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gmtrekking.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servizio in foreground che mantiene attivo il GPS durante la navigazione,
 * anche a schermo spento o con l'app in background.
 *
 * Espone gli aggiornamenti di posizione tramite [locationUpdates], uno
 * StateFlow statico: scelta semplice per questo scheletro (evita di scrivere
 * un bound service completo). Se il progetto cresce, vale la pena sostituirlo
 * con un vero binding Service <-> ViewModel.
 *
 * Nota permessi: questo servizio assume che i permessi di localizzazione
 * (primo piano + sfondo) siano già stati concessi da chi lo avvia — la
 * richiesta dei permessi va fatta nella UI prima di chiamare startForegroundService.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _locationUpdates.value = it }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _locationUpdates.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            // Permesso non concesso: il chiamante deve verificare con
            // LocationPermissions PRIMA di avviare il servizio.
            stopSelf()
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.nav_tracking_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "trekking_navigation"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 3000L
        private const val MIN_UPDATE_INTERVAL_MS = 1500L

        private val _locationUpdates = MutableStateFlow<Location?>(null)
        val locationUpdates = _locationUpdates.asStateFlow()
    }
}
