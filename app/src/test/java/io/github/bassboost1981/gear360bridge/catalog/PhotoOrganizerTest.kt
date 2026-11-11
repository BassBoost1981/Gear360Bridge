package io.github.bassboost1981.gear360bridge.catalog

import io.github.bassboost1981.gear360bridge.FakePhotoStore
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PhotoOrganizerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val store = FakePhotoStore()
    private lateinit var catalog: Catalog
    private lateinit var organizer: PhotoOrganizer
    private var settings = FilingSettings(useFlat = true)
    private val label = PhotoLabel("Musterstraße 12", "2026-11-10", "1. OG", "5", "Bad")

    @Before fun setUp() {
        catalog = Catalog(File(tmp.root, "catalog.json"))
        organizer = PhotoOrganizer(store, catalog) { settings }
    }

    private fun source(content: String = "jpeg"): File = tmp.newFile().apply { writeText(content) }

    private suspend fun file(l: PhotoLabel = label, content: String = "jpeg") = organizer.file(l, 1000L, source(content))

    /** Verzeichnis und Speicher muessen fuer jedes Bild denselben Ort nennen. */
    private fun assertInSync() {
        assertEquals(catalog.photos.value.size, store.entries.size)
        catalog.photos.value.forEach {
            val entry = store.entries.getValue(it.storeUri)
            assertEquals(it.folder, entry.folder)
            assertEquals(it.fileName, entry.name)
        }
    }

    @Test fun file_twoPhotosSameRoom_getBadAndBad2() = runBlocking {
        val a = file()
        val b = file()
        assertEquals("Bad.jpg", a.fileName)
        assertEquals("Bad_2.jpg", b.fileName)
        assertInSync()
    }

    @Test fun file_usesFolderFromSettings_andKeepsContent() = runBlocking {
        val record = file(content = "abc")
        assertEquals(listOf("Musterstraße 12", "2026-11-10", "1. OG", "Whg 5"), record.folder)
        assertEquals(label, record.label)
        assertEquals(1000L, record.takenAtMillis)
        assertEquals("abc", store.open(record.storeUri).readBytes().decodeToString())
        assertEquals(listOf(record), catalog.photos.value)
    }

    @Test fun move_threePhotos_toOtherFloor() = runBlocking {
        val ids = listOf(file(), file(label.copy(room = "Küche")), file(label.copy(room = "Flur"))).map { it.id }
        assertEquals(3, organizer.move(ids, LabelChange(floor = "2. OG")))
        catalog.photos.value.forEach {
            assertEquals("2. OG", it.label.floor)
            assertEquals(listOf("Musterstraße 12", "2026-11-10", "2. OG", "Whg 5"), it.folder)
        }
        assertEquals(listOf("Bad.jpg", "Küche.jpg", "Flur.jpg"), catalog.photos.value.map { it.fileName })
        assertInSync()
    }

    @Test fun move_nameTakenInTarget_getsNextFreeNumber() = runBlocking {
        val inTarget = file(label.copy(floor = "2. OG"), content = "target")
        val moving = file(content = "moving")
        organizer.move(listOf(moving.id), LabelChange(floor = "2. OG"))
        val moved = catalog.photos.value.single { it.id == moving.id }
        assertEquals("Bad_2.jpg", moved.fileName)
        assertEquals("target", store.open(inTarget.storeUri).readBytes().decodeToString())
        assertEquals("moving", store.open(moved.storeUri).readBytes().decodeToString())
        assertInSync()
    }

    @Test fun move_toSamePlace_changesNothing() = runBlocking {
        val a = file()
        assertEquals(0, organizer.move(listOf(a.id), LabelChange(floor = "1. OG")))
        assertEquals(listOf(a), catalog.photos.value)
    }

    @Test fun move_failureMidBatch_keepsCatalogAndStoreInSync() = runBlocking {
        val ids = listOf(file(), file(label.copy(room = "Küche")), file(label.copy(room = "Flur"))).map { it.id }
        store.failMoveAfter = 1
        try {
            organizer.move(ids, LabelChange(floor = "2. OG"))
            fail("expected IOException")
        } catch (e: IOException) {
        }
        assertEquals(listOf("2. OG", "1. OG", "1. OG"), catalog.photos.value.map { it.label.floor })
        assertInSync()
    }

    @Test fun rename_changesRoomAndFileName() = runBlocking {
        val a = file()
        organizer.rename(a.id, "Küche")
        val renamed = catalog.photos.value.single()
        assertEquals("Küche", renamed.label.room)
        assertEquals("Küche.jpg", renamed.fileName)
        assertEquals(a.folder, renamed.folder)
        assertInSync()
    }

    @Test fun delete_removesFromStoreAndCatalog() = runBlocking {
        val a = file()
        organizer.delete(a.id)
        assertTrue(catalog.photos.value.isEmpty())
        assertTrue(store.entries.isEmpty())
    }
}
