package com.gmtrekking.app.ui.screens.trailnavigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R

/**
 * Bussola sovrapposta alla mappa (richiesta esplicitamente, agosto 2026):
 * un piccolo quadrante con i quattro punti cardinali (N/E/S/O — richiesti
 * esplicitamente, agosto 2026) che ruota tutto insieme per restare sempre
 * allineato al nord reale, usando lo stesso sensore di orientamento già
 * letto per la freccia di navigazione (vedi DeviceHeading.kt). Non un vero
 * quadrante a 360° con tutti i gradi: coerente con la scelta di interfaccia
 * essenziale già seguita nel resto dell'app (poche informazioni, ben
 * leggibili — vedi principio guida nel piano di sviluppo).
 *
 * Le quattro lettere ruotano come un'unica lancetta (stesso principio di una
 * bussola vera: il quadrante gira, non l'ago) invece di restare fisse con
 * una sola freccia rotante: più immediato da leggere a colpo d'occhio.
 *
 * Tocca per nascondere/mostrare (l'opzione "Visualizza sì/no" richiesta):
 * da nascosta resta comunque un piccolo cerchio toccabile nello stesso
 * punto, invece di sparire del tutto — altrimenti non ci sarebbe modo di
 * farla ricomparire senza uscire e rientrare dalla schermata.
 */
@Composable
fun CompassOverlay(headingDegrees: Float, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    val diameter = if (expanded) 64.dp else 28.dp
    val backgroundAlpha = if (expanded) 0.85f else 0.5f
    val toggleDescription = stringResource(R.string.compass_toggle)

    Box(
        modifier = modifier
            .size(diameter)
            .background(Color.White.copy(alpha = backgroundAlpha), CircleShape)
            .clickable { expanded = !expanded }
            .semantics { contentDescription = toggleDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-headingDegrees),
            ) {
                CompassLabel(stringResource(R.string.compass_north), Modifier.align(Alignment.TopCenter))
                CompassLabel(stringResource(R.string.compass_east), Modifier.align(Alignment.CenterEnd))
                CompassLabel(stringResource(R.string.compass_south), Modifier.align(Alignment.BottomCenter))
                CompassLabel(stringResource(R.string.compass_west), Modifier.align(Alignment.CenterStart))
            }
        } else {
            // Da nascosta, un'unica freccia al posto delle 4 lettere: stesso
            // ingombro ridotto già scelto per il cerchio più piccolo.
            Icon(
                imageVector = Icons.Filled.North,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(-headingDegrees),
            )
        }
    }
}

@Composable
private fun CompassLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(2.dp),
    )
}
