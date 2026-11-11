package io.github.bassboost1981.gear360bridge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bassboost1981.gear360bridge.R
import io.github.bassboost1981.gear360bridge.catalog.SettingsRepository
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import kotlinx.coroutines.launch

private val DELAYS = listOf(0, 5, 10, 20)

@Composable
fun SettingsScreen(settings: SettingsRepository, modifier: Modifier = Modifier) {
    val filing by settings.filing.collectAsState(FilingSettings())
    val scope = rememberCoroutineScope()
    fun update(f: (FilingSettings) -> FilingSettings) {
        scope.launch { settings.updateFiling(f) }
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Heading(stringResource(R.string.settings_parts_title))
        Check(stringResource(R.string.settings_use_date), filing.useDate) { v -> update { it.copy(useDate = v) } }
        Check(stringResource(R.string.settings_use_floor), filing.useFloor) { v -> update { it.copy(useFloor = v) } }
        Check(stringResource(R.string.settings_use_flat), filing.useFlat) { v -> update { it.copy(useFlat = v) } }
        Check(stringResource(R.string.settings_use_room), filing.useRoom) { v -> update { it.copy(useRoom = v) } }

        Heading(stringResource(R.string.settings_order_title))
        Radio(stringResource(R.string.settings_date_below), !filing.dateAboveSite) { update { it.copy(dateAboveSite = false) } }
        Radio(stringResource(R.string.settings_date_above), filing.dateAboveSite) { update { it.copy(dateAboveSite = true) } }

        Heading(stringResource(R.string.settings_delay_title))
        DELAYS.forEach { seconds ->
            val label = if (seconds == 0) stringResource(R.string.settings_delay_none)
            else stringResource(R.string.settings_delay_seconds, seconds)
            Radio(label, filing.delaySeconds == seconds) { update { it.copy(delaySeconds = seconds) } }
        }

        Text(
            stringResource(R.string.settings_note),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Radio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
