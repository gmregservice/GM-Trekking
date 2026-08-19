package com.gmtrekking.app.ui.screens.trailnavigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Legge l'orientamento del telefono (verso quale direzione della bussola sta
 * "guardando" lo schermo, 0-360°, 0 = nord, in senso orario) dal sensore
 * combinato `Sensor.TYPE_ROTATION_VECTOR` (accelerometro + magnetometro +
 * giroscopio, più stabile del solo magnetometro).
 *
 * Usato sia per orientare la freccia di navigazione rispetto al senso di
 * marcia invece che rispetto al nord fisso, sia per la bussola sovrapposta
 * alla mappa (richiesto esplicitamente, agosto 2026 — segnalato che la
 * freccia indicava una direzione sbagliata rispetto a dove si stava andando
 * realmente).
 *
 * Ritorna null se il sensore non è disponibile sul dispositivo: stesso
 * principio già usato per il contapassi (vedi MainMapScreen.kt) — la
 * funzionalità semplicemente non si attiva, senza bloccare il resto
 * dell'app. In quel caso la freccia torna al comportamento precedente
 * (rotazione rispetto al nord), non a un errore.
 *
 * **Scelta deliberata: solo bussola, non una media con la direzione di
 * marcia calcolata dal GPS.** Il GPS stima male la direzione di marcia a
 * passo d'uomo (velocità bassa, soste frequenti) — proprio lo scenario
 * tipico di un'escursione — mentre la bussola funziona anche da fermi. Se in
 * futuro si rivelasse insufficiente (es. interferenze magnetiche frequenti
 * in certe zone), una combinazione dei due è un miglioramento possibile, non
 * implementato qui per contenere il rischio di questo primo incremento.
 *
 * **Semplificazione nota sulla rotazione schermo**: la correzione per
 * l'orientamento del display viene letta una sola volta, non ad ogni
 * lettura del sensore (per non interrogare il WindowManager decine di volte
 * al secondo) — corretta per l'uso tipico dell'app (telefono tenuto in
 * verticale durante il cammino); se lo schermo viene ruotato mentre la
 * bussola è attiva, l'effetto (schermata ricomposta da capo) la
 * ricalcola comunque al riavvio dell'effetto.
 */
@Composable
fun rememberDeviceHeadingDegrees(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        @Suppress("DEPRECATION")
        val displayRotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        val (remapAxisX, remapAxisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationValues = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.remapCoordinateSystem(rotationMatrix, remapAxisX, remapAxisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientationValues)

                val azimuthDegrees = (Math.toDegrees(orientationValues[0].toDouble()).toFloat() + 360f) % 360f
                heading = azimuthDegrees
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return heading
}
