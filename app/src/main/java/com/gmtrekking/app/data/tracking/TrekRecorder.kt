package com.gmtrekking.app.data.tracking

import android.location.Location
import com.gmtrekking.app.data.navigation.NavigationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingStatus { IDLE, RECORDING, PAUSED }

data class RecordingSnapshot(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val totalTimeMillis: Long = 0L,
    val movingTimeMillis: Long = 0L,
    // L'utente è in pausa ma il GPS rileva che si sta comunque spostando:
    // probabile dimenticanza di aver premuto "Riprendi" (vedi il punto 1
    // dei "Richiesta utente da sviluppare" in docs/PIANO_SVILUPPO.md).
    val possiblyForgottenPause: Boolean = false,
)

/**
 * Motore di registrazione del cammino effettuato: indipendente da un
 * eventuale percorso GPX caricato come guida (CurrentTrackHolder) — si può
 * registrare seguendo un GPX oppure partendo semplicemente con "Avvia
 * registrazione", senza bisogno di un tracciato precaricato.
 *
 * Oggetto singleton con stato in memoria (StateFlow), sullo stesso modello
 * di CurrentTrackHolder e del companion object di LocationTrackingService
 * già usati in questo progetto — coerente con lo stile esistente, senza
 * introdurre un framework di dependency injection per un caso così semplice.
 *
 * Dislivello: calcolato dal solo GPS (nessun barometro), con una soglia
 * minima per segmento per limitare il rumore verticale tipico del GPS —
 * limite noto, documentato anche negli "Spunti dalla ricerca competitiva"
 * in docs/PIANO_SVILUPPO.md. Un'integrazione con il barometro del telefono
 * (dove presente) è un miglioramento futuro, non incluso in questa prima
 * versione per tenere contenuto il rischio dell'incremento.
 */
object TrekRecorder {

    private val _state = MutableStateFlow(RecordingSnapshot())
    val state = _state.asStateFlow()

    private var points = mutableListOf<TrackedPoint>()
    private var startTimeMillis = 0L
    private var lastPointTimeMillis = 0L
    private var accumulatedMovingMillis = 0L
    private var pauseAnchorLat: Double? = null
    private var pauseAnchorLon: Double? = null

    fun start(location: Location) {
        val now = System.currentTimeMillis()
        points = mutableListOf(location.toTrackedPoint(now))
        startTimeMillis = now
        lastPointTimeMillis = now
        accumulatedMovingMillis = 0L
        pauseAnchorLat = null
        pauseAnchorLon = null
        _state.value = RecordingSnapshot(status = RecordingStatus.RECORDING)
    }

    fun pause() {
        val current = _state.value
        if (current.status != RecordingStatus.RECORDING) return
        val last = points.lastOrNull()
        pauseAnchorLat = last?.latitude
        pauseAnchorLon = last?.longitude
        _state.value = current.copy(status = RecordingStatus.PAUSED, possiblyForgottenPause = false)
    }

    fun resume() {
        val current = _state.value
        if (current.status != RecordingStatus.PAUSED) return
        lastPointTimeMillis = System.currentTimeMillis()
        pauseAnchorLat = null
        pauseAnchorLon = null
        _state.value = current.copy(status = RecordingStatus.RECORDING, possiblyForgottenPause = false)
    }

    /**
     * Conclude la registrazione e restituisce il percorso da salvare (null
     * se non era in corso una registrazione, o se troppo breve — meno di
     * due punti — per avere senso come percorso).
     */
    fun stop(): CompletedActivity? {
        val current = _state.value
        if (current.status == RecordingStatus.IDLE) return null

        val endTime = System.currentTimeMillis()
        val result = if (points.size >= 2) {
            CompletedActivity(
                id = startTimeMillis.toString(),
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTime,
                distanceMeters = current.distanceMeters,
                totalTimeMillis = endTime - startTimeMillis,
                movingTimeMillis = current.movingTimeMillis,
                elevationGainMeters = current.elevationGainMeters,
                points = points.toList(),
            )
        } else {
            null
        }
        reset()
        return result
    }

    /** Annulla la registrazione in corso senza salvare nulla. */
    fun discard() {
        reset()
    }

    /**
     * Da chiamare ad ogni nuovo fix GPS, indipendentemente dallo stato:
     * durante IDLE non fa nulla; durante PAUSED controlla solo se l'utente
     * sembra essersi comunque mosso (senza accumulare distanza/tempo);
     * durante RECORDING accumula distanza, tempo in movimento e dislivello.
     */
    fun onLocationUpdate(location: Location) {
        val current = _state.value
        when (current.status) {
            RecordingStatus.IDLE -> Unit
            RecordingStatus.PAUSED -> handlePausedUpdate(current, location)
            RecordingStatus.RECORDING -> handleRecordingUpdate(current, location)
        }
    }

    private fun handlePausedUpdate(current: RecordingSnapshot, location: Location) {
        val anchorLat = pauseAnchorLat
        val anchorLon = pauseAnchorLon
        if (anchorLat == null || anchorLon == null) return
        val moved = NavigationEngine.distanceMeters(anchorLat, anchorLon, location.latitude, location.longitude)
        val forgotten = moved > FORGOTTEN_PAUSE_DISTANCE_METERS
        if (forgotten != current.possiblyForgottenPause) {
            _state.value = current.copy(possiblyForgottenPause = forgotten)
        }
    }

    private fun handleRecordingUpdate(current: RecordingSnapshot, location: Location) {
        val now = System.currentTimeMillis()
        val last = points.last()
        val segmentDistance = NavigationEngine.distanceMeters(
            last.latitude, last.longitude, location.latitude, location.longitude,
        )
        val elevationGain = computeElevationGain(
            previousElevation = last.elevationMeters,
            currentElevation = if (location.hasAltitude()) location.altitude else null,
        )
        accumulatedMovingMillis += (now - lastPointTimeMillis).coerceAtLeast(0L)
        lastPointTimeMillis = now
        points.add(location.toTrackedPoint(now))

        _state.value = current.copy(
            distanceMeters = current.distanceMeters + segmentDistance,
            elevationGainMeters = current.elevationGainMeters + elevationGain,
            totalTimeMillis = now - startTimeMillis,
            movingTimeMillis = accumulatedMovingMillis,
        )
    }

    private fun computeElevationGain(previousElevation: Double?, currentElevation: Double?): Double {
        if (previousElevation == null || currentElevation == null) return 0.0
        val delta = currentElevation - previousElevation
        // Solo le salite reali contano (positive) e solo oltre la soglia di
        // rumore GPS: senza questo filtro, il solo GPS sovrastima parecchio
        // il dislivello anche su tratti pianeggianti (vedi ricerca
        // competitiva in docs/PIANO_SVILUPPO.md).
        return if (delta > MIN_ELEVATION_DELTA_METERS) delta else 0.0
    }

    private fun reset() {
        points = mutableListOf()
        startTimeMillis = 0L
        lastPointTimeMillis = 0L
        accumulatedMovingMillis = 0L
        pauseAnchorLat = null
        pauseAnchorLon = null
        _state.value = RecordingSnapshot()
    }

    private fun Location.toTrackedPoint(timestampMillis: Long) = TrackedPoint(
        latitude = latitude,
        longitude = longitude,
        elevationMeters = if (hasAltitude()) altitude else null,
        timestampMillis = timestampMillis,
    )

    private const val FORGOTTEN_PAUSE_DISTANCE_METERS = 30.0
    private const val MIN_ELEVATION_DELTA_METERS = 3.0
}
