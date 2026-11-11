package io.github.bassboost1981.gear360bridge.storage

import java.io.File
import java.io.InputStream

/**
 * Ablage der Bilder unter Pictures/Gear360Bridge/<folder>/<fileName>.
 * [folder] ist die Ordnerkette ohne den Wurzelordner. URIs sind Strings, damit die
 * Logik darueber ohne Android testbar bleibt.
 */
interface PhotoStore {
    /** Dateinamen, die in diesem Ordner schon vergeben sind. */
    suspend fun namesIn(folder: List<String>): Set<String>

    /** Kopiert [source] an seinen Platz und setzt das Aufnahmedatum. Gibt die URI zurueck. */
    suspend fun save(folder: List<String>, fileName: String, takenAtMillis: Long, source: File): String

    /** @throws IllegalStateException wenn der Zielname im Zielordner schon vergeben ist */
    suspend fun move(uri: String, newFolder: List<String>, newFileName: String)

    suspend fun delete(uri: String)

    fun open(uri: String): InputStream
}
