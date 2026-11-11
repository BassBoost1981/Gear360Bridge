package io.github.bassboost1981.gear360bridge.ui

import io.github.bassboost1981.gear360bridge.FakePhotoStore
import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.LabelChange
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import io.github.bassboost1981.gear360bridge.share.ShareZipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val store = FakePhotoStore()
    private lateinit var catalog: Catalog
    private lateinit var organizer: PhotoOrganizer
    private lateinit var vm: OverviewViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        catalog = Catalog(File(tmp.root, "catalog.json"))
        organizer = PhotoOrganizer(store, catalog) { FilingSettings() }
        vm = OverviewViewModel(catalog, organizer, store, tmp.newFolder("cache"))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(site: String, room: String, floor: String = "EG") =
        organizer.file(PhotoLabel(site, "2026-11-10", floor, null, room), 1L, tmp.newFile().apply { writeText(room) })

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

    @Test fun sites_areDistinctAndSorted() {
        await { add("Zeppelinweg 3", "Bad"); add("Musterstraße 12", "Bad"); add("Musterstraße 12", "Küche") }
        assertEquals(listOf("Musterstraße 12", "Zeppelinweg 3"), await { vm.sites.first { it.size == 2 } })
    }

    @Test fun photosOf_sortedByFolderThenName() {
        await { add("A", "Küche", "1. OG"); add("A", "Bad", "EG"); add("A", "Flur", "1. OG"); add("B", "Bad") }
        val photos = await { vm.photosOf("A").first { it.size == 3 } }
        assertEquals(listOf("1. OG/Flur.jpg", "1. OG/Küche.jpg", "EG/Bad.jpg"), photos.map { it.folder.last() + "/" + it.fileName })
    }

    @Test fun moveSelected_movesAndClearsSelection() {
        val a = await { add("A", "Bad") }
        val b = await { add("A", "Küche") }
        vm.toggle(a.id)
        vm.toggle(b.id)
        assertEquals(setOf(a.id, b.id), vm.selected.value)
        vm.moveSelected(LabelChange(floor = "2. OG"))
        await { catalog.photos.first { all -> all.all { it.label.floor == "2. OG" } } }
        await { vm.selected.first { it.isEmpty() } }
    }

    @Test fun openingASite_clearsSelectionFromAnotherSite() {
        val a = await { add("A", "Bad") }
        await { add("B", "Bad") }
        vm.open("A")
        vm.toggle(a.id)
        vm.open("B")
        assertTrue(vm.selected.value.isEmpty())
        assertEquals("B", vm.openSite.value)
    }

    @Test fun deleteSelected_neverTouchesPhotosOutsideTheOpenSite() {
        val a = await { add("A", "Bad") }
        val b = await { add("B", "Bad") }
        vm.open("B")
        vm.toggle(a.id) // veraltete Auswahl aus einer anderen Baustelle
        vm.toggle(b.id)
        vm.deleteSelected()
        assertEquals(listOf("A"), await { catalog.photos.first { it.size == 1 } }.map { it.label.site })
    }

    @Test fun closingASite_clearsSelection() {
        val a = await { add("A", "Bad") }
        vm.open("A")
        vm.toggle(a.id)
        vm.close()
        assertTrue(vm.selected.value.isEmpty())
        assertEquals(null, vm.openSite.value)
    }

    @Test fun toggle_twice_deselects() {
        val a = await { add("A", "Bad") }
        vm.toggle(a.id)
        vm.toggle(a.id)
        assertTrue(vm.selected.value.isEmpty())
    }

    @Test fun deleteSelected_removesPhotos() {
        val a = await { add("A", "Bad") }
        await { add("A", "Küche") }
        vm.toggle(a.id)
        vm.deleteSelected()
        assertEquals(listOf("Küche.jpg"), await { catalog.photos.first { it.size == 1 } }.map { it.fileName })
        assertEquals(1, store.entries.size)
    }

    @Test fun moveFails_setsErrorFlag() {
        val a = await { add("A", "Bad") }
        store.failMoveAfter = 0
        vm.toggle(a.id)
        vm.moveSelected(LabelChange(floor = "2. OG"))
        assertTrue(await { vm.moveFailed.first { it } })
    }

    @Test fun shareNeedsWarning_onlyWhileCameraConnected() {
        var connected = true
        val vm = OverviewViewModel(catalog, organizer, store, tmp.newFolder("cache2")) { connected }
        assertTrue(vm.shareNeedsWarning)
        connected = false
        assertTrue(!vm.shareNeedsWarning)
    }

    @Test fun shareNeedsWarning_falseByDefault() {
        assertTrue(!vm.shareNeedsWarning)
    }

    @Test fun buildShareZip_containsPhotosWithFolders() {
        val a = await { add("Musterstraße 12", "Bad") }
        val zip = await { vm.buildShareZip(listOf(a)) }
        assertEquals("Musterstraße 12.zip", zip.name)
        ZipFile(zip).use { assertEquals(listOf(ShareZipper.entryName(a)), it.entries().toList().map { e -> e.name }) }
    }
}
