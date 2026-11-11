package io.github.bassboost1981.gear360bridge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.bassboost1981.gear360bridge.R
import io.github.bassboost1981.gear360bridge.catalog.LabelChange
import io.github.bassboost1981.gear360bridge.catalog.PhotoRecord
import kotlinx.coroutines.launch

@Composable
fun OverviewScreen(vm: OverviewViewModel, modifier: Modifier = Modifier) {
    val sites by vm.sites.collectAsState()
    // Die offene Baustelle liegt im ViewModel, zusammen mit der Auswahl: beides wechselt nur gemeinsam.
    val site by vm.openSite.collectAsState()
    val moveFailed by vm.moveFailed.collectAsState()

    val open = site
    if (open == null || open !in sites) {
        SiteList(sites, onOpen = vm::open, modifier = modifier)
    } else {
        SitePhotos(open, vm, onBack = vm::close, modifier = modifier)
    }

    if (moveFailed) {
        AlertDialog(
            onDismissRequest = vm::dismissMoveFailed,
            confirmButton = { TextButton(onClick = vm::dismissMoveFailed) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.msg_move_failed)) },
        )
    }
}

@Composable
private fun SiteList(sites: List<String>, onOpen: (String) -> Unit, modifier: Modifier) {
    if (sites.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.overview_empty))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(sites) { site ->
            Text(
                site,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(site) }.heightIn(min = 64.dp).padding(16.dp),
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SitePhotos(site: String, vm: OverviewViewModel, onBack: () -> Unit, modifier: Modifier) {
    val photos by remember(site) { vm.photosOf(site) }.collectAsState(emptyList())
    val selected by vm.selected.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMove by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<List<PhotoRecord>?>(null) }

    fun share(list: List<PhotoRecord>) {
        if (list.isEmpty()) return
        Toast.makeText(context, R.string.share_preparing, Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val zip = vm.buildShareZip(list)
                val missing = vm.lastShareSkipped
                if (missing > 0) {
                    val text = context.resources.getQuantityString(R.plurals.share_skipped, missing, missing)
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
                if (missing == list.size) return@launch
                shareZip(context, FileProvider.getUriForFile(context, "${context.packageName}.share", zip))
            } catch (e: Exception) {
                Toast.makeText(context, R.string.msg_share_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(
                if (selected.isEmpty()) site else stringResource(R.string.overview_selected, selected.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) {
                IconButton(onClick = { showMove = true }) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.action_move))
                }
                if (selected.size == 1) {
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_rename)) }
                }
                IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
            }
            IconButton(onClick = {
                val list = if (selected.isEmpty()) photos else photos.filter { it.id in selected }
                if (vm.shareNeedsWarning) pendingShare = list else share(list)
            }) {
                Icon(Icons.Filled.Share, stringResource(R.string.action_share))
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            photos.groupBy { it.folder }.forEach { (folder, group) ->
                item(key = "h:" + folder.joinToString("/")) {
                    Text(
                        folder.joinToString(" / "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { it.id }) { photo ->
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (selected.isNotEmpty()) vm.toggle(photo.id) },
                                onLongClick = { vm.toggle(photo.id) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = Uri.parse(photo.storeUri),
                            contentDescription = photo.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 112.dp, height = 56.dp),
                        )
                        Text(photo.fileName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (selected.isNotEmpty()) Checkbox(checked = photo.id in selected, onCheckedChange = { vm.toggle(photo.id) })
                    }
                }
            }
        }
    }

    pendingShare?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingShare = null },
            text = { Text(stringResource(R.string.share_no_internet)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingShare = null
                    share(list)
                }) { Text(stringResource(R.string.share_anyway)) }
            },
            dismissButton = { TextButton(onClick = { pendingShare = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showMove) {
        MoveDialog(onDismiss = { showMove = false }, onMove = {
            showMove = false
            vm.moveSelected(it)
        })
    }
    if (showRename) {
        val id = selected.singleOrNull()
        TextDialog(
            title = stringResource(R.string.rename_title),
            label = stringResource(R.string.field_room),
            initial = photos.firstOrNull { it.id == id }?.label?.room.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { room ->
                showRename = false
                if (id != null && room.isNotBlank()) vm.rename(id, room.trim())
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.delete_confirm, selected.size)) },
        )
    }
}

@Composable
private fun MoveDialog(onDismiss: () -> Unit, onMove: (LabelChange) -> Unit) {
    var site by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var flat by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    fun String.orNull() = trim().ifEmpty { null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.move_hint))
                OutlinedTextField(site, { site = it }, label = { Text(stringResource(R.string.field_site)) }, singleLine = true)
                OutlinedTextField(floor, { floor = it }, label = { Text(stringResource(R.string.field_floor)) }, singleLine = true)
                OutlinedTextField(flat, { flat = it }, label = { Text(stringResource(R.string.field_flat)) }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text(stringResource(R.string.field_room)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(LabelChange(site.orNull(), floor.orNull(), flat.orNull(), room.orNull())) }) {
                Text(stringResource(R.string.action_move))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun shareZip(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
}
