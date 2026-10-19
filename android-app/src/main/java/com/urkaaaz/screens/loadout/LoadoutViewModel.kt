package com.urkaaaz.screens.loadout

import com.urkaaaz.application.loadout.LoadoutAvailability
import com.urkaaaz.application.loadout.LoadoutCommitResult
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.LoadoutCorrection
import com.urkaaaz.application.loadout.LoadoutEditAction
import com.urkaaaz.application.loadout.LoadoutEditResult
import com.urkaaaz.application.loadout.LoadoutError
import com.urkaaaz.application.loadout.LoadoutOpenResult
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutStorageFailure
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig

data class LoadoutUiState(
    val level: Int,
    val availableUnits: List<String>,
    val availableAmmunition: List<String>,
    val availableSpells: List<String>,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val draft: MatchLoadout? = null,
    val corrections: List<LoadoutCorrection> = emptyList(),
    val progression: LoadoutProgressionSnapshot? = null,
    val failure: LoadoutUiFailure? = null,
    val validationError: LoadoutError? = null,
    val detailItem: LoadoutItemReference? = null,
    val committedLoadout: PlayerLoadoutConfig? = null,
) {
    val canStart: Boolean
        get() = !isLoading &&
            !isSaving &&
            draft != null &&
            progression != null &&
            validationError == null &&
            failure !is LoadoutUiFailure.Progression &&
            failure !is LoadoutUiFailure.Commit &&
            failure !is LoadoutUiFailure.Storage
}

sealed interface LoadoutUiFailure {
    data class Edit(val error: LoadoutError) : LoadoutUiFailure
    data class Commit(val error: LoadoutError) : LoadoutUiFailure
    data class Progression(val reason: LoadoutProgressionFailure) : LoadoutUiFailure
    data class Storage(val reason: LoadoutStorageFailure) : LoadoutUiFailure
}

sealed interface LoadoutItemReference {
    val identifier: String

    data class Unit(override val identifier: String) : LoadoutItemReference
    data class Ammunition(override val identifier: String) : LoadoutItemReference
    data class Spell(override val identifier: String) : LoadoutItemReference
}

enum class LoadoutOptionStatus {
    SELECTED,
    AVAILABLE,
    UNAVAILABLE,
    LOCKED,
    OUT_OF_STOCK,
}

internal fun <T> availableOptionsFirst(
    options: List<T>,
    statusOf: (T) -> LoadoutOptionStatus,
): List<T> = options.sortedBy { option ->
    when (statusOf(option)) {
        LoadoutOptionStatus.SELECTED,
        LoadoutOptionStatus.AVAILABLE -> false
        LoadoutOptionStatus.UNAVAILABLE,
        LoadoutOptionStatus.LOCKED,
        LoadoutOptionStatus.OUT_OF_STOCK -> true
    }
}

class LoadoutViewModel(
    campaignLevel: Int,
    private val useCases: LoadoutUseCases,
    definition: CampaignLevelDefinition = CampaignLevelDefinition.forLevel(campaignLevel),
) {
    private val scenario = definition.toMatchScenario()
    private val availability = LoadoutAvailability(
        level = definition.level,
        unitTypes = scenario.availableUnits.toSet(),
        ammunitionTypes = scenario.availableAmmunition.toSet(),
        spellTypes = scenario.availableSpells.toSet(),
    )

    var state = LoadoutUiState(
        level = definition.level,
        availableUnits = LoadoutCatalog.units
            .map { it.id }
            .filter { it in availability.unitTypes },
        availableAmmunition = LoadoutCatalog.ammunition
            .map { it.id }
            .filter { it in availability.ammunitionTypes },
        availableSpells = LoadoutCatalog.spells
            .map { it.id }
            .filter { it in availability.spellTypes },
    )
        private set

    suspend fun load() {
        state = state.copy(
            isLoading = true,
            isSaving = false,
            draft = null,
            corrections = emptyList(),
            progression = null,
            failure = null,
            validationError = null,
            detailItem = null,
            committedLoadout = null,
        )
        state = when (val result = useCases.open(availability)) {
            is LoadoutOpenResult.Opened -> state.copy(
                isLoading = false,
                draft = result.draft,
                corrections = result.corrections,
                progression = result.progression,
                validationError = useCases.validateDraft(
                    result.draft,
                    availability,
                    result.progression,
                ),
            )
            is LoadoutOpenResult.ProgressionBlocked -> state.copy(
                isLoading = false,
                failure = LoadoutUiFailure.Progression(result.reason),
            )
            is LoadoutOpenResult.StorageFailure -> state.copy(
                isLoading = false,
                failure = LoadoutUiFailure.Storage(result.reason),
            )
        }
    }

    fun edit(action: LoadoutEditAction) {
        val draft = state.draft ?: return
        val progression = state.progression ?: return
        when (
            val result = useCases.edit(
                draft = draft,
                action = action,
                availability = availability,
                snapshot = progression,
            )
        ) {
            is LoadoutEditResult.Accepted -> state = state.copy(
                draft = result.draft,
                failure = null,
                validationError = useCases.validateDraft(
                    result.draft,
                    availability,
                    progression,
                ),
            )
            is LoadoutEditResult.Rejected -> state = state.copy(
                failure = LoadoutUiFailure.Edit(result.error),
            )
        }
    }

    fun optionStatus(item: LoadoutItemReference): LoadoutOptionStatus {
        val draft = state.draft
        val progression = state.progression
        return when (item) {
            is LoadoutItemReference.Unit -> when {
                LoadoutCatalog.findUnit(item.identifier) == null ->
                    LoadoutOptionStatus.UNAVAILABLE
                item.identifier in draft?.unitTypes.orEmpty() -> LoadoutOptionStatus.SELECTED
                item.identifier !in availability.unitTypes -> LoadoutOptionStatus.UNAVAILABLE
                else -> LoadoutOptionStatus.AVAILABLE
            }
            is LoadoutItemReference.Ammunition -> {
                val ammunition = LoadoutCatalog.findAmmunition(item.identifier)
                    ?: return LoadoutOptionStatus.UNAVAILABLE
                when {
                    item.identifier == MatchLoadout.ROCK -> LoadoutOptionStatus.SELECTED
                    item.identifier in draft?.ammunitionSlots.orEmpty() ->
                        LoadoutOptionStatus.SELECTED
                    item.identifier !in availability.ammunitionTypes ->
                        LoadoutOptionStatus.UNAVAILABLE
                    !ammunition.unlimited &&
                        (progression?.ammunitionStock(item.identifier) ?: 0) <= 0 ->
                        LoadoutOptionStatus.OUT_OF_STOCK
                    else -> LoadoutOptionStatus.AVAILABLE
                }
            }
            is LoadoutItemReference.Spell -> {
                val spell = LoadoutCatalog.findSpell(item.identifier)
                    ?: return LoadoutOptionStatus.UNAVAILABLE
                when {
                    item.identifier in draft?.spellTypes.orEmpty() ->
                        LoadoutOptionStatus.SELECTED
                    spell.special &&
                        (progression?.specialSpellStock(item.identifier) ?: 0) <= 0 ->
                        LoadoutOptionStatus.OUT_OF_STOCK
                    !spell.special && item.identifier !in availability.spellTypes ->
                        LoadoutOptionStatus.UNAVAILABLE
                    !spell.special &&
                        item.identifier !in progression?.unlockedSpellTypes.orEmpty() ->
                        LoadoutOptionStatus.LOCKED
                    else -> LoadoutOptionStatus.AVAILABLE
                }
            }
        }
    }

    fun showDetails(item: LoadoutItemReference) {
        state = state.copy(detailItem = item)
    }

    fun closeDetails() {
        state = state.copy(detailItem = null)
    }

    fun handleBackPressed(): Boolean {
        if (state.detailItem == null) return false
        closeDetails()
        return true
    }

    fun cancelPendingCommit() {
        if (state.isSaving) {
            state = state.copy(isSaving = false)
        }
    }

    suspend fun commit(): PlayerLoadoutConfig? {
        val draft = state.draft ?: return null
        if (!state.canStart) return null

        state = state.copy(isSaving = true, failure = null, committedLoadout = null)
        state = when (
            val result = useCases.commit(
                draft = draft,
                availability = availability,
                playerId = PlayerId(PLAYER_ID),
            )
        ) {
            is LoadoutCommitResult.Committed -> state.copy(
                isSaving = false,
                committedLoadout = result.playerLoadout,
                validationError = null,
            )
            is LoadoutCommitResult.Rejected -> state.copy(
                isSaving = false,
                failure = LoadoutUiFailure.Commit(result.error),
                validationError = result.error,
            )
            is LoadoutCommitResult.ProgressionBlocked -> state.copy(
                isSaving = false,
                failure = LoadoutUiFailure.Progression(result.reason),
            )
            is LoadoutCommitResult.StorageFailure -> state.copy(
                isSaving = false,
                failure = LoadoutUiFailure.Storage(result.reason),
            )
        }
        return state.committedLoadout
    }

    private companion object {
        const val PLAYER_ID = "player"
    }
}
