package io.github.bassboost1981.gear360bridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.bassboost1981.gear360bridge.capture.CaptureService
import io.github.bassboost1981.gear360bridge.capture.CaptureState
import io.github.bassboost1981.gear360bridge.ui.CaptureScreen
import io.github.bassboost1981.gear360bridge.ui.CaptureViewModel
import io.github.bassboost1981.gear360bridge.ui.ConnectGuideScreen
import io.github.bassboost1981.gear360bridge.ui.OverviewScreen
import io.github.bassboost1981.gear360bridge.ui.OverviewViewModel
import io.github.bassboost1981.gear360bridge.ui.SettingsScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val container get() = (application as Gear360BridgeApp).container
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { App(container) } }
    }

    override fun onStart() {
        super.onStart()
        container.link.start()
    }

    override fun onStop() {
        super.onStop()
        // Laeuft gerade eine Aufnahme, muss die Verbindung im Hintergrund bestehen bleiben.
        if (!container.coordinator.isCapturing) container.link.stop()
    }
}

@Composable
private fun App(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.CameraAlt, null) }, label = { Text(stringResource(R.string.nav_capture)) })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Folder, null) }, label = { Text(stringResource(R.string.nav_overview)) })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.nav_settings)) })
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            0 -> CaptureTab(container, modifier)
            1 -> {
                val vm = viewModel {
                    OverviewViewModel(container.catalog, container.organizer, container.store, container.cacheDir) {
                        container.link.state.value.connected
                    }
                }
                OverviewScreen(vm, modifier)
            }
            else -> SettingsScreen(container.settings, modifier)
        }
    }
}

@Composable
private fun CaptureTab(container: AppContainer, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val vm = viewModel {
        CaptureViewModel(
            link = container.link.state,
            phase = container.coordinator.state,
            recovery = container.coordinator.lastRecovery,
            acknowledgeRecovery = container.coordinator::acknowledgeRecovery,
            settings = container.settings,
            organizer = container.organizer,
            startCapture = { label, delay -> CaptureService.start(context, label, delay) },
            cancelCapture = { CaptureService.cancel(context) },
            today = { LocalDate.now().toString() },
        )
    }
    val ui by vm.ui.collectAsState()
    val busy = ui.phase is CaptureState.Countdown || ui.phase == CaptureState.CameraWorking || ui.phase == CaptureState.Downloading
    // Die Anleitung erscheint, solange die Kamera nicht erreichbar ist und nichts mehr anzuzeigen ist.
    if (!ui.connected && !busy && ui.lastPhoto == null && ui.messageRes == null && ui.recovery == null) {
        ConnectGuideScreen(modifier)
    } else {
        CaptureScreen(ui, vm, modifier)
    }
}
