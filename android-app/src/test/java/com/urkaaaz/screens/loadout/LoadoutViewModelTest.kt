package com.urkaaaz.screens.loadout

import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult
import com.urkaaaz.application.loadout.LoadoutRepository
import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.loadout.MatchLoadout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadoutViewModelTest {
    @Test
    fun validatedLocalProfileUnlocksStartOnlyAfterLoadoutIsPersisted() = runBlocking {
        val repository = FakeRepository()
        val viewModel = LoadoutViewModel(
            campaignLevel = 8,
            useCases = LoadoutUseCases(FakeProgression(), repository),
        )

        assertFalse(viewModel.state.canStart)
        viewModel.load()
        assertTrue("DEMOLISHER" in viewModel.state.availableUnits)
        assertTrue("HASTE" in viewModel.state.availableSpells)
        assertTrue("HEAL" in viewModel.state.availableSpells)
        assertTrue(viewModel.state.canStart)

        val committed = viewModel.commit()

        assertNotNull(committed)
        assertTrue(repository.saved)
    }

    @Test
    fun storageFailureKeepsMatchStartBlocked() = runBlocking {
        val viewModel = LoadoutViewModel(
            campaignLevel = 1,
            useCases = LoadoutUseCases(FakeProgression(), FakeRepository(failRead = true)),
        )

        viewModel.load()

        assertFalse(viewModel.state.canStart)
        assertTrue(viewModel.state.errorMessage?.contains("unavailable") == true)
    }

    @Test
    fun saveFailureDoesNotProduceLoadoutForNavigation() = runBlocking {
        val repository = FakeRepository(failSave = true)
        val viewModel = LoadoutViewModel(
            campaignLevel = 1,
            useCases = LoadoutUseCases(FakeProgression(), repository),
        )
        viewModel.load()

        assertTrue(viewModel.state.canStart)
        assertTrue(viewModel.commit() == null)
        assertFalse(viewModel.state.canStart)
        assertTrue(viewModel.state.committedLoadout == null)
        assertTrue(viewModel.state.errorMessage?.contains("unavailable") == true)
    }

    @Test
    fun cancellingLoadoutReadNeverEnablesMatchStart() = runBlocking {
        val enteredRead = CompletableDeferred<Unit>()
        val viewModel = LoadoutViewModel(
            campaignLevel = 1,
            useCases = LoadoutUseCases(
                BlockingProgression(enteredRead),
                FakeRepository(),
            ),
        )
        val load = launch { viewModel.load() }

        enteredRead.await()
        load.cancelAndJoin()

        assertFalse(viewModel.state.canStart)
        assertTrue(viewModel.state.isLoading)
    }

    private class FakeProgression : LoadoutProgressionPort {
        override suspend fun snapshot(level: Int): LoadoutProgressionRead =
            LoadoutProgressionRead.Available(
                LoadoutProgressionSnapshot(
                    unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
                    ammunitionStocks = emptyMap(),
                    specialSpellStocks = emptyMap(),
                    goldBalance = 0,
                ),
            )

        override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
            LoadoutPurchaseResult.Blocked
    }

    private class BlockingProgression(
        private val enteredRead: CompletableDeferred<Unit>,
    ) : LoadoutProgressionPort {
        override suspend fun snapshot(level: Int): LoadoutProgressionRead {
            enteredRead.complete(Unit)
            awaitCancellation()
        }

        override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
            LoadoutPurchaseResult.Blocked
    }

    private class FakeRepository(
        private val failRead: Boolean = false,
        private val failSave: Boolean = false,
    ) : LoadoutRepository {
        var saved = false

        override suspend fun read(level: Int): LoadoutStorageResult =
            if (failRead) {
                LoadoutStorageResult.Failure(
                    com.urkaaaz.application.loadout.LoadoutStorageFailure.STORAGE_UNAVAILABLE,
                )
            } else {
                LoadoutStorageResult.Success(null)
            }

        override suspend fun save(
            level: Int,
            loadout: MatchLoadout,
        ): LoadoutStorageWriteResult {
            saved = !failSave
            return if (failSave) {
                LoadoutStorageWriteResult.Failure(
                    com.urkaaaz.application.loadout.LoadoutStorageFailure.STORAGE_UNAVAILABLE,
                )
            } else {
                LoadoutStorageWriteResult.Saved
            }
        }
    }
}
