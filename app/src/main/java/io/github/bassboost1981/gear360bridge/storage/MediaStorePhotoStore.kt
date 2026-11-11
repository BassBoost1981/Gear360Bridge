package io.github.bassboost1981.gear360bridge.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.bassboost1981.gear360bridge.filing.PathBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Legt Bilder ueber den Medienspeicher von Android unter Bilder/Gear360Bridge ab. */
class MediaStorePhotoStore(context: Context) : PhotoStore {
    private val resolver = context.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun relativePath(folder: List<String>): String =
        (listOf("Pictures", PathBuilder.ROOT) + folder).joinToString("/") + "/"

    override suspend fun namesIn(folder: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val names = mutableSetOf<String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativePath(folder)),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        names
    }

    override suspend fun save(folder: List<String>, fileName: String, takenAtMillis: Long, source: File): String =
        withContext(Dispatchers.IO) {
            check(!taken(folder, fileName)) { "name taken: $fileName" }
            stampDate(source, takenAtMillis)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folder))
                put(MediaStore.Images.Media.DATE_TAKEN, takenAtMillis)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
            try {
                resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                // Keine halbe Datei in der Galerie zuruecklassen.
                resolver.delete(uri, null, null)
                throw e
            }
            uri.toString()
        }

    override suspend fun move(uri: String, newFolder: List<String>, newFileName: String) = withContext(Dispatchers.IO) {
        check(!taken(newFolder, newFileName)) { "name taken: $newFileName" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(newFolder))
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
        }
        if (resolver.update(Uri.parse(uri), values, null, null) != 1) throw IOException("move failed")
    }

    override suspend fun delete(uri: String) {
        withContext(Dispatchers.IO) { resolver.delete(Uri.parse(uri), null, null) }
    }

    override fun open(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("cannot open $uri")

    private suspend fun taken(folder: List<String>, name: String): Boolean =
        namesIn(folder).any { it.equals(name, ignoreCase = true) }

    /**
     * Setzt das Aufnahmedatum im Bild auf die Handy-Zeit; die Uhr der Kamera ist nicht verlaesslich.
     * Scheitert das, wird das Bild trotzdem abgelegt: ein Foto mit falschem Datum im Bild ist
     * besser als kein Foto, und der Ordner traegt das richtige Datum.
     */
    private fun stampDate(file: File, takenAtMillis: Long) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(takenAtMillis))
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
                setAttribute(ExifInterface.TAG_DATETIME, stamp)
                saveAttributes()
            }
        }
    }
}
