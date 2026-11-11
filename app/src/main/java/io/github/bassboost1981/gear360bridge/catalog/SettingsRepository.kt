package io.github.bassboost1981.gear360bridge.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Einstellungen dieses Handys: Ablage-Regeln und die zuletzt gewaehlte Baustelle. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val filingKey = stringPreferencesKey("filing")
    private val selectionKey = stringPreferencesKey("selection")

    val filing: Flow<FilingSettings> = dataStore.data.map { it.filing() }
    val selection: Flow<Selection> = dataStore.data.map { it.selection() }

    suspend fun updateFiling(f: (FilingSettings) -> FilingSettings) {
        dataStore.edit { it[filingKey] = json.encodeToString(f(it.filing())) }
    }

    suspend fun updateSelection(f: (Selection) -> Selection) {
        dataStore.edit { it[selectionKey] = json.encodeToString(f(it.selection())) }
    }

    private fun Preferences.filing(): FilingSettings =
        this[filingKey]?.let { runCatching { json.decodeFromString<FilingSettings>(it) }.getOrNull() } ?: FilingSettings()

    private fun Preferences.selection(): Selection =
        this[selectionKey]?.let { runCatching { json.decodeFromString<Selection>(it) }.getOrNull() } ?: Selection()
}
