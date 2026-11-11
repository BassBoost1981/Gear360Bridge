package io.github.bassboost1981.gear360bridge.storage

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Laeuft auf einem echten Geraet und legt Testbilder unter Bilder/Gear360Bridge/TEST-... ab. */
@RunWith(AndroidJUnit4::class)
class MediaStorePhotoStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = MediaStorePhotoStore(context)
    private val site = "TEST-${System.nanoTime()}"
    private val folder = listOf(site, "2026-11-10", "2. OG", "Whg 5")
    private val other = listOf(site, "2026-11-10", "3. OG", "Whg 5")
    private val created = mutableListOf<String>()
    private val takenAt = 1_762_800_000_000L

    @After fun cleanUp() = runBlocking {
        created.forEach { runCatching { store.delete(it) } }
    }

    /** Kleines JPEG mit derselben 360-Grad-Kennzeichnung (XMP GPano) wie die Bilder der Kamera. */
    private fun source(): File {
        val jpeg = ByteArrayOutputStream().also {
            Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()
        val xmp = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() +
            ("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
                "<rdf:Description xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\">" +
                "<GPano:ProjectionType>equirectangular</GPano:ProjectionType>" +
                "</rdf:Description></rdf:RDF></x:xmpmeta>").toByteArray()
        val length = xmp.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + xmp
        val file = File.createTempFile("src", ".jpg", context.cacheDir)
        file.writeBytes(jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size))
        return file
    }

    private suspend fun save(f: List<String>, name: String): String =
        store.save(f, name, takenAt, source()).also { created += it }

    @Test fun save_putsFileInExpectedFolder() = runBlocking {
        val uri = save(folder, "Bad.jpg")
        assertEquals(setOf("Bad.jpg"), store.namesIn(folder))
        assertTrue(store.namesIn(other).isEmpty())
        val columns = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(Uri.parse(uri), columns, null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("Pictures/Gear360Bridge/$site/2026-11-10/2. OG/Whg 5/", it.getString(0))
            assertEquals("Bad.jpg", it.getString(1))
        }
    }

    @Test fun save_keeps360Marker_andSetsDate() = runBlocking {
        val uri = save(folder, "Bad.jpg")
        val bytes = store.open(uri).use { it.readBytes() }
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("GPano:ProjectionType>equirectangular"))
        val expected = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(takenAt))
        val exif = store.open(uri).use { ExifInterface(it) }
        assertEquals(expected, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test fun save_failure_leavesNoEntry() = runBlocking {
        try {
            store.save(folder, "Bad.jpg", takenAt, File(context.cacheDir, "does-not-exist.jpg"))
            fail("expected an exception")
        } catch (e: Exception) {
        }
        assertTrue(store.namesIn(folder).isEmpty())
    }

    @Test fun save_sameNameTwice_throws() = runBlocking {
        save(folder, "Bad.jpg")
        try {
            save(folder, "bad.JPG")
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
        }
        assertEquals(setOf("Bad.jpg"), store.namesIn(folder))
    }

    @Test fun move_changesFolderAndName() = runBlocking {
        val uri = save(folder, "Bad.jpg")
        store.move(uri, other, "Küche.jpg")
        assertTrue(store.namesIn(folder).isEmpty())
        assertEquals(setOf("Küche.jpg"), store.namesIn(other))
        assertTrue(store.open(uri).use { it.readBytes() }.isNotEmpty())
    }

    @Test fun move_ontoExistingName_throws_andKeepsBoth() = runBlocking {
        val a = save(folder, "Bad.jpg")
        save(other, "Bad.jpg")
        try {
            store.move(a, other, "Bad.jpg")
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
        }
        assertEquals(setOf("Bad.jpg"), store.namesIn(folder))
        assertEquals(setOf("Bad.jpg"), store.namesIn(other))
    }

    @Test fun delete_removesFile() = runBlocking {
        val uri = save(folder, "Bad.jpg")
        store.delete(uri)
        assertTrue(store.namesIn(folder).isEmpty())
    }
}
