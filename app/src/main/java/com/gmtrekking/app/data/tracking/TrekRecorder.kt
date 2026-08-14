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
    // Null finché il sensore contapassi non ha ancora dato una prima lettura
    // dopo l'avvio della registrazione (o non è disponibile/permesso negato).
    val stepCount: Int? = null,
    // Numero di note/foto aggiunte finora in questa registrazione (punti 3 e
    // 4 del piano) — solo per dare un riscontro immediato in UI ("Note: 2"),
    // il contenuto vero e proprio vive nella lista waypoints di TrekRecorder
    // e finisce in CompletedActivity solo al termine (stop()).
    val waypointCount: Int = 0,
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
    // Lettura cumulativa del sensore contapassi (passi dall'ultimo riavvio del
    // telefono) all'avvio della registrazione: i passi dell'attività sono la
    // differenza fra la lettura corrente e questa. Null finché non arriva
    // ancora nessuna lettura dal sensore dopo lo start.
    private var startStepCount: Int? = null
    // Ultima lettura grezza del sensore vista, aggiornata sempre (anche in
    // pausa/idle): serve per calcolare quanti passi "saltare" al resume.
    private var latestRawStepTotal: Int? = null
    // Lettura grezza del sensore al momento della pausa: alla ripresa, i passi
    // fatti nel frattempo vengono esclusi spostando in avanti startStepCount
    // (stesso principio già usato per distanza e tempo in movimento).
    private var pauseStepAnchor: Int? = null
    // Note puntuali e foto raccolte durante la registrazione in corso (punti
    // 3 e 4 del piano) — vuota finché l'utente non ne aggiunge.
    private var waypoints = mutableListOf<ActivityWaypoint>()

    fun start(location: Location) {
        val now = System.currentTimeMillis()
        points = mutableListOf(location.toTrackedPoint(now))
        startTimeMillis = now
        lastPointTimeMillis = now
        accumulatedMovingMillis = 0L
        pauseAnchorLat = null
        pauseAnchorLon = null
        startStepCount = null
        pauseStepAnchor = null
        waypoints = mutableListOf()
        _state.value = RecordingSnapshot(status = RecordingStatus.RECORDING)
    }

    fun pause() {
        val current = _state.value
        if (current.status != RecordingStatus.RECORDING) return
        val last = points.lastOrNull()
        pauseAnchorLat = last?.latitude
        pauseAnchorLon = last?.longitude
        pauseStepAnchor = latestRawStepTotal
        _state.value = current.copy(status = RecordingStatus.PAUSED, possiblyForgottenPause = false)
    }

    fun resume() {
        val current = _state.value
        if (current.status != RecordingStatus.PAUSED) return
        lastPointTimeMillis = System.currentTimeMillis()
        pauseAnchorLat = null
        pauseAnchorLon = null

        val pausedAtStep = pauseStepAnchor
        val nowStep = latestRawStepTotal
        val start = startStepCount
        if (pausedAtStep != null && nowStep != null && start != null) {
            startStepCount = start + (nowStep - pausedAtStep)
        }
        pauseStepAnchor = null

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
                stepCount = current.stepCount,
                waypoints = waypoints.toList(),
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

    /**
     * Da chiamare ad ogni lettura del sensore `Sensor.TYPE_STEP_COUNTER`
     * (contatore cumulativo dei passi dall'ultimo riavvio del telefono — il
     * chiamante, in MainMapScreen, registra il listener e inoltra qui il
     * valore grezzo). La lettura grezza viene sempre memorizzata (serve al
     * calcolo dei passi da escludere quando si riprende da una pausa), ma i
     * passi dell'attività aumentano solo mentre lo stato è RECORDING, in
     * linea con distanza e tempo in movimento.
     */
    fun onStepCountSensorUpdate(totalStepsSinceBoot: Int) {
        latestRawStepTotal = totalStepsSinceBoot
        val current = _state.value
        if (current.status != RecordingStatus.RECORDING) return

        val start = startStepCount
        if (start == null) {
            // Prima lettura dopo l'avvio: diventa il punto di riferimento,
            // da qui in poi 0 passi finché non arrivano nuove letture.
            startStepCount = totalStepsSinceBoot
            if (current.stepCount != 0) {
                _state.value = current.copy(stepCount = 0)
            }
            return
        }

        val steps = (totalStepsSinceBoot - start).coerceAtLeast(0)
        if (steps != current.stepCount) {
            _state.value = current.copy(stepCount = steps)
        }
    }

    /**
     * Aggiunge una nota puntuale geolocalizzata alla posizione [location]
     * (punto 4 del piano). Non fa nulla fuori da una registrazione, o se il
     * testo è vuoto. Consentito sia durante RECORDING sia durante PAUSED —
     * a differenza di distanza/tempo/passi, aggiungere una nota mentre ci si
     * è fermati (es. per il panorama) è un caso d'uso legittimo, non un
     * errore da escludere.
     */
    fun addNoteWaypoint(location: Location, note: String) {
        if (note.isBlank()) return
        addWaypoint(
            ActivityWaypoint(
                id = nextWaypointId(),
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = System.currentTimeMillis(),
                note = note.trim(),
            )
        )
    }

    /**
     * Aggiunge una foto geolocalizzata alla posizione [location] (punto 3
     * del piano). [photoFileName] è il nome del file già salvato da
     * PhotoStorage.newPhotoTarget/la fotocamera — qui si registra solo il
     * riferimento, non si tocca il file.
     */
    fun addPhotoWaypoint(location: Location, photoFileName: String) {
        addWaypoint(
            ActivityWaypoint(
                id = nextWaypointId(),
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = System.currentTimeMillis(),
                photoFileName = photoFileName,
            )
        )
    }

    private fun addWaypoint(waypoint: ActivityWaypoint) {
        val current = _state.value
        if (current.status == RecordingStatus.IDLE) return
        waypoints.add(waypoint)
        _state.value = current.copy(waypointCount = waypoints.size)
    }

    private fun nextWaypointId(): String = "${System.currentTimeMillis()}_${waypoints.size}"

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
        startStepCount = null
        pauseStepAnchor = null
        waypoints = mutableListOf()
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
