package io.github.bassboost1981.gear360bridge.share

import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packt Bilder so in ein ZIP, dass die Ordnerstruktur beim Empfaenger erhalten bleibt. */
object ShareZipper {
    fun entryName(p: PhotoRecord): String = (p.folder + p.fileName).joinToString("/")

    /** @return Anzahl der Bilder, die nicht mehr lesbar waren und deshalb fehlen */
    fun zip(photos: List<PhotoRecord>, open: (String) -> InputStream, out: OutputStream): Int {
        var skipped = 0
        ZipOutputStream(out, Charsets.UTF_8).use { zip ->
            // JPEG ist bereits komprimiert; ohne erneute Kompression geht es schneller.
            zip.setLevel(0)
            for (photo in photos) {
                // Ein Bild, das ausserhalb der App geloescht wurde, darf nicht die ganze Baustelle blockieren.
                val input = try {
                    open(photo.storeUri)
                } catch (e: Exception) {
                    skipped++
                    continue
                }
                zip.putNextEntry(ZipEntry(entryName(photo)))
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return skipped
    }
}
