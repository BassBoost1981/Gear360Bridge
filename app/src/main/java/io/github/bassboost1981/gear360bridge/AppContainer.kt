package io.github.bassboost1981.gear360bridge

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.bassboost1981.gear360bridge.capture.CameraLink
import io.github.bassboost1981.gear360bridge.capture.CaptureCoordinator
import io.github.bassboost1981.gear360bridge.catalog.Catalog
import io.github.bassboost1981.gear360bridge.catalog.PhotoOrganizer
import io.github.bassboost1981.gear360bridge.catalog.SettingsRepository
import io.github.bassboost1981.gear360bridge.storage.MediaStorePhotoStore
import io.github.bassboost1981.gear360bridge.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.io.File

private val Context.settingsStore by preferencesDataStore("settings")

/** Die Bausteine der App, einmal pro App-Start verdrahtet. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val cacheDir: File = context.cacheDir
    val settings = SettingsRepository(context.settingsStore)
    val catalog = Catalog(File(context.filesDir, "catalog.json"))
    val store: PhotoStore = MediaStorePhotoStore(context)
    val organizer = PhotoOrganizer(store, catalog) { settings.filing.first() }
    val link = CameraLink(context, scope)
    val coordinator = CaptureCoordinator(link.osc, organizer, catalog, File(context.cacheDir, "capture")) {
        System.currentTimeMillis()
    }

    init {
        // Sobald die Kamera wieder da ist, abgebrochene Uebertragungen nachholen.
        link.onConnected = { coordinator.recoverPending() }
    }
}

class Gear360BridgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
