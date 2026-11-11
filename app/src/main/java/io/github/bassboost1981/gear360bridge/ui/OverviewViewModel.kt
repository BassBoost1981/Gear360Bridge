package io.github.bassboost1981.gear360bridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.LabelChange
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import io.github.bassboost1981.gear360bridge.filing.PathBuilder
import io.github.bassboost1981.gear360bridge.share.ShareZipper
import io.github.bassboost1981.gear360bridge.storage.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OverviewViewModel(
    private val catalog: Catalog,
    private val organizer: PhotoOrganizer,
    private val store: PhotoStore,
    private val cacheDir: File,
    private val cameraConnected: () -> Boolean = { false },
) : ViewModel() {
    /** Im Kamera-WLAN gibt es kein Internet; Verschicken per Mail oder Messenger scheitert dort. */
    val shareNeedsWarning: Boolean get() = cameraConnected()

    val sites: StateFlow<List<String>> = catalog.photos
        .map { all -> all.map { it.label.site }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _openSite = MutableStateFlow<String?>(null)

    /** Die gerade geoeffnete Baustelle; Auswahl und Aktionen gelten nur fuer sie. */
    val openSite: StateFlow<String?> = _openSite.asStateFlow()

    fun open(site: String) {
        clearSelection()
        _openSite.value = site
    }

    fun close() {
        clearSelection()
        _openSite.value = null
    }

    /** Nur was in der offenen Baustelle liegt, darf verschoben oder geloescht werden. */
    private fun selectedInOpenSite(): List<String> {
        val site = _openSite.value
        val visible = catalog.photos.value.filter { site == null || it.label.site == site }.map { it.id }.toSet()
        return _selected.value.filter { it in visible }
    }

    private val _moveFailed = MutableStateFlow(false)
    val moveFailed: StateFlow<Boolean> = _moveFailed.asStateFlow()

    fun photosOf(site: String): Flow<List<PhotoRecord>> = catalog.photos.map { all ->
        all.filter { it.label.site == site }.sortedWith(compareBy({ it.folder.joinToString("/") }, { it.fileName }))
    }

    fun toggle(id: String) = _selected.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun dismissMoveFailed() {
        _moveFailed.value = false
    }

    fun moveSelected(change: LabelChange) {
        val ids = selectedInOpenSite()
        clearSelection()
        viewModelScope.launch {
            try {
                organizer.move(ids, change)
            } catch (e: Exception) {
                _moveFailed.value = true
            }
        }
    }

    fun rename(id: String, room: String) {
        clearSelection()
        viewModelScope.launch {
            try {
                organizer.rename(id, room)
            } catch (e: Exception) {
                _moveFailed.value = true
            }
        }
    }

    fun deleteSelected() {
        val ids = selectedInOpenSite()
        clearSelection()
        viewModelScope.launch { ids.forEach { organizer.delete(it) } }
    }

    /** Wie viele Bilder beim letzten Teilen fehlten, weil sie nicht mehr auf dem Handy liegen. */
    @Volatile var lastShareSkipped: Int = 0
        private set

    /** Packt die Bilder in cacheDir/share/<Baustelle>.zip. */
    suspend fun buildShareZip(photos: List<PhotoRecord>): File = withContext(Dispatchers.IO) {
        val site = photos.map { it.label.site }.distinct().singleOrNull()
        val dir = File(cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${site?.let(PathBuilder::sanitize) ?: PathBuilder.ROOT}.zip")
        lastShareSkipped = file.outputStream().use { ShareZipper.zip(photos, store::open, it) }
        file
    }
}
