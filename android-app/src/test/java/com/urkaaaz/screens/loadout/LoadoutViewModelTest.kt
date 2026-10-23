package com.urkaaaz.screens.loadout

import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult
import com.urkaaaz.application.loadout.LoadoutRepository
import com.urkaaaz.application.loadout.LoadoutEditAction
import com.urkaaaz.application.loadout.LoadoutError
import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.application.progression.DemoLoadoutProgressionPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadoutViewModelTest {
    @Test
    fun availableOptionsSortBeforeUnavailableOptionsAndKeepCatalogOrder() {
        val options = listOf("unavailable", "available", "selected", "locked", "outOfStock")
        val statuses = mapOf(
            "unavailable" to LoadoutOptionStatus.UNAVAILABLE,
            "available" to LoadoutOptionStatus.AVAILABLE,
            "selected" to LoadoutOptionStatus.SELECTED,
            "locked" to LoadoutOptionStatus.LOCKED,
            "outOfStock" to LoadoutOptionStatus.OUT_OF_STOCK,
        )

        val sorted = availableOptionsFirst(options) { statuses.getValue(it) }

        assertEquals(
            listOf("available", "selected", "unavailable", "locked", "outOfStock"),
            sorted,
        )
    }

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
    fun demoLoadoutOffersTheFullCatalogAndUsesDemoLabel() = runBlocking {
        val viewModel = LoadoutViewModel(
            campaignLevel = 1,
            useCases = LoadoutUseCases(DemoLoadoutProgressionPort(), FakeRepository()),
            demoMode = true,
        )

        viewModel.load()

        assertTrue(viewModel.state.demoMode)
        assertEquals(LoadoutCatalog.units.map { it.id }.toSet(), viewModel.state.availableUnits.toSet())
        assertEquals(
            LoadoutCatalog.ammunition.map { it.id }.toSet(),
            viewModel.state.availableAmmunition.toSet(),
        )
        assertEquals(LoadoutCatalog.spells.map { it.id }.toSet(), viewModel.state.availableSpells.toSet())
        viewModel.state.availableAmmunition.forEach { type ->
            assertTrue(
                viewModel.optionStatus(LoadoutItemReference.Ammunition(type)) in
                    setOf(LoadoutOptionStatus.AVAILABLE, LoadoutOptionStatus.SELECTED),
            )
        }
        viewModel.state.availableSpells.forEach { type ->
            assertTrue(
                viewModel.optionStatus(LoadoutItemReference.Spell(type)) in
                    setOf(LoadoutOptionStatus.AVAILABLE, LoadoutOptionStatus.SELECTED),
            )
        }
    }

    @Test
    fun storageFailureKeepsMatchStartBlocked() = runBlocking {
        val viewModel = LoadoutViewModel(
            campaignLevel = 1,
            useCases = LoadoutUseCases(FakeProgression(), FakeRepository(failRead = true)),
        )

        viewModel.load()

        assertFalse(viewModel.state.canStart)
        assertTrue(viewModel.state.failure is LoadoutUiFailure.Storage)
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
        assertTrue(viewModel.state.failure is LoadoutUiFailure.Storage)
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

    @Test
    fun detailBackKeepsDraftAndOnlyConsumesBackWhenDetailsAreOpen() = runBlocking {
        val viewModel = LoadoutViewModel(
            campaignLevel = 8,
            useCases = LoadoutUseCases(FakeProgression(), FakeRepository()),
        )
        viewModel.load()
        val draftBeforeDetails = viewModel.state.draft

        viewModel.showDetails(LoadoutItemReference.Unit("DEFENDER"))

        assertTrue(viewModel.handleBackPressed())
        assertNull(viewModel.state.detailItem)
        assertEquals(draftBeforeDetails, viewModel.state.draft)
        assertFalse(viewModel.handleBackPressed())
    }

    @Test
    fun rejectedEditIsVisibleButDoesNotBlockAValidDraft() = runBlocking {
        val viewModel = LoadoutViewModel(
            campaignLevel = 8,
            useCases = LoadoutUseCases(FakeProgression(), FakeRepository()),
        )
        viewModel.load()

        viewModel.edit(LoadoutEditAction.SelectUnit("UNKNOWN_UNIT", selected = true))

        assertEquals(
            LoadoutUiFailure.Edit(LoadoutError.UNKNOWN_UNIT),
            viewModel.state.failure,
        )
        assertTrue(viewModel.state.canStart)
        assertEquals(
            LoadoutOptionStatus.LOCKED,
            viewModel.optionStatus(LoadoutItemReference.Spell("HASTE")),
        )
    }

    @Test
    fun cancellingPendingCommitRestoresRetryableDraftState() = runBlocking {
        val enteredCommitValidation = CompletableDeferred<Unit>()
        val viewModel = LoadoutViewModel(
            campaignLevel = 8,
            useCases = LoadoutUseCases(
                BlockingCommitProgression(enteredCommitValidation),
                FakeRepository(),
            ),
        )
        viewModel.load()
        val draft = viewModel.state.draft
        val commit = launch { viewModel.commit() }

        enteredCommitValidation.await()
        assertTrue(viewModel.state.isSaving)
        viewModel.cancelPendingCommit()
        commit.cancelAndJoin()

        assertFalse(viewModel.state.isSaving)
        assertTrue(viewModel.state.canStart)
        assertEquals(draft, viewModel.state.draft)
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

    private class BlockingCommitProgression(
        private val enteredCommitValidation: CompletableDeferred<Unit>,
    ) : LoadoutProgressionPort {
        private var reads = 0

        override suspend fun snapshot(level: Int): LoadoutProgressionRead {
            reads += 1
            if (reads > 1) {
                enteredCommitValidation.complete(Unit)
                awaitCancellation()
            }
            return FakeProgression().snapshot(level)
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
