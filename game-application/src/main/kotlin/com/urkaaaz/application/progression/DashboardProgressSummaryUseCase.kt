package com.urkaaaz.application.progression

import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.campaign.CampaignSystem
data class DashboardProgressSummary(
    val currentLevel: Int,
    val gold: Int,
    val lifeSnapshot: LifeSnapshot,
)

enum class DashboardProgressFailure {
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_VERSION,
}

sealed interface DashboardProgressRead {
    data class Available(val summary: DashboardProgressSummary) : DashboardProgressRead
    data class Failure(val reason: DashboardProgressFailure) : DashboardProgressRead
}

class DashboardProgressSummaryUseCase(
    private val campaignSystem: CampaignSystem,
    private val progression: PlayerProgressionRepository,
    private val lifeSystem: LifeSystem,
) {
    suspend fun load(): DashboardProgressRead {
        val campaign = when (val result = campaignSystem.progress()) {
            is CampaignProgressRead.Available -> result.progress
            is CampaignProgressRead.Failure -> return DashboardProgressRead.Failure(
                result.reason.toDashboardFailure(),
            )
        }
        val profile = when (
            val result = progression.loadOrInitialize(PlayerProgressionUseCases.INITIAL_PROFILE)
        ) {
            is PlayerProgressionRepositoryResult.Available -> result.snapshot
            is PlayerProgressionRepositoryResult.Failure ->
                return DashboardProgressRead.Failure(result.reason.toDashboardFailure())
        }
        if (!PlayerProgressionUseCases.isValidProfile(profile)) {
            return DashboardProgressRead.Failure(DashboardProgressFailure.INVALID_PROFILE)
        }
        val lives = when (val result = lifeSystem.snapshot()) {
            is LifeSystemResult.Success -> result.value
            is LifeSystemResult.Rejected ->
                return DashboardProgressRead.Failure(DashboardProgressFailure.INVALID_PROFILE)
            is LifeSystemResult.Failure ->
                return DashboardProgressRead.Failure(result.reason.toDashboardFailure())
        }
        return DashboardProgressRead.Available(
            DashboardProgressSummary(
                currentLevel = campaign.highestUnlockedLevel,
                gold = profile.goldBalance,
                lifeSnapshot = lives,
            ),
        )
    }

    private fun CampaignProgressFailure.toDashboardFailure(): DashboardProgressFailure =
        when (this) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                DashboardProgressFailure.STORAGE_UNAVAILABLE
            CampaignProgressFailure.INVALID_DATA ->
                DashboardProgressFailure.INVALID_PROFILE
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                DashboardProgressFailure.UNSUPPORTED_VERSION
        }

    private fun PlayerProgressionRepositoryFailure.toDashboardFailure(): DashboardProgressFailure =
        when (this) {
            PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                DashboardProgressFailure.STORAGE_UNAVAILABLE
            PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                DashboardProgressFailure.INVALID_PROFILE
            PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                DashboardProgressFailure.UNSUPPORTED_VERSION
        }

    private fun LifeSystemFailure.toDashboardFailure(): DashboardProgressFailure =
        when (this) {
            LifeSystemFailure.STORAGE_UNAVAILABLE ->
                DashboardProgressFailure.STORAGE_UNAVAILABLE
            LifeSystemFailure.INVALID_PROFILE ->
                DashboardProgressFailure.INVALID_PROFILE
            LifeSystemFailure.UNSUPPORTED_PROFILE_VERSION ->
                DashboardProgressFailure.UNSUPPORTED_VERSION
        }
}
