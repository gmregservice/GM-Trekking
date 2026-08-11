package com.gmtrekking.app.ui.screens.trailnavigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * Freccia direzionale grande e leggibile: l'elemento centrale della schermata
 * di navigazione (vedi principio guida "sicurezza e chiarezza" nel piano di
 * sviluppo).
 *
 * NOTA: la rotazione è calcolata rispetto al nord (bearing assoluto verso il
 * prossimo punto del tracciato), non rispetto all'orientamento del telefono
 * in mano all'utente. Per far corrispondere davvero la freccia a "dove sto
 * guardando" servirebbe leggere anche la bussola del dispositivo (sensore
 * magnetometro) e sottrarre l'azimut del telefono dal bearing: funzionalità
 * pianificata per la Fase 2, non ancora implementata qui.
 */
@Composable
fun DirectionArrow(
    bearingDegrees: Double,
    isOffRoute: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isOffRoute) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Icon(
        imageVector = Icons.Filled.North,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(120.dp)
            .rotate(bearingDegrees.toFloat()),
    )
}
