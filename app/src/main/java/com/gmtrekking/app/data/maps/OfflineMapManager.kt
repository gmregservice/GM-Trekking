package com.gmtrekking.app.data.maps

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus

/**
 * Download/gestione di mappe offline tramite `OfflineManager` di MapLibre:
 * scarica tile vettoriali + stile + sprite/glyph per un'area rettangolare
 * (vedi OfflineRegions.kt) in un database locale sul dispositivo. Una volta
 * scaricata, TrekMapView non ha bisogno di nessuna logica in più per usarla:
 * è MapLibre stesso a controllare prima la cache locale prima di scaricare
 * una tile dalla rete, quindi la mappa "funziona offline" in modo trasparente
 * appena l'area è stata scaricata mentre si aveva connessione.
 *
 * Prima integrazione di questa API in questo progetto (agosto 2026): nomi di
 * classi/metodi verificati sulla documentazione ufficiale di MapLibre Native
 * Android 13.4.1 (stessa versione già usata da questo progetto, vedi
 * build.gradle.kts), non testati con una build reale in questo ambiente
 * (nessun accesso a un emulatore/dispositivo qui) — da verificare con la
 * prima build CI dopo l'introduzione.
 */
object OfflineMapManager {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RegionMetadata(val name: String)

    sealed class DownloadState {
        object Idle : DownloadState()
        data class InProgress(
            val completedResourceCount: Long,
            val requiredResourceCount: Long,
            val downloadedBytes: Long,
        ) : DownloadState()
        data class Completed(val downloadedBytes: Long) : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    data class SavedRegion(val region: OfflineRegion, val name: String)

    // Limite di default di MapLibre: 6.000 tile scaricabili per l'intero
    // database offline. Un'area larga come una regione, anche solo fino allo
    // zoom 14, può avvicinarsi o superare questa soglia (stima fatta prima di
    // scrivere questo codice: qualche migliaio di tile per la sola Lombardia)
    // — senza alzarlo esplicitamente, il download si fermerebbe in silenzio
    // al limite invece di completare l'area richiesta (vedi
    // mapboxTileCountLimitExceeded sotto). 50.000 lascia ampio margine anche
    // per aree più grandi in futuro (selezione libera, pianificata).
    private const val TILE_COUNT_LIMIT = 50_000L

    /**
     * Avvia il download di una regione con nome [name] e confini/zoom
     * [definition] (vedi OfflineRegions.kt), notificando [onUpdate] ad ogni
     * avanzamento. Se una regione con la stessa definizione esiste già nel
     * database, MapLibre ne crea comunque una nuova voce distinta (nessuna
     * deduplica automatica) — per l'uso attuale (una sola area fissa,
     * scaricata una volta) non è un problema pratico; la schermata chiamante
     * evita comunque di riproporre il download se una regione con questo
     * nome risulta già completa (vedi SettingsScreen.kt).
     */
    fun download(
        context: Context,
        definition: OfflineRegionDefinition,
        name: String,
        onUpdate: (DownloadState) -> Unit,
    ) {
        val manager = OfflineManager.getInstance(context)
        manager.setOfflineMapboxTileCountLimit(TILE_COUNT_LIMIT)
        val metadataBytes = json.encodeToString(RegionMetadata(name)).toByteArray()

        onUpdate(DownloadState.InProgress(0, 0, 0))

        manager.createOfflineRegion(
            definition,
            metadataBytes,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            onUpdate(
                                if (status.isComplete) {
                                    DownloadState.Completed(status.completedResourceSize)
                                } else {
                                    DownloadState.InProgress(
                                        status.completedResourceCount,
                                        status.requiredResourceCount,
                                        status.completedResourceSize,
                                    )
                                }
                            )
                        }

                        override fun onError(error: OfflineRegionError) {
                            onUpdate(DownloadState.Failed("${error.reason}: ${error.message}"))
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            onUpdate(DownloadState.Failed("Limite di $limit tile superato: prova un'area più piccola o uno zoom massimo più basso."))
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    onUpdate(DownloadState.Failed(error))
                }
            },
        )
    }

    /** Elenco delle aree già scaricate (nome letto dai metadata salvati insieme alla regione). */
    fun listSavedRegions(
        context: Context,
        onResult: (List<SavedRegion>) -> Unit,
        onError: (String) -> Unit,
    ) {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().map { region ->
                    val name = runCatching {
                        json.decodeFromString<RegionMetadata>(String(region.metadata)).name
                    }.getOrDefault("Area scaricata")
                    SavedRegion(region, name)
                }
                onResult(regions)
            }

            override fun onError(error: String) {
                onError(error)
            }
        })
    }

    /** Elimina [region] dal database locale, liberando lo spazio occupato. */
    fun delete(region: OfflineRegion, onDone: (success: Boolean, error: String?) -> Unit) {
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                onDone(true, null)
            }

            override fun onError(error: String) {
                onDone(false, error)
            }
        })
    }
}
