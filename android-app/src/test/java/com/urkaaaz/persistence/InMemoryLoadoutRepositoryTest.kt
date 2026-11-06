package com.urkaaaz.persistence

import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.MatchLoadout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLoadoutRepositoryTest {
    @Test
    fun selectionsAreAvailableInMemoryButAreNotRestoredByANewRepository() = runBlocking {
        val repository = InMemoryLoadoutRepository()
        val loadout = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        assertTrue(repository.save(12, loadout) is LoadoutStorageWriteResult.Saved)
        assertEquals(
            loadout.unitTypes,
            (repository.read(12) as LoadoutStorageResult.Success)
                .selection?.unitTypes,
        )
        assertEquals(
            null,
            (InMemoryLoadoutRepository().read(12) as LoadoutStorageResult.Success).selection,
        )
    }
}
