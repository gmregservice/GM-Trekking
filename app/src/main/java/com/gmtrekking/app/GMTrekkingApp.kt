package com.gmtrekking.app

import android.app.Application

/**
 * Punto di ingresso a livello di applicazione.
 *
 * Per ora non fa nulla oltre a esistere (referenziata da AndroidManifest.xml).
 * Quando in Fase 2 introdurremo Room per la cache dei luoghi utili offline,
 * l'istanza del database andrà inizializzata/esposta da qui (o tramite un
 * framework di dependency injection, se il progetto crescerà abbastanza da
 * giustificarlo).
 */
class GMTrekkingApp : Application()
