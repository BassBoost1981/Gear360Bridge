package io.github.bassboost1981.gear360bridge

import io.github.bassboost1981.gear360bridge.storage.PhotoStore
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Bildspeicher im Arbeitsspeicher mit demselben Verhalten wie der echte. */
class FakePhotoStore : PhotoStore {
    data class Entry(val folder: List<String>, val name: String, val bytes: ByteArray, val takenAtMillis: Long)

    val entries = linkedMapOf<String, Entry>()

    /** Nach so vielen erfolgreichen Verschiebungen scheitert die naechste. */
    var failMoveAfter: Int? = null
    private var moves = 0
    private var nextId = 1

    override suspend fun namesIn(folder: List<String>): Set<String> =
        entries.values.filter { it.folder == folder }.map { it.name }.toSet()

    /** Wenn gesetzt, scheitert das Ablegen wie bei vollem Speicher. */
    var failSave = false

    override suspend fun save(folder: List<String>, fileName: String, takenAtMillis: Long, source: File): String {
        if (failSave) throw IOException("no space left")
        check(!taken(folder, fileName)) { "name taken: $fileName" }
        val uri = "fake://${nextId++}"
        entries[uri] = Entry(folder, fileName, source.readBytes(), takenAtMillis)
        return uri
    }

    override suspend fun move(uri: String, newFolder: List<String>, newFileName: String) {
        val entry = entries.getValue(uri)
        failMoveAfter?.let { if (moves >= it) throw IOException("move failed") }
        check(!taken(newFolder, newFileName)) { "name taken: $newFileName" }
        entries[uri] = entry.copy(folder = newFolder, name = newFileName)
        moves++
    }

    override suspend fun delete(uri: String) {
        entries.remove(uri)
    }

    override fun open(uri: String): InputStream = entries.getValue(uri).bytes.inputStream()

    private fun taken(folder: List<String>, name: String) =
        entries.values.any { it.folder == folder && it.name.equals(name, ignoreCase = true) }
}
