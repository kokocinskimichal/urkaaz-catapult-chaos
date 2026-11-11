package com.urkaaaz.application.campaign

import com.urkaaaz.application.progression.LifeSnapshot
import com.urkaaaz.application.progression.LifeSystem
import com.urkaaaz.application.progression.LifeSystemFailure
import com.urkaaaz.application.progression.LifeSystemResult
import com.urkaaaz.campaign.CampaignLevelDefinition

enum class CampaignLevelStartRejection {
    INVALID_LEVEL,
    LEVEL_LOCKED,
    LEVEL_ALREADY_COMPLETED,
    NO_LIVES,
}

enum class CampaignLevelStartFailure {
    CAMPAIGN_STORAGE_UNAVAILABLE,
    CAMPAIGN_INVALID_DATA,
    CAMPAIGN_UNSUPPORTED_VERSION,
    LIFE_STORAGE_UNAVAILABLE,
    LIFE_INVALID_PROFILE,
    LIFE_UNSUPPORTED_PROFILE_VERSION,
}

sealed interface CampaignLevelStartResult {
    data class Allowed(val definition: CampaignLevelDefinition) : CampaignLevelStartResult
    data class Rejected(val reason: CampaignLevelStartRejection) : CampaignLevelStartResult
    data class Failure(val reason: CampaignLevelStartFailure) : CampaignLevelStartResult
}

class CampaignLevelStartUseCase(
    private val campaignSystem: CampaignSystem,
    private val lifeSystem: LifeSystem,
) {
    suspend fun progress(): CampaignProgressRead = campaignSystem.progress()

    suspend fun lifeSnapshot(): LifeSystemResult<LifeSnapshot> = lifeSystem.snapshot()

    suspend fun authorize(level: Int): CampaignLevelStartResult {
        val selection = when (val result = campaignSystem.selectLevel(level)) {
            is CampaignLevelSelection.Selected -> result.definition
            is CampaignLevelSelection.Rejected ->
                return CampaignLevelStartResult.Rejected(
                    when (result.reason) {
                        CampaignSelectionRejection.INVALID_LEVEL ->
                            CampaignLevelStartRejection.INVALID_LEVEL
                        CampaignSelectionRejection.LEVEL_LOCKED ->
                            CampaignLevelStartRejection.LEVEL_LOCKED
                        CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED ->
                            CampaignLevelStartRejection.LEVEL_ALREADY_COMPLETED
                    },
                )
            is CampaignLevelSelection.Failure ->
                return CampaignLevelStartResult.Failure(result.reason.toStartFailure())
        }

        return when (val lives = lifeSystem.snapshot()) {
            is LifeSystemResult.Success ->
                if (lives.value.canStartCampaign) {
                    CampaignLevelStartResult.Allowed(selection)
                } else {
                    CampaignLevelStartResult.Rejected(CampaignLevelStartRejection.NO_LIVES)
                }
            is LifeSystemResult.Rejected ->
                CampaignLevelStartResult.Failure(CampaignLevelStartFailure.LIFE_INVALID_PROFILE)
            is LifeSystemResult.Failure ->
                CampaignLevelStartResult.Failure(lives.reason.toStartFailure())
        }
    }

    private fun CampaignProgressFailure.toStartFailure(): CampaignLevelStartFailure =
        when (this) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                CampaignLevelStartFailure.CAMPAIGN_STORAGE_UNAVAILABLE
            CampaignProgressFailure.INVALID_DATA ->
                CampaignLevelStartFailure.CAMPAIGN_INVALID_DATA
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                CampaignLevelStartFailure.CAMPAIGN_UNSUPPORTED_VERSION
        }

    private fun LifeSystemFailure.toStartFailure(): CampaignLevelStartFailure =
        when (this) {
            LifeSystemFailure.STORAGE_UNAVAILABLE ->
                CampaignLevelStartFailure.LIFE_STORAGE_UNAVAILABLE
            LifeSystemFailure.INVALID_PROFILE ->
                CampaignLevelStartFailure.LIFE_INVALID_PROFILE
            LifeSystemFailure.UNSUPPORTED_PROFILE_VERSION ->
                CampaignLevelStartFailure.LIFE_UNSUPPORTED_PROFILE_VERSION
        }
}
