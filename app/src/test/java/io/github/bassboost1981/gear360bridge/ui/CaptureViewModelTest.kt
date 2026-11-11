package io.github.bassboost1981.gear360bridge.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.bassboost1981.gear360bridge.FakePhotoStore
import io.github.bassboost1981.gear360bridge.capture.CaptureFailure
import io.github.bassboost1981.gear360bridge.capture.CaptureState
import io.github.bassboost1981.gear360bridge.capture.LinkState
import io.github.bassboost1981.gear360bridge.capture.RecoveryResult
import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.catalog.SettingsRepository
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val job = Job()
    private val link = MutableStateFlow(LinkState(connected = true, batteryPercent = 70))
    private val phase = MutableStateFlow<CaptureState>(CaptureState.Idle)
    private val recovery = MutableStateFlow<RecoveryResult?>(null)
    private var acknowledged = 0
    private val started = mutableListOf<Pair<PhotoLabel, Int>>()
    private lateinit var settings: SettingsRepository
    private lateinit var vm: CaptureViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { File(tmp.root, "s.preferences_pb") },
        )
        vm = newViewModel()
    }

    /** Ein neues ViewModel ueber denselben gespeicherten Einstellungen, wie nach einem Neustart der App. */
    private fun newViewModel(): CaptureViewModel {
        val catalog = Catalog(File(tmp.root, "catalog.json"))
        val organizer = PhotoOrganizer(FakePhotoStore(), catalog) { FilingSettings() }
        return CaptureViewModel(
            link = link, phase = phase, recovery = recovery, acknowledgeRecovery = { acknowledged++ },
            settings = settings, organizer = organizer,
            startCapture = { label, delay -> started += label to delay },
            cancelCapture = {},
            today = { "2026-11-10" },
        )
    }

    @Test fun room_survivesRestartOfTheApp() {
        vm.setSite("p5")
        vm.setRoom("Wohnzimmer")
        await { it.site == "p5" && it.room == "Wohnzimmer" }
        val restarted = newViewModel()
        val ui = runBlocking { withTimeout(5_000) { restarted.ui.first { it.site == "p5" } } }
        assertEquals("Wohnzimmer", ui.room)
        restarted.shoot()
        assertEquals("Wohnzimmer", started.single().first.room)
    }

    @After fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    private fun await(condition: (CaptureUi) -> Boolean): CaptureUi =
        runBlocking { withTimeout(5_000) { vm.ui.first(condition) } }

    @Test fun canShoot_falseWhenSiteBlank() {
        assertFalse(await { it.connected }.canShoot)
    }

    @Test fun canShoot_trueWhenConnectedAndSiteSet() {
        vm.setSite("Musterstraße 12")
        assertTrue(await { it.site == "Musterstraße 12" }.canShoot)
    }

    @Test fun canShoot_falseWhenNotConnected() {
        vm.setSite("Musterstraße 12")
        link.value = LinkState(false, null)
        assertFalse(await { it.site == "Musterstraße 12" && !it.connected }.canShoot)
    }

    @Test fun canShoot_falseWhileCapturing() {
        vm.setSite("Musterstraße 12")
        phase.value = CaptureState.Countdown(5)
        assertFalse(await { it.site == "Musterstraße 12" && it.phase == CaptureState.Countdown(5) }.canShoot)
    }

    @Test fun sameFailureTwice_canBeDismissedBothTimes() {
        phase.value = CaptureState.Failed(CaptureFailure.CameraUnreachable)
        await { it.messageRes != null }
        vm.dismiss()
        await { it.messageRes == null }
        phase.value = CaptureState.CameraWorking
        await { it.phase == CaptureState.CameraWorking }
        phase.value = CaptureState.Failed(CaptureFailure.CameraUnreachable)
        await { it.messageRes != null }
        vm.dismiss()
        await { it.messageRes == null }
    }

    @Test fun savedPhoto_previewDisappearsAfterDismiss() = runBlocking {
        val organizer = PhotoOrganizer(FakePhotoStore(), Catalog(File(tmp.root, "c2.json"))) { FilingSettings() }
        val photo = organizer.file(PhotoLabel("p5", "2026-11-10", room = "Bad"), 1L, tmp.newFile().apply { writeText("x") })
        phase.value = CaptureState.Saved(photo)
        assertEquals(photo, await { it.lastPhoto != null }.lastPhoto)
        vm.dismiss()
        await { it.lastPhoto == null }
        Unit
    }

    @Test fun label_leavesOutPartsThatAreSwitchedOff() = runBlocking {
        settings.updateFiling { it.copy(useFloor = false, useRoom = false) }
        vm.setSite("p5")
        vm.setFloor("2. OG")
        vm.setRoom("Bad")
        await { it.site == "p5" && it.floor == "2. OG" && it.room == "Bad" && !it.filing.useFloor }
        vm.shoot()
        assertEquals(PhotoLabel("p5", "2026-11-10"), started.single().first)
    }

    @Test fun failure_mapsToMessage() {
        val messages = CaptureFailure.entries.map { CaptureViewModel.messageFor(it) }
        assertEquals(CaptureFailure.entries.size, messages.toSet().size)
        phase.value = CaptureState.Failed(CaptureFailure.Timeout)
        assertEquals(CaptureViewModel.messageFor(CaptureFailure.Timeout), await { it.messageRes != null }.messageRes)
    }

    @Test fun setFloor_isRememberedInSelection() = runBlocking {
        vm.setFloor("2. OG")
        await { it.floor == "2. OG" }
        assertEquals("2. OG", withTimeout(5_000) { settings.selection.first { it.floor.isNotEmpty() } }.floor)
    }

    @Test fun shoot_startsCaptureWithLabelAndDelay() {
        vm.setSite("Musterstraße 12")
        vm.setFloor("2. OG")
        vm.setRoom("Bad")
        await { it.site == "Musterstraße 12" && it.floor == "2. OG" && it.room == "Bad" }
        vm.shoot()
        assertEquals(listOf(PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", null, "Bad") to 10), started)
    }

    @Test fun shoot_whenNotAllowed_doesNothing() {
        await { it.connected }
        vm.shoot()
        assertTrue(started.isEmpty())
    }

    @Test fun customRoom_isAddedToRooms() {
        vm.setSite("Musterstraße 12")
        vm.setRoom("Sauna")
        await { it.site == "Musterstraße 12" && it.room == "Sauna" }
        vm.shoot()
        val ui = await { "Sauna" in it.rooms }
        assertTrue(ui.rooms.containsAll(CaptureViewModel.DEFAULT_ROOMS))
    }

    @Test fun recovery_isShownAndAcknowledged() {
        recovery.value = RecoveryResult(recovered = 1, lost = 0)
        assertEquals(RecoveryResult(1, 0), await { it.recovery != null }.recovery)
        vm.acknowledgeRecovery()
        assertEquals(1, acknowledged)
    }

    @Test fun uncheckedFlat_isNotInTargetPreview() = runBlocking {
        vm.setSite("Musterstraße 12")
        vm.setFloor("2. OG")
        vm.setFlat("5")
        vm.setRoom("Bad")
        val without = await { it.flat == "5" && it.room == "Bad" && it.floor == "2. OG" }
        assertEquals("Musterstraße 12 / 2026-11-10 / 2. OG / Bad.jpg", without.target)
        settings.updateFiling { it.copy(useFlat = true) }
        assertEquals("Musterstraße 12 / 2026-11-10 / 2. OG / Whg 5 / Bad.jpg", await { it.filing.useFlat }.target)
    }
}
