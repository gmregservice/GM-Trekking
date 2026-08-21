package com.gmtrekking.app.data.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Foto associate ai punti del percorso (ActivityWaypoint), salvate nella
 * cartella privata dell'app — stesso principio già seguito per il resto
 * della registrazione (JSON locale, nessun servizio esterno, vedi
 * ActivityStorage.kt): niente cloud, niente account, tutto sul telefono.
 *
 * Le foto vengono scattate delegando all'app Fotocamera di sistema (intent
 * `ACTION_IMAGE_CAPTURE`), non con l'API Camera direttamente: questo evita
 * di dover richiedere il permesso CAMERA e di scrivere/mantenere una UI di
 * scatto — scelta deliberata di semplicità, coerente con l'uso di
 * `Intent.ACTION_DIAL` invece di chiamare direttamente per i luoghi utili.
 * Per scrivere il risultato in un file nostro (non solo l'anteprima a bassa
 * risoluzione restituita di default) serve un Uri condivisibile con l'app
 * Fotocamera: da qui il FileProvider (vedi AndroidManifest.xml e
 * res/xml/file_paths.xml).
 */
object PhotoStorage {
    private const val PHOTOS_DIR_NAME = "activity_photos"

    fun photoFile(context: Context, fileName: String): File =
        File(File(context.filesDir, PHOTOS_DIR_NAME), fileName)

    /**
     * Crea un nuovo file vuoto pronto per la fotocamera e restituisce il suo
     * nome (da passare a TrekRecorder.addPhotoWaypoint una volta confermato
     * lo scatto) e l'Uri condivisibile via FileProvider da passare
     * all'intent ACTION_IMAGE_CAPTURE.
     */
    fun newPhotoTarget(context: Context): Pair<String, Uri> {
        val dir = File(context.filesDir, PHOTOS_DIR_NAME).apply { mkdirs() }
        val fileName = "photo_${System.currentTimeMillis()}.jpg"
        val file = File(dir, fileName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return fileName to uri
    }

    /**
     * Elimina il file foto [fileName]: usata sia quando l'utente annulla uno
     * scatto (file vuoto appena creato da [newPhotoTarget]) sia quando un
     * intero percorso viene cancellato dalla Cronologia e bisogna rimuovere
     * anche le foto dei suoi waypoint (vedi ActivityDetailScreen.kt).
     */
    fun delete(context: Context, fileName: String) {
        runCatching { photoFile(context, fileName).delete() }
    }

    /**
     * Carica una versione ridotta della foto per le anteprime in Cronologia,
     * invece della risoluzione piena della fotocamera (spesso diversi MB):
     * senza questo downscaling, mostrare più foto in una lista rischierebbe
     * di esaurire la memoria disponibile all'app.
     */
    fun loadThumbnail(context: Context, fileName: String, reqSize: Int = 256): Bitmap? {
        val file = photoFile(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOnly)

            var sampleSize = 1
            while (boundsOnly.outWidth / (sampleSize * 2) >= reqSize && boundsOnly.outHeight / (sampleSize * 2) >= reqSize) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        }.getOrNull()
    }

    /**
     * Carica la foto per il visualizzatore a schermo intero (richiesto
     * esplicitamente, agosto 2026: la miniatura di 256px in Cronologia era
     * troppo piccola per vedere bene una foto scattata durante il cammino).
     * Un limite di 2048px di lato (comunque più che sufficiente per riempire
     * lo schermo di qualunque telefono) invece della risoluzione piena della
     * fotocamera, che su alcuni dispositivi supera i 10 MB per singola foto —
     * decodificarla senza alcun limite rischierebbe comunque di esaurire la
     * memoria disponibile all'app se l'utente apre più foto in sequenza.
     */
    fun loadFullScreen(context: Context, fileName: String, reqSize: Int = 2048): Bitmap? =
        loadThumbnail(context, fileName, reqSize)

    /**
     * Uri condivisibile (tramite lo stesso FileProvider già configurato per
     * la fotocamera, vedi [newPhotoTarget]) per il pulsante "Condividi" nel
     * visualizzatore a schermo intero — null se il file non esiste più.
     */
    fun shareableUri(context: Context, fileName: String): Uri? {
        val file = photoFile(context, fileName)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
