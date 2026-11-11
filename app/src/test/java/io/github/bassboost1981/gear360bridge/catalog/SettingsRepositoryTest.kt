package io.github.bassboost1981.gear360bridge.catalog

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.bassboost1981.gear360bridge.filing.FilingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(job: Job): SettingsRepository {
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(tmp.root, "settings.preferences_pb")
        }
        return SettingsRepository(store)
    }

    @Test fun settings_defaults() = runBlocking {
        val job = Job()
        val repo = open(job)
        assertEquals(FilingSettings(), repo.filing.first())
        assertEquals(Selection(), repo.selection.first())
        job.cancelAndJoin()
    }

    @Test fun settings_updatePersists() = runBlocking {
        val first = Job()
        open(first).apply {
            updateFiling { it.copy(delaySeconds = 20, dateAboveSite = true, useFlat = true) }
            updateSelection { it.copy(site = "Musterstraße 12", floor = "2. OG", customRooms = listOf("Sauna")) }
        }
        first.cancelAndJoin()

        val second = Job()
        val repo = open(second)
        assertEquals(FilingSettings(useFlat = true, dateAboveSite = true, delaySeconds = 20), repo.filing.first())
        assertEquals(Selection(site = "Musterstraße 12", floor = "2. OG", customRooms = listOf("Sauna")), repo.selection.first())
        second.cancelAndJoin()
    }
}
