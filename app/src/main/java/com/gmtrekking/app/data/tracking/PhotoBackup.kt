package com.gmtrekking.app.data.tracking

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gmtrekking.app.data.emergency.ReverseGeocoder
import com.gmtrekking.app.location.LocationPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia le foto di un percorso concluso nella galleria pubblica del telefono
 * (richiesto esplicitamente, agosto 2026), dentro
 * `Pictures/GM Trekking/<sottocartella>/` — a differenza dell'archivio
 * privato usato da [PhotoStorage] (pensato solo per l'uso interno dell'app,
 * non raggiungibile da Galleria/Gestione file/PC), queste copie restano
 * visibili e accessibili con qualunque app del telefono, esattamente come le
 * foto scattate normalmente con la fotocamera.
 *
 * Va chiamata DOPO che il percorso è già stato salvato in [ActivityStorage]
 * (vedi `MainMapScreen.kt`, `onStop`): un fallimento qui non deve mai far
 * perdere il percorso registrato né bloccare l'utente — ogni singola copia è
 * avvolta in `runCatching` e un fallimento (spazio esaurito, permesso
 * mancante su Android 9 e precedenti, file foto già cancellato) viene
 * semplicemente ignorato, senza propagare un'eccezione né mostrare un
 * errore: stesso principio "arricchimento facoltativo, mai un blocco" già
 * seguito per l'instradamento reale (vedi RoutingRepository.kt).
 *
 * **Sottocartella**: nome basato sulla data del percorso (`yyyy-MM-dd`), con
 * in aggiunta la località (se il reverse geocoding dal primo punto del
 * percorso riesce — richiede tipicamente connessione dati, quindi spesso
 * assente in montagna: caso normale, non un errore) e, solo se nello stesso
 * giorno risultano più percorsi salvati, un numero progressivo in più per
 * non sovrascrivere la cartella di un percorso precedente dello stesso
 * giorno (richiesto esplicitamente).
 */
object PhotoBackup {

    private const val MASTER_FOLDER_NAME = "GM Trekking"

    suspend fun backupActivityPhotos(
        context: Context,
        activity: CompletedActivity,
        allActivities: List<CompletedActivity>,
    ) = withContext(Dispatchers.IO) {
        val photoFileNames = activity.waypoints.mapNotNull { it.photoFileName }
        if (photoFileNames.isEmpty()) return@withContext

        // Su Android 9 e precedenti, senza il permesso non c'è modo di
        // scrivere nella galleria pubblica: si salta silenziosamente,
        // l'utente ha comunque le foto nell'archivio privato dell'app
        // (Cronologia, vedi ActivityDetailScreen.kt).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            !LocationPermissions.hasLegacyWriteExternalStoragePermission(context)
        ) {
            return@withContext
        }

        val subFolder = subFolderName(context, activity, allActivities)
        photoFileNames.forEachIndexed { index, fileName ->
            runCatching {
                val sourceFile = PhotoStorage.photoFile(context, fileName)
                if (!sourceFile.exists()) return@runCatching
                copyToPublicGallery(context, sourceFile, subFolder, "foto_${index + 1}.jpg")
            }
        }
    }

    private suspend fun subFolderName(
        context: Context,
        activity: CompletedActivity,
        allActivities: List<CompletedActivity>,
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val date = dateFormat.format(Date(activity.startTimeMillis))

        // Percorsi dello stesso giorno, in ordine di inizio: serve per capire
        // se questo è l'unico percorso di oggi (nessun numero progressivo
        // necessario) o il secondo/terzo... (serve un elemento in più per
        // non far finire le foto di percorsi diversi nella stessa cartella).
        val sameDay = allActivities
            .filter { dateFormat.format(Date(it.startTimeMillis)) == date }
            .sortedBy { it.startTimeMillis }
        val position = sameDay.indexOfFirst { it.id == activity.id }
            .let { if (it >= 0) it else sameDay.size } + 1

        val firstPoint = activity.points.firstOrNull()
        val location = firstPoint?.let {
            runCatching { ReverseGeocoder.addressFor(context, it.latitude, it.longitude) }.getOrNull()
        }
            ?.substringBefore(",")
            ?.let(::sanitizeForFolderName)
            ?.takeIf { it.isNotBlank() }

        return buildString {
            append(date)
            if (location != null) append("_").append(location)
            if (sameDay.size > 1) append("_").append(position)
        }
    }

    /** Rimuove caratteri non sicuri per un nome di cartella/file su tutti i filesystem comuni. */
    private fun sanitizeForFolderName(text: String): String =
        text.trim()
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .replace(" ", "_")
            .take(40)

    private fun copyToPublicGallery(context: Context, sourceFile: File, subFolder: String, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // "Scoped storage": nessun permesso necessario per scrivere un
            // nuovo file multimediale proprio in una raccolta pubblica.
            val relativePath = "${Environment.DIRECTORY_PICTURES}/$MASTER_FOLDER_NAME/$subFolder"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
        } else {
            // Pre-scoped-storage: scrittura diretta nella cartella pubblica
            // (permesso già verificato dal chiamante), poi MediaScannerConnection
            // per farla comparire subito in Galleria invece di aspettare la
            // prossima scansione automatica del sistema.
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(picturesDir, "$MASTER_FOLDER_NAME/$subFolder").apply { mkdirs() }
            val destFile = File(dir, displayName)
            sourceFile.inputStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("image/jpeg"), null)
        }
    }
}
