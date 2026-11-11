package io.github.bassboost1981.gear360bridge.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Verzeichnis der abgelegten Fotos und der offenen Aufnahmen, gespeichert als eine JSON-Datei. */
class Catalog(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var data = load()

    private val _photos = MutableStateFlow(data.photos)
    private val _pending = MutableStateFlow(data.pending)
    val photos: StateFlow<List<PhotoRecord>> = _photos.asStateFlow()
    val pending: StateFlow<List<PendingCapture>> = _pending.asStateFlow()

    suspend fun putPhoto(r: PhotoRecord) = change { it.copy(photos = it.photos.upsert(r) { p -> p.id }) }

    suspend fun removePhoto(id: String) = change { it.copy(photos = it.photos.filterNot { p -> p.id == id }) }

    suspend fun putPending(p: PendingCapture) = change { it.copy(pending = it.pending.upsert(p) { c -> c.id }) }

    suspend fun removePending(id: String) = change { it.copy(pending = it.pending.filterNot { c -> c.id == id }) }

    private suspend fun change(f: (CatalogData) -> CatalogData) = mutex.withLock {
        val next = f(data)
        withContext(Dispatchers.IO) { save(next) }
        data = next
        _photos.value = next.photos
        _pending.value = next.pending
    }

    private fun load(): CatalogData {
        if (!file.exists()) return CatalogData()
        return try {
            json.decodeFromString<CatalogData>(file.readText())
        } catch (e: Exception) {
            // Unlesbare Datei nicht ueberschreiben, sondern beiseitelegen.
            file.copyTo(File(file.path + ".bak"), overwrite = true)
            CatalogData()
        }
    }

    /** Erst vollstaendig in eine Nachbardatei schreiben, dann ersetzen. */
    private fun save(next: CatalogData) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(next))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> =
        if (any { key(it) == key(item) }) map { if (key(it) == key(item)) item else it } else this + item
}
