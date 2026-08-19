package com.gmtrekking.app.ui.screens.trailnavigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.gmtrekking.app.R

/**
 * Bussola sovrapposta alla mappa (richiesta esplicitamente, agosto 2026):
 * un'icona a forma di "N" che ruota per restare sempre allineata al nord
 * reale, usando lo stesso sensore di orientamento già letto per la freccia
 * di navigazione (vedi DeviceHeading.kt). Non è un vero quadrante a 360°:
 * coerente con la scelta di interfaccia essenziale già seguita nel resto
 * dell'app (poche informazioni, ben leggibili — vedi principio guida nel
 * piano di sviluppo).
 *
 * Tocca per nascondere/mostrare (l'opzione "Visualizza sì/no" richiesta):
 * da nascosta resta comunque un piccolo cerchio toccabile nello stesso
 * punto, invece di sparire del tutto — altrimenti non ci sarebbe modo di
 * farla ricomparire senza uscire e rientrare dalla schermata.
 */
@Composable
fun CompassOverlay(headingDegrees: Float, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    val diameter = if (expanded) 56.dp else 28.dp
    val iconSize = if (expanded) 32.dp else 16.dp
    val backgroundAlpha = if (expanded) 0.85f else 0.5f

    Box(
        modifier = modifier
            .size(diameter)
            .background(Color.White.copy(alpha = backgroundAlpha), CircleShape)
            .clickable { expanded = !expanded },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.North,
            contentDescription = stringResource(R.string.compass_toggle),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(iconSize)
                .rotate(-headingDegrees),
        )
    }
}
