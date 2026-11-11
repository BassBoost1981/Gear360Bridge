package io.github.bassboost1981.gear360bridge.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.bassboost1981.gear360bridge.R
import io.github.bassboost1981.gear360bridge.capture.CaptureState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(ui: CaptureUi, vm: CaptureViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            ui.batteryPercent?.let { stringResource(R.string.status_connected, it) }
                ?: stringResource(R.string.status_connected_no_battery),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SyncedField(ui.site, vm::setSite, stringResource(R.string.field_site))
        if (ui.filing.useFloor) SyncedField(ui.floor, vm::setFloor, stringResource(R.string.field_floor))
        if (ui.filing.useFlat) SyncedField(ui.flat, vm::setFlat, stringResource(R.string.field_flat))
        if (ui.filing.useRoom) {
            Text(stringResource(R.string.field_room), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.rooms.forEach { room ->
                    FilterChip(selected = ui.room == room, onClick = { vm.setRoom(room) }, label = { Text(room) })
                }
            }
            SyncedField(ui.room, vm::setRoom, stringResource(R.string.field_room_custom))
        }
        Text(stringResource(R.string.target_label), style = MaterialTheme.typography.labelLarge)
        Text(ui.target, style = MaterialTheme.typography.bodyLarge)

        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            when (val phase = ui.phase) {
                is CaptureState.Countdown -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase.secondsLeft.toString(), style = MaterialTheme.typography.displayLarge)
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.countdown_cancel))
                    }
                }
                CaptureState.CameraWorking, CaptureState.Downloading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(72.dp))
                    Text(
                        stringResource(
                            if (phase == CaptureState.CameraWorking) R.string.phase_camera_working else R.string.phase_downloading,
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = vm::shoot, enabled = ui.canShoot, shape = CircleShape, modifier = Modifier.size(128.dp)) {
                        Text(stringResource(R.string.shutter))
                    }
                    if (ui.site.isBlank()) {
                        Text(stringResource(R.string.site_missing), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        ui.lastPhoto?.let { photo ->
            Card(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = Uri.parse(photo.storeUri),
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                )
                Text(
                    stringResource(R.string.saved_as, (photo.folder + photo.fileName).joinToString(" / ")),
                    modifier = Modifier.padding(12.dp),
                )
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::discardLast, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.discard))
                    }
                    Button(onClick = vm::dismiss, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.next))
                    }
                }
            }
        }
    }

    ui.recovery?.let { result ->
        val lines = listOfNotNull(
            result.recovered.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.msg_recovered, it, it) },
            result.lost.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.msg_lost, it, it) },
        )
        AlertDialog(
            onDismissRequest = vm.acknowledgeRecovery,
            confirmButton = { TextButton(onClick = vm.acknowledgeRecovery) { Text(stringResource(R.string.ok)) } },
            text = { Text(lines.joinToString("\n\n")) },
        )
    }

    ui.messageRes?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismiss,
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(message)) },
        )
    }
}

/**
 * Textfeld, dessen Wert gespeichert wird. Waehrend getippt wird, zaehlt die Eingabe; der
 * gespeicherte Wert wird nur uebernommen, solange das Feld nicht den Fokus hat.
 */
@Composable
private fun SyncedField(value: String, onChange: (String) -> Unit, label: String) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused && text != value) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
}
