package com.urkaaaz.application.loadout

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.UnitConfigurations
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LoadoutUseCasesTest {
    private val availability = LoadoutAvailability(
        level = 8,
        unitTypes = setOf("DEFENDER", "SAPPER", "DEMOLISHER"),
        ammunitionTypes = AmmunitionType.values().map { it.name }.toSet(),
        spellTypes = setOf("FORTRESS_SHIELD", "HASTE", "HEAL", "ICE_TRAP"),
    )

    private fun snapshot(
        unlocked: Set<String> = setOf("FORTRESS_SHIELD", "HASTE", "HEAL", "ICE_TRAP"),
        ammoStock: Map<String, Int> = emptyMap(),
        specialStock: Map<String, Int> = emptyMap(),
    ) = LoadoutProgressionSnapshot(unlocked, ammoStock, specialStock, goldBalance = 500)

    @Test
    fun structuralModelEnforcesSlotsLimitsUniquenessAndRockPosition() {
        assertEquals(
            MatchLoadout(
                unitTypes = emptyList(),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = emptyList(),
            ),
            MatchLoadout(emptyList(), listOf("ROCK", null, null), emptyList()),
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MatchLoadout(
                unitTypes = listOf("DEFENDER", "DEFENDER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = emptyList(),
            )
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MatchLoadout(
                unitTypes = emptyList(),
                ammunitionSlots = listOf("SIEGE_BOMB", null, null),
                spellTypes = emptyList(),
            )
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MatchLoadout(
                unitTypes = emptyList(),
                ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", "SIEGE_BOMB"),
                spellTypes = emptyList(),
            )
        }
    }

    @Test
    fun defaultsFollowCentralCatalogOrderAndIncludeOnlyEligibleContent() {
        val defaults = MatchLoadout.defaults(
            availability,
            snapshot(ammoStock = mapOf("POWDER_BARREL" to 2)),
        )

        assertEquals(
            UnitConfigurations.all.take(3).map { it.id },
            defaults.unitTypes,
        )
        assertEquals(listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL"), defaults.ammunitionSlots)
        assertEquals(listOf("ICE_TRAP", "HASTE", "HEAL"), defaults.spellTypes)
    }

    @Test
    fun campaignPreferredSpellsComeFirstOnlyInGeneratedDefaults() {
        val progression = snapshot().copy(defaultSpellTypes = setOf("HASTE", "HEAL"))

        val defaults = MatchLoadout.defaults(availability, progression)

        assertEquals(listOf("HASTE", "HEAL", "ICE_TRAP"), defaults.spellTypes)
    }

    @Test
    fun savedLoadoutIsNotAppendedWithNewCampaignDefaultSpells() = runBlocking {
        val repository = FakeRepository(
            StoredLoadoutSelection(
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        val useCases = useCases(
            progression = FakeProgression(
                snapshot().copy(defaultSpellTypes = setOf("HASTE", "HEAL")),
            ),
            repository = repository,
        )

        val result = assertIs<LoadoutOpenResult.Opened>(useCases.open(availability))

        assertEquals(listOf("FORTRESS_SHIELD"), result.draft.spellTypes)
    }

    @Test
    fun openNormalizesStaleStoredValuesAndReportsEachCorrection() {
        val repository = FakeRepository(
            StoredLoadoutSelection(
                unitTypes = listOf("MISSING", "DEFENDER", "DEFENDER", "SAPPER", "DEMOLISHER", "SAPPER"),
                ammunitionSlots = listOf("WRONG", "POWDER_BARREL", "POWDER_BARREL", "SIEGE_BOMB"),
                spellTypes = listOf("MISSING", "HASTE", "HASTE", "HEAL", "FORTRESS_SHIELD"),
            ),
        )
        val useCases = useCases(
            progression = FakeProgression(
                snapshot(ammoStock = mapOf("POWDER_BARREL" to 1)),
            ),
            repository = repository,
        )

        val result = assertIs<LoadoutOpenResult.Opened>(
            runBlocking { useCases.open(availability) },
        )

        assertEquals(listOf("DEFENDER", "SAPPER", "DEMOLISHER"), result.draft.unitTypes)
        assertEquals(listOf("ROCK", "POWDER_BARREL", null), result.draft.ammunitionSlots)
        assertEquals(listOf("HASTE", "HEAL", "FORTRESS_SHIELD"), result.draft.spellTypes)
        assertEquals(
            setOf(
                LoadoutCorrectionReason.UNKNOWN,
                LoadoutCorrectionReason.DUPLICATE,
                LoadoutCorrectionReason.EXCESS,
                LoadoutCorrectionReason.INVALID_SLOT,
            ),
            result.corrections.map { it.reason }.toSet(),
        )
    }

    @Test
    fun editingRejectsUnavailableOrOutOfStockItemsWithoutChangingDraft() {
        val useCases = useCases()
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val progression = snapshot()

        val unitResult = assertIs<LoadoutEditResult.Rejected>(
            useCases.edit(
                draft,
                LoadoutEditAction.SelectUnit("DEMOLISHER", selected = true),
                availability.copy(unitTypes = setOf("DEFENDER")),
                progression,
            ),
        )
        val ammoResult = assertIs<LoadoutEditResult.Rejected>(
            useCases.edit(
                draft,
                LoadoutEditAction.SetAmmunition(1, "POWDER_BARREL"),
                availability,
                progression,
            ),
        )

        assertEquals(LoadoutError.UNIT_UNAVAILABLE, unitResult.error)
        assertEquals(LoadoutError.AMMUNITION_OUT_OF_STOCK, ammoResult.error)
        assertEquals(draft, unitResult.draft)
        assertEquals(draft, ammoResult.draft)
    }

    @Test
    fun ammunitionToggleUsesFirstEmptySlotAndRemovesSelectionWithoutMovingOtherTypes() {
        val useCases = useCases()
        val initial = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val progression = snapshot(ammoStock = mapOf("POWDER_BARREL" to 2))

        val withSiegeBomb = assertIs<LoadoutEditResult.Accepted>(
            useCases.edit(
                initial,
                LoadoutEditAction.ToggleAmmunition("SIEGE_BOMB", selected = true),
                availability,
                progression,
            ),
        ).draft
        val withPowderBarrel = assertIs<LoadoutEditResult.Accepted>(
            useCases.edit(
                withSiegeBomb,
                LoadoutEditAction.ToggleAmmunition("POWDER_BARREL", selected = true),
                availability,
                progression,
            ),
        ).draft
        val withoutSiegeBomb = assertIs<LoadoutEditResult.Accepted>(
            useCases.edit(
                withPowderBarrel,
                LoadoutEditAction.ToggleAmmunition("SIEGE_BOMB", selected = false),
                availability,
                progression,
            ),
        ).draft
        val filledFirstSlot = assertIs<LoadoutEditResult.Accepted>(
            useCases.edit(
                withoutSiegeBomb,
                LoadoutEditAction.ToggleAmmunition("SIEGE_BOMB", selected = true),
                availability,
                progression,
            ),
        ).draft

        assertEquals(listOf("ROCK", "SIEGE_BOMB", null), withSiegeBomb.ammunitionSlots)
        assertEquals(
            listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL"),
            withPowderBarrel.ammunitionSlots,
        )
        assertEquals(listOf("ROCK", null, "POWDER_BARREL"), withoutSiegeBomb.ammunitionSlots)
        assertEquals(
            listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL"),
            filledFirstSlot.ammunitionSlots,
        )
    }

    @Test
    fun ammunitionToggleKeepsRockFixedAndEnforcesStockAndSelectionLimit() {
        val useCases = useCases()
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL"),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val progression = snapshot(ammoStock = mapOf("POWDER_BARREL" to 1))

        val rockResult = useCases.edit(
            draft,
            LoadoutEditAction.ToggleAmmunition("ROCK", selected = false),
            availability,
            progression,
        )
        val fullResult = useCases.edit(
            draft,
            LoadoutEditAction.ToggleAmmunition("FIRE_RAIN", selected = true),
            availability,
            progression.copy(
                ammunitionStocks = mapOf(
                    "POWDER_BARREL" to 1,
                    "FIRE_RAIN" to 1,
                ),
            ),
        )
        val stockResult = useCases.edit(
            MatchLoadout(
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
            LoadoutEditAction.ToggleAmmunition("FIRE_RAIN", selected = true),
            availability,
            progression,
        )

        assertEquals(
            LoadoutError.ROCK_SLOT_FIXED,
            assertIs<LoadoutEditResult.Rejected>(rockResult).error,
        )
        assertEquals(
            LoadoutError.SELECTION_LIMIT_REACHED,
            assertIs<LoadoutEditResult.Rejected>(fullResult).error,
        )
        assertEquals(
            LoadoutError.AMMUNITION_OUT_OF_STOCK,
            assertIs<LoadoutEditResult.Rejected>(stockResult).error,
        )
    }

    @Test
    fun specialSpellRequiresStockButDoesNotRequireLevelAvailability() {
        val useCases = useCases()
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val special = "CATAPULT_LOCK"
        val specialAvailability = availability.copy(spellTypes = setOf("FORTRESS_SHIELD"))
        val noStock = useCases.edit(
            draft,
            LoadoutEditAction.ToggleSpell(special, selected = true),
            specialAvailability,
            snapshot(),
        )
        val withStock = useCases.edit(
            draft,
            LoadoutEditAction.ToggleSpell(special, selected = true),
            specialAvailability,
            snapshot(specialStock = mapOf(special to 1)),
        )

        assertEquals(
            LoadoutError.SPECIAL_SPELL_REQUIRES_SHOP,
            assertIs<LoadoutEditResult.Rejected>(noStock).error,
        )
        assertEquals(
            listOf("FORTRESS_SHIELD", special),
            assertIs<LoadoutEditResult.Accepted>(withStock).draft.spellTypes,
        )
    }

    @Test
    fun commitUsesFreshProgressionAndDoesNotPersistRejectedLoadout() {
        val progression = FakeProgression(
            snapshot(ammoStock = mapOf("POWDER_BARREL" to 1)),
        )
        val repository = FakeRepository(null)
        val useCases = useCases(progression, repository)
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "POWDER_BARREL", null),
            spellTypes = listOf("HASTE"),
        )
        progression.current = snapshot(ammoStock = emptyMap())

        val result = runBlocking {
            useCases.commit(draft, availability, PlayerId("player"))
        }

        assertEquals(
            LoadoutCommitResult.Rejected(LoadoutError.AMMUNITION_OUT_OF_STOCK),
            result,
        )
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun commitPersistsOnlyAfterValidationAndReturnsContractProjection() {
        val repository = FakeRepository(null)
        val useCases = useCases(
            progression = FakeProgression(snapshot()),
            repository = repository,
        )
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        val result = assertIs<LoadoutCommitResult.Committed>(
            runBlocking { useCases.commit(draft, availability, PlayerId("player")) },
        )

        assertEquals(draft, repository.saved)
        assertEquals(listOf("DEFENDER"), result.playerLoadout.unitTypes)
        assertEquals(listOf("FORTRESS_SHIELD"), result.playerLoadout.spellTypes)
    }

    @Test
    fun emptyDefaultSpellSelectionIsReportedAndCannotBeCommitted() {
        val repository = FakeRepository(
            StoredLoadoutSelection(
                unitTypes = emptyList(),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("HASTE"),
            ),
        )
        val useCases = useCases(
            progression = FakeProgression(snapshot(unlocked = emptySet())),
            repository = repository,
        )

        val opened = assertIs<LoadoutOpenResult.Opened>(
            runBlocking { useCases.open(availability) },
        )
        assertEquals(emptyList(), opened.draft.spellTypes)
        assertEquals(
            LoadoutCommitResult.Rejected(LoadoutError.EMPTY_SPELL_SELECTION),
            runBlocking {
                useCases.commit(opened.draft, availability, PlayerId("player"))
            },
        )
        assertEquals(0, repository.saveCount)
        assertEquals(
            setOf(LoadoutCorrectionReason.DEFAULT_USED, LoadoutCorrectionReason.LOCKED),
            opened.corrections.map { it.reason }.toSet(),
        )
    }

    @Test
    fun storageFailureDoesNotReturnSuccessfulCommit() {
        val repository = FakeRepository(null, saveSucceeds = false)
        val useCases = useCases(
            progression = FakeProgression(snapshot()),
            repository = repository,
        )
        val draft = MatchLoadout(
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        assertEquals(
            LoadoutCommitResult.StorageFailure(LoadoutStorageFailure.STORAGE_UNAVAILABLE),
            runBlocking { useCases.commit(draft, availability, PlayerId("player")) },
        )
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun purchaseDelegatesOnlyOrdinarySpellsAndPreservesTypedDenial() {
        val progression = FakeProgression(
            snapshot(unlocked = setOf("FORTRESS_SHIELD")),
            purchase = LoadoutPurchaseResult.InsufficientGold,
        )
        val useCases = useCases(progression)

        assertEquals(
            LoadoutPurchaseOutcome.InsufficientGold,
            runBlocking { useCases.purchaseSpell("HASTE", availability) },
        )
        assertEquals(
            LoadoutPurchaseOutcome.Rejected(LoadoutError.SPECIAL_SPELL_REQUIRES_SHOP),
            runBlocking { useCases.purchaseSpell("CATAPULT_LOCK", availability) },
        )
        assertEquals(listOf("HASTE"), progression.purchased)
    }

    private fun useCases(
        progression: FakeProgression = FakeProgression(snapshot()),
        repository: FakeRepository = FakeRepository(null),
    ) = LoadoutUseCases(progression, repository)

    private class FakeProgression(
        var current: LoadoutProgressionSnapshot,
        private val purchase: LoadoutPurchaseResult = LoadoutPurchaseResult.Purchased,
    ) : LoadoutProgressionPort {
        val purchased = mutableListOf<String>()

        override suspend fun snapshot(level: Int): LoadoutProgressionRead =
            LoadoutProgressionRead.Available(current)

        override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult {
            purchased += spellType
            return purchase
        }
    }

    private class FakeRepository(
        private val stored: StoredLoadoutSelection?,
        private val saveSucceeds: Boolean = true,
    ) : LoadoutRepository {
        var saved: MatchLoadout? = null
        var saveCount = 0

        override suspend fun read(level: Int): LoadoutStorageResult =
            LoadoutStorageResult.Success(stored)

        override suspend fun save(level: Int, loadout: MatchLoadout): LoadoutStorageWriteResult {
            saveCount += 1
            saved = loadout
            return if (saveSucceeds) {
                LoadoutStorageWriteResult.Saved
            } else {
                LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.STORAGE_UNAVAILABLE)
            }
        }
    }
}
