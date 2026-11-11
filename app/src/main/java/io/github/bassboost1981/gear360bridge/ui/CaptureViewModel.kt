package io.github.bassboost1981.gear360bridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bassboost1981.gear360bridge.R
import io.github.bassboost1981.gear360bridge.capture.CaptureFailure
import io.github.bassboost1981.gear360bridge.capture.CaptureState
import io.github.bassboost1981.gear360bridge.capture.LinkState
import io.github.bassboost1981.gear360bridge.capture.RecoveryResult
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import io.github.bassboost1981.gear360bridge.catalog.Selection
import io.github.bassboost1981.gear360bridge.catalog.SettingsRepository
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import io.github.bassboost1981.gear360bridge.filing.PathBuilder
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUi(
    val connected: Boolean = false,
    val batteryPercent: Int? = null,
    val site: String = "",
    val floor: String = "",
    val flat: String = "",
    val room: String = "",
    val rooms: List<String> = CaptureViewModel.DEFAULT_ROOMS,
    val filing: FilingSettings = FilingSettings(),
    val phase: CaptureState = CaptureState.Idle,
    val canShoot: Boolean = false,
    val messageRes: Int? = null,
    /** Zielordner und Dateiname des naechsten Fotos in Klartext. */
    val target: String = "",
    /** Das zuletzt gespeicherte Foto, solange es nicht verworfen oder bestaetigt wurde. */
    val lastPhoto: PhotoRecord? = null,
    /** Im Hintergrund nachgeholte oder nicht mehr auffindbare Fotos, bis die Meldung bestaetigt ist. */
    val recovery: RecoveryResult? = null,
)

class CaptureViewModel(
    link: StateFlow<LinkState>,
    phase: StateFlow<CaptureState>,
    recovery: StateFlow<RecoveryResult?>,
    val acknowledgeRecovery: () -> Unit,
    private val settings: SettingsRepository,
    private val organizer: PhotoOrganizer,
    private val startCapture: (PhotoLabel, Int) -> Unit,
    private val cancelCapture: () -> Unit,
    private val today: () -> String,
) : ViewModel() {
    /** Was nur diesen Bildschirm betrifft: das bereits bestaetigte Ergebnis. */
    // Bewusst keine data class: zwei gleich aussehende Fehlschlaege sind zwei Ereignisse. Mit
    // Wertvergleich wuerde das zweite Bestaetigen verschluckt und die Meldung liesse sich nicht schliessen.
    private class Local(val dismissed: CaptureState? = null)

    private val local = MutableStateFlow(Local())

    val ui: StateFlow<CaptureUi> =
        combine(link, phase, settings.filing, settings.selection, local) { link, phase, filing, selection, local ->
            build(link, phase, filing, selection, local)
        }.combine(recovery) { ui, recovery -> ui.copy(recovery = recovery) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CaptureUi())

    private fun build(link: LinkState, phase: CaptureState, filing: FilingSettings, selection: Selection, local: Local): CaptureUi {
        val label = label(selection, filing)
        val handled = phase === local.dismissed
        return CaptureUi(
            connected = link.connected,
            batteryPercent = link.batteryPercent,
            site = selection.site,
            floor = selection.floor,
            flat = selection.flat,
            room = selection.room,
            rooms = (DEFAULT_ROOMS + selection.customRooms).distinct(),
            filing = filing,
            phase = phase,
            canShoot = link.connected && selection.site.isNotBlank() && !phase.isRunning(),
            messageRes = (phase as? CaptureState.Failed)?.takeIf { !handled }?.let { messageFor(it.reason) },
            target = (PathBuilder.folder(label, filing) + "${PathBuilder.baseName(label, filing)}.jpg").joinToString(" / "),
            lastPhoto = (phase as? CaptureState.Saved)?.takeIf { !handled }?.photo,
        )
    }

    /**
     * Abgeschaltete Angaben kommen gar nicht erst ins Foto. Sonst truege es unsichtbar ein altes
     * Stockwerk mit sich, das beim spaeteren Verschieben ploetzlich als Ordner auftauchen wuerde.
     */
    private fun label(selection: Selection, filing: FilingSettings) = PhotoLabel(
        site = selection.site.trim(),
        date = today(),
        floor = selection.floor.trim().takeIf { filing.useFloor && it.isNotEmpty() },
        flat = selection.flat.trim().takeIf { filing.useFlat && it.isNotEmpty() },
        room = selection.room.trim().takeIf { filing.useRoom && it.isNotEmpty() },
    )

    private fun CaptureState.isRunning() =
        this is CaptureState.Countdown || this == CaptureState.CameraWorking || this == CaptureState.Downloading

    fun setSite(v: String) = select { it.copy(site = v) }
    fun setFloor(v: String) = select { it.copy(floor = v) }
    fun setFlat(v: String) = select { it.copy(flat = v) }
    // Der Raum wird wie Baustelle und Stockwerk gespeichert, damit er einen Neustart der App uebersteht.
    fun setRoom(v: String) = select { it.copy(room = v) }

    private fun select(f: (Selection) -> Selection) {
        viewModelScope.launch { settings.updateSelection(f) }
    }

    fun shoot() {
        val now = ui.value
        if (!now.canShoot) return
        val room = now.room.trim()
        if (room.isNotEmpty() && room !in now.rooms) select { it.copy(customRooms = it.customRooms + room) }
        startCapture(label(Selection(now.site, now.floor, now.flat, now.room), now.filing), now.filing.delaySeconds)
    }

    fun cancel() = cancelCapture()

    /** Loescht das gerade gespeicherte Foto wieder. */
    fun discardLast() {
        val photo = ui.value.lastPhoto ?: return
        dismiss()
        viewModelScope.launch { organizer.delete(photo.id) }
    }

    /** Blendet Meldung bzw. Vorschau des letzten Ergebnisses aus. */
    fun dismiss() {
        local.value = Local(ui.value.phase)
    }

    companion object {
        val DEFAULT_ROOMS = listOf(
            "Bad", "WC", "Küche", "Flur", "Wohnzimmer", "Schlafzimmer", "Kinderzimmer",
            "Abstellraum", "Balkon", "Keller", "Technikraum", "Treppenhaus",
        )

        fun messageFor(f: CaptureFailure): Int = when (f) {
            CaptureFailure.CameraUnreachable -> R.string.msg_camera_unreachable
            CaptureFailure.CameraRefused -> R.string.msg_camera_refused
            CaptureFailure.DownloadInterrupted -> R.string.msg_download_interrupted
            CaptureFailure.Timeout -> R.string.msg_timeout
            CaptureFailure.StorageFailed -> R.string.msg_storage_failed
        }
    }
}
