package io.github.bassboost1981.gear360bridge.share

import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ShareZipperTest {
    private val folder = listOf("Musterstraße 12", "2026-11-10", "2. OG", "Whg 5")
    private fun photo(name: String, uri: String) = PhotoRecord(
        uri, PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", "5", "Bad"), folder, name, uri, 1L,
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().decodeToString()
            }
        }
        return result
    }

    @Test fun entryName_keepsFolderStructure() =
        assertEquals("Musterstraße 12/2026-11-10/2. OG/Whg 5/Bad.jpg", ShareZipper.entryName(photo("Bad.jpg", "u1")))

    @Test fun zip_containsAllPhotosWithContent() {
        val contents = mapOf("u1" to "eins", "u2" to "zwei")
        val out = ByteArrayOutputStream()
        ShareZipper.zip(listOf(photo("Bad.jpg", "u1"), photo("Küche.jpg", "u2")), { contents.getValue(it).byteInputStream() }, out)
        assertEquals(
            mapOf(
                "Musterstraße 12/2026-11-10/2. OG/Whg 5/Bad.jpg" to "eins",
                "Musterstraße 12/2026-11-10/2. OG/Whg 5/Küche.jpg" to "zwei",
            ),
            unzip(out.toByteArray()),
        )
    }

    @Test fun zip_skipsUnreadablePhotos_andReportsHowMany() {
        val out = ByteArrayOutputStream()
        val skipped = ShareZipper.zip(
            listOf(photo("Bad.jpg", "u1"), photo("Weg.jpg", "gone"), photo("Küche.jpg", "u2")),
            { if (it == "gone") throw java.io.FileNotFoundException(it) else it.byteInputStream() },
            out,
        )
        assertEquals(1, skipped)
        assertEquals(
            setOf("Musterstraße 12/2026-11-10/2. OG/Whg 5/Bad.jpg", "Musterstraße 12/2026-11-10/2. OG/Whg 5/Küche.jpg"),
            unzip(out.toByteArray()).keys,
        )
    }

    @Test fun zip_emptyList_producesValidEmptyZip() {
        val out = ByteArrayOutputStream()
        ShareZipper.zip(emptyList(), { error("nothing to open") }, out)
        assertTrue(unzip(out.toByteArray()).isEmpty())
    }
}
