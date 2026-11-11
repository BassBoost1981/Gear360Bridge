package io.github.bassboost1981.gear360bridge.catalog

import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PathBuilder
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import io.github.bassboost1981.gear360bridge.storage.PhotoStore
import java.io.File
import java.util.UUID

/** Was beim Verschieben geaendert wird; null heisst unveraendert. */
data class LabelChange(
    val site: String? = null,
    val floor: String? = null,
    val flat: String? = null,
    val room: String? = null,
)

/** Legt Fotos ab und haelt dabei Bildspeicher und Verzeichnis im Gleichschritt. */
class PhotoOrganizer(
    private val store: PhotoStore,
    private val catalog: Catalog,
    private val settings: suspend () -> FilingSettings,
) {
    suspend fun file(label: PhotoLabel, takenAtMillis: Long, source: File): PhotoRecord {
        val s = settings()
        val folder = PathBuilder.folder(label, s)
        val fileName = PathBuilder.fileName(PathBuilder.baseName(label, s), store.namesIn(folder))
        val uri = store.save(folder, fileName, takenAtMillis, source)
        val record = PhotoRecord(UUID.randomUUID().toString(), label, folder, fileName, uri, takenAtMillis)
        catalog.putPhoto(record)
        return record
    }

    /**
     * Verschiebt ein Bild nach dem anderen: erst die Datei, dann der Eintrag. Scheitert eine
     * Datei, bleiben alle bisherigen verschoben und der Rest unveraendert.
     * @return Anzahl der geaenderten Bilder
     */
    suspend fun move(ids: List<String>, change: LabelChange): Int {
        val s = settings()
        var moved = 0
        for (id in ids) {
            val photo = catalog.photos.value.firstOrNull { it.id == id } ?: continue
            val label = photo.label.copy(
                site = change.site ?: photo.label.site,
                floor = change.floor ?: photo.label.floor,
                flat = change.flat ?: photo.label.flat,
                room = change.room ?: photo.label.room,
            )
            if (label == photo.label) continue
            val folder = PathBuilder.folder(label, s)
            val sameFolder = folder == photo.folder
            val taken = store.namesIn(folder).filterNot { sameFolder && it == photo.fileName }.toSet()
            val keepName = label.room == photo.label.room && taken.none { it.equals(photo.fileName, ignoreCase = true) }
            val fileName = if (keepName) photo.fileName else PathBuilder.fileName(PathBuilder.baseName(label, s), taken)
            if (!sameFolder || fileName != photo.fileName) store.move(photo.storeUri, folder, fileName)
            catalog.putPhoto(photo.copy(label = label, folder = folder, fileName = fileName))
            moved++
        }
        return moved
    }

    suspend fun rename(id: String, newRoom: String) {
        move(listOf(id), LabelChange(room = newRoom))
    }

    suspend fun delete(id: String) {
        val photo = catalog.photos.value.firstOrNull { it.id == id } ?: return
        store.delete(photo.storeUri)
        catalog.removePhoto(id)
    }
}
