package com.urkaaaz.application.campaign

import com.urkaaaz.campaign.CampaignLevelDefinition

data class CampaignProgress(
    val highestUnlockedLevel: Int = 1,
    val completedLevels: Set<Int> = emptySet(),
    val appliedMilestoneIds: Set<String> = emptySet(),
) {
    init {
        require(highestUnlockedLevel in 1..CampaignLevelDefinition.MAX_LEVELS) {
            "highestUnlockedLevel must be a valid campaign level"
        }
        require(completedLevels.all { it in 1..CampaignLevelDefinition.MAX_LEVELS }) {
            "completed levels must be valid campaign levels"
        }
        require(appliedMilestoneIds.all { id ->
            id.matches(Regex("[a-z][a-z0-9_]*"))
        }) {
            "milestone IDs must be stable lowercase identifiers"
        }
    }

    companion object {
        val INITIAL = CampaignProgress()
    }
}

enum class CampaignProgressFailure {
    STORAGE_UNAVAILABLE,
    INVALID_DATA,
    UNSUPPORTED_VERSION,
}

sealed interface CampaignProgressRepositoryResult {
    data class Available(val progress: CampaignProgress) : CampaignProgressRepositoryResult
    data class Failure(val reason: CampaignProgressFailure) : CampaignProgressRepositoryResult
}

interface CampaignProgressRepository {
    suspend fun loadOrInitialize(): CampaignProgressRepositoryResult

    /**
     * Applies a pure progress transformation in the same transaction that reads and writes it.
     */
    suspend fun update(
        transform: (CampaignProgress) -> CampaignProgress,
    ): CampaignProgressRepositoryResult
}
