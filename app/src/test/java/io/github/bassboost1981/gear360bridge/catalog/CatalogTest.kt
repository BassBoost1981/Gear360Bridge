package io.github.bassboost1981.gear360bridge.catalog

import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val label = PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", "5", "Bad")
    private val photo = PhotoRecord(
        id = "p1", label = label, folder = listOf("Musterstraße 12", "2026-11-10", "2. OG", "Whg 5"),
        fileName = "Bad.jpg", storeUri = "content://x/1", takenAtMillis = 1_762_800_000_000,
    )
    private val pending = PendingCapture(id = "c1", label = label, triggeredAtMillis = 1_762_800_000_000)

    private fun file() = File(tmp.root, "catalog.json")

    @Test fun catalog_missingFile_startsEmpty() {
        val catalog = Catalog(file())
        assertTrue(catalog.photos.value.isEmpty())
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun catalog_persistsAcrossReopen() = runBlocking {
        val catalog = Catalog(file())
        catalog.putPhoto(photo)
        catalog.putPending(pending.copy(cameraUri = "/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG", cameraSize = 8298241, cameraDate = "2016:01:01 00:14:50+0100"))
        val reopened = Catalog(file())
        assertEquals(listOf(photo), reopened.photos.value)
        assertEquals("/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG", reopened.pending.value.single().cameraUri)
        assertEquals(8298241L, reopened.pending.value.single().cameraSize)
    }

    @Test fun catalog_putPhoto_sameId_replaces() = runBlocking {
        val catalog = Catalog(file())
        catalog.putPhoto(photo)
        catalog.putPhoto(photo.copy(fileName = "Küche.jpg"))
        assertEquals(listOf("Küche.jpg"), catalog.photos.value.map { it.fileName })
    }

    @Test fun catalog_putPending_sameId_replaces() = runBlocking {
        val catalog = Catalog(file())
        catalog.putPending(pending)
        catalog.putPending(pending.copy(cameraUri = "/a.jpg"))
        assertEquals(listOf("/a.jpg"), catalog.pending.value.map { it.cameraUri })
    }

    @Test fun catalog_remove() = runBlocking {
        val catalog = Catalog(file())
        catalog.putPhoto(photo)
        catalog.putPending(pending)
        catalog.removePhoto("p1")
        catalog.removePending("c1")
        val reopened = Catalog(file())
        assertTrue(reopened.photos.value.isEmpty())
        assertTrue(reopened.pending.value.isEmpty())
    }

    @Test fun catalog_corruptFile_startsEmpty_andKeepsBackup() = runBlocking {
        file().writeText("{{{")
        val catalog = Catalog(file())
        assertTrue(catalog.photos.value.isEmpty())
        assertEquals("{{{", File(tmp.root, "catalog.json.bak").readText())
        catalog.putPhoto(photo)
        assertEquals(listOf(photo), Catalog(file()).photos.value)
    }
}
