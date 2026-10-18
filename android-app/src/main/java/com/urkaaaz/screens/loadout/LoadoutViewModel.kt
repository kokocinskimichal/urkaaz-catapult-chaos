package com.urkaaaz.screens.loadout

import com.urkaaaz.application.loadout.LoadoutAvailability
import com.urkaaaz.application.loadout.LoadoutCommitResult
import com.urkaaaz.application.loadout.LoadoutCorrection
import com.urkaaaz.application.loadout.LoadoutEditAction
import com.urkaaaz.application.loadout.LoadoutEditResult
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
    val errorMessage: String? = null,
    val committedLoadout: PlayerLoadoutConfig? = null,
) {
    val canStart: Boolean
        get() = !isLoading && !isSaving && draft != null && errorMessage == null
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
        availableUnits = scenario.availableUnits,
        availableAmmunition = scenario.availableAmmunition,
        availableSpells = scenario.availableSpells,
    )
        private set

    suspend fun load() {
        state = state.copy(
            isLoading = true,
            isSaving = false,
            draft = null,
            corrections = emptyList(),
            progression = null,
            errorMessage = null,
            committedLoadout = null,
        )
        state = when (val result = useCases.open(availability)) {
            is LoadoutOpenResult.Opened -> {
                val validationError = useCases.validateDraft(
                    result.draft,
                    availability,
                    result.progression,
                )
                state.copy(
                    isLoading = false,
                    draft = result.draft,
                    corrections = result.corrections,
                    progression = result.progression,
                    errorMessage = validationError?.let {
                        "Loadout cannot be started: $it."
                    },
                )
            }
            is LoadoutOpenResult.ProgressionBlocked -> state.copy(
                isLoading = false,
                errorMessage = progressionFailureMessage(result.reason),
            )
            is LoadoutOpenResult.StorageFailure -> state.copy(
                isLoading = false,
                errorMessage = storageFailureMessage(result.reason),
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
                errorMessage = useCases.validateDraft(
                    result.draft,
                    availability,
                    progression,
                )?.let { "Loadout cannot be started: $it." },
            )
            is LoadoutEditResult.Rejected ->
                state = state.copy(errorMessage = "Loadout change rejected: ${result.error}.")
        }
    }

    suspend fun commit(): PlayerLoadoutConfig? {
        val draft = state.draft ?: return null
        if (!state.canStart) return null

        state = state.copy(isSaving = true, errorMessage = null)
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
            )
            is LoadoutCommitResult.Rejected -> state.copy(
                isSaving = false,
                errorMessage = "Loadout cannot be saved: ${result.error}.",
            )
            is LoadoutCommitResult.ProgressionBlocked -> state.copy(
                isSaving = false,
                errorMessage = progressionFailureMessage(result.reason),
            )
            is LoadoutCommitResult.StorageFailure -> state.copy(
                isSaving = false,
                errorMessage = storageFailureMessage(result.reason),
            )
        }
        return state.committedLoadout
    }

    private fun progressionFailureMessage(reason: LoadoutProgressionFailure): String =
        when (reason) {
            LoadoutProgressionFailure.INVALID_LEVEL -> "This campaign level is invalid."
            LoadoutProgressionFailure.STORAGE_UNAVAILABLE ->
                "Progression data is unavailable. Match start is blocked."
            LoadoutProgressionFailure.INVALID_PROFILE ->
                "Progression data is invalid. Match start is blocked."
            LoadoutProgressionFailure.UNSUPPORTED_PROFILE_VERSION ->
                "Progression data uses an unsupported version. Match start is blocked."
        }

    private fun storageFailureMessage(reason: LoadoutStorageFailure): String =
        when (reason) {
            LoadoutStorageFailure.INVALID_LEVEL -> "This campaign level is invalid."
            LoadoutStorageFailure.STORAGE_UNAVAILABLE ->
                "Saved loadout data is unavailable. Match start is blocked."
            LoadoutStorageFailure.INVALID_DATA ->
                "Saved loadout data is invalid. Match start is blocked."
            LoadoutStorageFailure.UNSUPPORTED_VERSION ->
                "Saved loadout data uses an unsupported version. Match start is blocked."
        }

    private companion object {
        const val PLAYER_ID = "player"
    }
}
