package io.github.bassboost1981.gear360bridge.capture

import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.PendingCapture
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import io.github.bassboost1981.gear360bridge.osc.CameraImage
import io.github.bassboost1981.gear360bridge.osc.CommandStatus
import io.github.bassboost1981.gear360bridge.osc.Osc
import io.github.bassboost1981.gear360bridge.osc.OscException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class CaptureFailure { CameraUnreachable, CameraRefused, DownloadInterrupted, Timeout, StorageFailed }

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Countdown(val secondsLeft: Int) : CaptureState
    data object CameraWorking : CaptureState
    data object Downloading : CaptureState
    data class Saved(val photo: PhotoRecord) : CaptureState
    data class Failed(val reason: CaptureFailure) : CaptureState
}

data class RecoveryResult(val recovered: Int, val lost: Int)

/**
 * Ablauf einer Aufnahme: warten, ausloesen, auf die Kamera warten, herunterladen, einsortieren.
 * Jede ausgeloeste Aufnahme wird vorher als offen vermerkt, damit ein Bild, dessen Uebertragung
 * abbricht, spaeter nachgeholt werden kann.
 */
class CaptureCoordinator(
    private val osc: Osc,
    private val organizer: PhotoOrganizer,
    private val catalog: Catalog,
    private val tempDir: File,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _lastRecovery = MutableStateFlow<RecoveryResult?>(null)

    /** Ergebnis des letzten Nachholens, bis es bestaetigt wurde. */
    val lastRecovery: StateFlow<RecoveryResult?> = _lastRecovery.asStateFlow()

    /** Aufnahme und Nachholen sprechen nie gleichzeitig mit der Kamera. */
    private val busy = Mutex()
    private val capturing = AtomicBoolean(false)
    @Volatile private var cancelRequested = false

    /** Ob gerade eine Aufnahme laeuft (auch waehrend sie noch auf ein Nachholen wartet). */
    val isCapturing: Boolean get() = capturing.get()

    private class Interrupted : Exception()
    private class StorageFailure : Exception()
    private class Refused : Exception()
    private class TimedOut : Exception()

    suspend fun capture(label: PhotoLabel, delaySeconds: Int) {
        // Ein zweiter Druck auf den Ausloeser waehrend einer Aufnahme tut nichts.
        if (!capturing.compareAndSet(false, true)) return
        try {
            cancelRequested = false
            _state.value = if (delaySeconds > 0) CaptureState.Countdown(delaySeconds) else CaptureState.CameraWorking
            // Laeuft gerade ein Nachholen, wird gewartet statt die Aufnahme stillschweigend zu verwerfen.
            busy.withLock {
                for (left in delaySeconds downTo 1) {
                    _state.value = CaptureState.Countdown(left)
                    delay(1_000)
                    if (cancelRequested) {
                        _state.value = CaptureState.Idle
                        return
                    }
                }
                _state.value = CaptureState.CameraWorking
                _state.value = shoot(label)
            }
        } catch (e: CancellationException) {
            // Sonst bliebe die Anzeige fuer immer auf "nimmt auf" stehen.
            _state.value = CaptureState.Idle
            throw e
        } finally {
            capturing.set(false)
        }
    }

    /** Wirkt nur waehrend der Wartezeit. */
    fun cancelCountdown() {
        if (_state.value is CaptureState.Countdown) cancelRequested = true
    }

    fun acknowledgeRecovery() {
        _lastRecovery.value = null
    }

    /**
     * Holt eigene Bilder nach, deren Uebertragung frueher abgebrochen ist. Wirft nie: scheitert
     * etwas, bleibt die Aufnahme offen und wird beim naechsten Verbinden erneut versucht.
     */
    suspend fun recoverPending(): RecoveryResult = busy.withLock {
        val open = catalog.pending.value
        if (open.isEmpty()) return RecoveryResult(0, 0)
        val images = try {
            osc.listImages(RECOVERY_LIST_SIZE)
        } catch (e: OscException) {
            return RecoveryResult(0, 0)
        }
        // Eine leere Liste heisst eher "Karte noch nicht bereit" als "alles weg": nichts entscheiden.
        if (images.isEmpty()) return RecoveryResult(0, 0)
        var recovered = 0
        var lost = 0
        for (p in open) {
            try {
                val own = ownImage(p, images)
                if (own == null) {
                    catalog.removePending(p.id)
                    lost++
                    continue
                }
                fetch(p.copy(cameraSize = own.size, cameraDate = own.dateTimeZone))
                recovered++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // bleibt offen, naechster Versuch bei der naechsten Verbindung
            }
        }
        val result = RecoveryResult(recovered, lost)
        // Nachholen passiert im Hintergrund; ohne Meldung wuesste niemand, dass das Bild jetzt da ist.
        if (recovered + lost > 0) _lastRecovery.value = result
        result
    }

    /**
     * Das Bild auf der Kamera, das zu dieser offenen Aufnahme gehoert, oder null. Dateinamen
     * wiederholen sich nach dem Formatieren der Karte, und andere Handys fotografieren mit
     * derselben Kamera; deshalb zaehlen Groesse und Kamera-Datum mit. Sind sie nie bekannt
     * geworden, genuegt der Dateiname nur kurz nach dem Ausloesen.
     */
    private fun ownImage(p: PendingCapture, images: List<CameraImage>): CameraImage? {
        val match = images.firstOrNull { it.uri == p.cameraUri } ?: return null
        return when {
            p.cameraSize != null && p.cameraDate != null ->
                match.takeIf { it.size == p.cameraSize && it.dateTimeZone == p.cameraDate }
            now() - p.triggeredAtMillis <= TRUST_URI_ONLY_MS -> match
            else -> null
        }
    }

    private suspend fun shoot(label: PhotoLabel): CaptureState {
        var pending = PendingCapture(UUID.randomUUID().toString(), label, now())
        var session: String? = null
        try {
            catalog.putPending(pending)
            session = osc.startSession()
            val commandId = try {
                osc.takePicture(session)
            } catch (e: OscException.CameraFailure) {
                // Abgelaufene oder fremde Sitzung: einmal mit einer frischen Sitzung wiederholen.
                closeQuietly(session)
                session = osc.startSession()
                osc.takePicture(session)
            }
            val fileUri = awaitPicture(commandId)
            pending = pending.copy(cameraUri = fileUri)
            catalog.putPending(pending)
            val image = osc.listImages().firstOrNull { it.uri == fileUri }
            pending = pending.copy(cameraSize = image?.size, cameraDate = image?.dateTimeZone)
            catalog.putPending(pending)
            _state.value = CaptureState.Downloading
            return CaptureState.Saved(fetch(pending))
        } catch (e: CancellationException) {
            // Ohne bekannte Datei laesst sich nichts nachholen.
            if (pending.cameraUri == null) withContext(NonCancellable) { runCatching { catalog.removePending(pending.id) } }
            throw e
        } catch (e: Interrupted) {
            return CaptureState.Failed(CaptureFailure.DownloadInterrupted)
        } catch (e: StorageFailure) {
            return CaptureState.Failed(CaptureFailure.StorageFailed)
        } catch (e: TimedOut) {
            return giveUp(pending, CaptureFailure.Timeout)
        } catch (e: OscException.Unreachable) {
            return giveUp(pending, CaptureFailure.CameraUnreachable)
        } catch (e: Refused) {
            return giveUp(pending, CaptureFailure.CameraRefused)
        } catch (e: OscException) {
            return giveUp(pending, CaptureFailure.CameraRefused)
        } catch (e: Exception) {
            // z. B. voller Handy-Speicher beim Schreiben des Verzeichnisses: melden statt abstuerzen.
            return CaptureState.Failed(CaptureFailure.StorageFailed)
        } finally {
            // Sitzung freigeben, damit das naechste Handy die Kamera sofort benutzen kann.
            session?.let { withContext(NonCancellable) { closeQuietly(it) } }
        }
    }

    /** Ist die Datei auf der Kamera schon bekannt, bleibt die Aufnahme offen und wird nachgeholt. */
    private suspend fun giveUp(pending: PendingCapture, reason: CaptureFailure): CaptureState {
        if (pending.cameraUri != null) return CaptureState.Failed(CaptureFailure.DownloadInterrupted)
        runCatching { catalog.removePending(pending.id) }
        return CaptureState.Failed(reason)
    }

    private suspend fun closeQuietly(session: String) {
        try {
            osc.closeSession(session)
        } catch (e: OscException) {
            // Die Sitzung laeuft auf der Kamera ohnehin nach 300 Sekunden ab.
        }
    }

    private suspend fun awaitPicture(commandId: String): String {
        repeat(POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            when (val status = osc.commandStatus(commandId)) {
                is CommandStatus.Done -> return status.fileUri
                is CommandStatus.Failed -> throw Refused()
                CommandStatus.InProgress -> Unit
            }
        }
        throw TimedOut()
    }

    /** Laedt das Bild in eine Zwischendatei, prueft die Groesse und sortiert es erst dann ein. */
    private suspend fun fetch(p: PendingCapture): PhotoRecord {
        tempDir.mkdirs()
        val temp = File(tempDir, "${p.id}.jpg")
        try {
            val bytes = try {
                osc.downloadImage(p.cameraUri!!, temp)
            } catch (e: OscException) {
                throw Interrupted()
            }
            if (p.cameraSize != null && bytes != p.cameraSize) throw Interrupted()
            val record = try {
                organizer.file(p.label, p.triggeredAtMillis, temp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw StorageFailure()
            }
            runCatching { catalog.removePending(p.id) }
            return record
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val POLL_ATTEMPTS = 60
        const val RECOVERY_LIST_SIZE = 200
        const val TRUST_URI_ONLY_MS = 10 * 60_000L
    }
}
