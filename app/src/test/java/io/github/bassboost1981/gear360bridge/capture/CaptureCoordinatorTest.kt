package io.github.bassboost1981.gear360bridge.capture

import io.github.bassboost1981.gear360bridge.FakeOsc
import io.github.bassboost1981.gear360bridge.FakePhotoStore
import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.PendingCapture
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import io.github.bassboost1981.gear360bridge.osc.CameraImage
import io.github.bassboost1981.gear360bridge.osc.CommandStatus
import io.github.bassboost1981.gear360bridge.osc.OscException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val osc = FakeOsc()
    private val store = FakePhotoStore()
    private lateinit var catalog: Catalog
    private lateinit var tempDir: File
    private lateinit var coordinator: CaptureCoordinator
    private val label = PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", "5", "Bad")

    @Before fun setUp() {
        catalog = Catalog(File(tmp.root, "catalog.json"))
        tempDir = tmp.newFolder("temp")
        val organizer = PhotoOrganizer(store, catalog) { FilingSettings(useFlat = true) }
        coordinator = CaptureCoordinator(osc, organizer, catalog, tempDir) { 1_762_800_000_000 }
    }

    private fun TestScope.recordStates(): List<CaptureState> {
        val states = mutableListOf<CaptureState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.state.collect { states += it } }
        return states
    }

    private fun failure() = (coordinator.state.value as CaptureState.Failed).reason

    @Test fun countdown_emits10to1_thenTakesPicture() = runTest {
        val states = recordStates()
        coordinator.capture(label, 10)
        assertEquals((10 downTo 1).map { CaptureState.Countdown(it) }, states.filterIsInstance<CaptureState.Countdown>())
        assertEquals(1, osc.takePictureCalls)
        assertTrue(states.indexOf(CaptureState.CameraWorking) > states.indexOf(CaptureState.Countdown(1)))
    }

    @Test fun cancelDuringCountdown_takesNoPicture() = runTest {
        val job = launch { coordinator.capture(label, 10) }
        advanceTimeBy(3_500)
        coordinator.cancelCountdown()
        advanceUntilIdle()
        job.join()
        assertEquals(0, osc.takePictureCalls)
        assertEquals(CaptureState.Idle, coordinator.state.value)
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun happyPath_savesPhoto_andClearsPending() = runTest {
        val states = recordStates()
        coordinator.capture(label, 0)
        val saved = coordinator.state.value as CaptureState.Saved
        assertEquals(listOf(saved.photo), catalog.photos.value)
        assertEquals("Bad.jpg", saved.photo.fileName)
        assertEquals(label, saved.photo.label)
        assertEquals(1_762_800_000_000, saved.photo.takenAtMillis)
        assertEquals(5000, store.entries.values.single().bytes.size)
        assertTrue(catalog.pending.value.isEmpty())
        assertTrue(tempDir.listFiles()!!.isEmpty())
        assertTrue(states.contains(CaptureState.Downloading))
        assertTrue(states.none { it is CaptureState.Countdown })
    }

    @Test fun statusInProgressFirst_thenDone_saves() = runTest {
        repeat(9) { osc.statuses += CommandStatus.InProgress }
        coordinator.capture(label, 0)
        assertTrue(coordinator.state.value is CaptureState.Saved)
    }

    @Test fun downloadFails_keepsPending_noPhoto() = runTest {
        osc.downloadError = OscException.Unreachable(IOException("cut"))
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.DownloadInterrupted, failure())
        val pending = catalog.pending.value.single()
        assertEquals(FakeOsc.OWN_URI, pending.cameraUri)
        assertEquals(5000L, pending.cameraSize)
        assertEquals(FakeOsc.OWN_DATE, pending.cameraDate)
        assertEquals(label, pending.label)
        assertTrue(catalog.photos.value.isEmpty())
        assertTrue(store.entries.isEmpty())
        assertTrue(tempDir.listFiles()!!.isEmpty())
    }

    @Test fun sizeMismatchAfterDownload_isDownloadInterrupted() = runTest {
        osc.deliveredBytes = 2000
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.DownloadInterrupted, failure())
        assertEquals(1, catalog.pending.value.size)
        assertTrue(store.entries.isEmpty())
        assertTrue(tempDir.listFiles()!!.isEmpty())
    }

    @Test fun cameraReportsFailed_isCameraRefused_noPending() = runTest {
        osc.defaultStatus = CommandStatus.Failed("cameraInExclusiveUse")
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.CameraRefused, failure())
        assertTrue(catalog.pending.value.isEmpty())
        assertEquals(0, osc.downloadCalls)
    }

    @Test fun invalidSession_retriesOnceWithNewSession() = runTest {
        osc.takePictureErrors += OscException.CameraFailure("invalidSessionId")
        coordinator.capture(label, 0)
        assertEquals(2, osc.startSessionCalls)
        assertEquals(2, osc.takePictureCalls)
        assertTrue(coordinator.state.value is CaptureState.Saved)
    }

    @Test fun sessionIsClosedAfterEachCapture_soTheNextPhoneCanUseTheCamera() = runTest {
        coordinator.capture(label, 0)
        coordinator.capture(label, 0)
        assertEquals(2, osc.startSessionCalls)
        assertEquals(listOf("SID_1", "SID_2"), osc.closedSessions)
        assertEquals(listOf("Bad.jpg", "Bad_2.jpg"), catalog.photos.value.map { it.fileName })
    }

    @Test fun sessionIsClosedAlsoAfterFailure() = runTest {
        osc.defaultStatus = CommandStatus.Failed("x")
        coordinator.capture(label, 0)
        assertEquals(listOf("SID_1"), osc.closedSessions)
    }

    @Test fun anyCameraFailureOnTrigger_retriesOnceWithFreshSession() = runTest {
        osc.takePictureErrors += OscException.CameraFailure("cameraInExclusiveUse")
        coordinator.capture(label, 0)
        assertEquals(2, osc.startSessionCalls)
        assertTrue(coordinator.state.value is CaptureState.Saved)
    }

    @Test fun cameraFailureTwice_isCameraRefused() = runTest {
        osc.takePictureErrors += OscException.CameraFailure("a")
        osc.takePictureErrors += OscException.CameraFailure("b")
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.CameraRefused, failure())
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun filingFails_isStorageFailed_keepsPending_andDoesNotCrash() = runTest {
        store.failSave = true
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.StorageFailed, failure())
        assertEquals(FakeOsc.OWN_URI, catalog.pending.value.single().cameraUri)
        assertTrue(tempDir.listFiles()!!.isEmpty())
        // Nachholen scheitert weiter, darf aber nichts werfen und nichts verlieren.
        assertEquals(RecoveryResult(0, 0), coordinator.recoverPending())
        assertEquals(1, catalog.pending.value.size)
        store.failSave = false
        assertEquals(RecoveryResult(1, 0), coordinator.recoverPending())
    }

    @Test fun cancelledMidCapture_resetsState_andNextCaptureWorks() = runTest {
        osc.defaultStatus = CommandStatus.InProgress
        val job = launch { coordinator.capture(label, 0) }
        advanceTimeBy(3_500)
        job.cancel()
        job.join()
        assertEquals(CaptureState.Idle, coordinator.state.value)
        assertTrue(catalog.pending.value.isEmpty())
        osc.defaultStatus = CommandStatus.Done(FakeOsc.OWN_URI)
        coordinator.capture(label, 0)
        assertTrue(coordinator.state.value is CaptureState.Saved)
    }

    @Test fun secondCaptureWhileOneRuns_isIgnored() = runTest {
        val first = launch { coordinator.capture(label, 5) }
        advanceTimeBy(1_500)
        coordinator.capture(label, 0)
        advanceUntilIdle()
        first.join()
        assertEquals(1, osc.takePictureCalls)
        assertEquals(1, catalog.photos.value.size)
    }

    @Test fun captureDuringRecovery_waitsInsteadOfBeingDropped() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, FakeOsc.OWN_DATE))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        osc.listGate = gate
        val recovery = launch { coordinator.recoverPending() }
        advanceTimeBy(100)
        val capture = launch { coordinator.capture(label.copy(room = "Küche"), 0) }
        advanceTimeBy(100)
        assertEquals(0, osc.takePictureCalls)
        osc.listGate = null
        gate.complete(Unit)
        advanceUntilIdle()
        recovery.join()
        capture.join()
        assertEquals(1, osc.takePictureCalls)
        assertEquals(setOf("Bad.jpg", "Küche.jpg"), catalog.photos.value.map { it.fileName }.toSet())
    }

    @Test fun recover_emptyCameraList_keepsPending() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, FakeOsc.OWN_DATE))
        osc.images.clear()
        assertEquals(RecoveryResult(0, 0), coordinator.recoverPending())
        assertEquals(1, catalog.pending.value.size)
    }

    @Test fun recover_ownUriMissingFromNonEmptyList_isLost() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, "/mnt/mmc/DCIM/100PHOTO/SAM_0099.JPG", 5000, FakeOsc.OWN_DATE))
        assertEquals(RecoveryResult(0, 1), coordinator.recoverPending())
    }

    @Test fun recover_sizeUnknownButTriggeredRecently_isRecoveredByUri() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1_762_800_000_000 - 60_000, FakeOsc.OWN_URI))
        assertEquals(RecoveryResult(1, 0), coordinator.recoverPending())
        assertEquals(label, catalog.photos.value.single().label)
    }

    @Test fun recover_sizeUnknownAndOld_isLost_notDownloaded() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1_762_800_000_000 - 3_600_000, FakeOsc.OWN_URI))
        assertEquals(RecoveryResult(0, 1), coordinator.recoverPending())
        assertEquals(0, osc.downloadCalls)
    }

    @Test fun statusNeverDone_timesOutAfter60s() = runTest {
        osc.defaultStatus = CommandStatus.InProgress
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.Timeout, failure())
        assertEquals(60_000, testScheduler.currentTime)
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun unreachable_isCameraUnreachable() = runTest {
        osc.unreachable = true
        coordinator.capture(label, 0)
        assertEquals(CaptureFailure.CameraUnreachable, failure())
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun recover_downloadsOwnPendingImage() = runTest {
        osc.downloadError = OscException.Unreachable(IOException("cut"))
        coordinator.capture(label, 0)
        osc.downloadError = null
        assertEquals(RecoveryResult(1, 0), coordinator.recoverPending())
        assertEquals(label, catalog.photos.value.single().label)
        assertTrue(catalog.pending.value.isEmpty())
        assertTrue(tempDir.listFiles()!!.isEmpty())
    }

    @Test fun recover_reportsResultUntilAcknowledged() = runTest {
        assertEquals(null, coordinator.lastRecovery.value)
        osc.downloadError = OscException.Unreachable(IOException("cut"))
        coordinator.capture(label, 0)
        osc.downloadError = null
        coordinator.recoverPending()
        assertEquals(RecoveryResult(1, 0), coordinator.lastRecovery.value)
        coordinator.acknowledgeRecovery()
        assertEquals(null, coordinator.lastRecovery.value)
    }

    @Test fun recover_nothingPending_reportsNothing() = runTest {
        coordinator.recoverPending()
        assertEquals(null, coordinator.lastRecovery.value)
    }

    @Test fun recover_cameraStillGone_reportsNothing() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, FakeOsc.OWN_DATE))
        osc.unreachable = true
        coordinator.recoverPending()
        assertEquals(null, coordinator.lastRecovery.value)
    }

    @Test fun recover_sameUriDifferentSize_isLost_notDownloaded() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 8298241, FakeOsc.OWN_DATE))
        assertEquals(RecoveryResult(0, 1), coordinator.recoverPending())
        assertEquals(0, osc.downloadCalls)
        assertTrue(catalog.pending.value.isEmpty())
        assertTrue(catalog.photos.value.isEmpty())
    }

    @Test fun recover_sameUriDifferentDate_isLost_notDownloaded() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, "2016:01:02 10:00:00+0100"))
        assertEquals(RecoveryResult(0, 1), coordinator.recoverPending())
        assertEquals(0, osc.downloadCalls)
    }

    @Test fun recover_ignoresImagesOfOtherPhones() = runTest {
        osc.images += CameraImage("SAM_0006.JPG", "/mnt/mmc/DCIM/100PHOTO/SAM_0006.JPG", 7000, "2016:01:01 00:20:00+0100")
        osc.images += CameraImage("SAM_0007.JPG", "/mnt/mmc/DCIM/100PHOTO/SAM_0007.JPG", 7100, "2016:01:01 00:21:00+0100")
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, FakeOsc.OWN_DATE))
        assertEquals(RecoveryResult(1, 0), coordinator.recoverPending())
        assertEquals(listOf(FakeOsc.OWN_URI), osc.downloadedUris)
        assertEquals(1, catalog.photos.value.size)
    }

    @Test fun recover_pendingWithoutCameraUri_isLost() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L))
        assertEquals(RecoveryResult(0, 1), coordinator.recoverPending())
        assertTrue(catalog.pending.value.isEmpty())
    }

    @Test fun recover_cameraUnreachable_keepsPending() = runTest {
        catalog.putPending(PendingCapture("c1", label, 1L, FakeOsc.OWN_URI, 5000, FakeOsc.OWN_DATE))
        osc.unreachable = true
        assertEquals(RecoveryResult(0, 0), coordinator.recoverPending())
        assertEquals(1, catalog.pending.value.size)
    }
}
