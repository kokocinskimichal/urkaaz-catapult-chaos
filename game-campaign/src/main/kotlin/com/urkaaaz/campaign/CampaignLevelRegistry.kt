package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.domain.CampaignBalance

class CampaignLevelRegistry(
    private val levels: Map<Int, CampaignLevelDefinition>,
) {
    fun level(levelNumber: Int): CampaignLevelDefinition =
        levels[levelNumber] ?: error("unknown campaign level: $levelNumber")

    fun mapDefinition(levelNumber: Int): MapDefinition = level(levelNumber).mapDefinition

    fun scenario(
        levelNumber: Int,
        playerId: PlayerId = PlayerId("player"),
        opponentId: PlayerId = PlayerId("opponent"),
        playerLoadout: PlayerLoadoutConfig? = null,
    ): MatchScenario =
        level(levelNumber).toMatchScenario(playerId, opponentId, playerLoadout)

    fun contains(levelNumber: Int): Boolean = levelNumber in levels

    companion object {
        fun default(
            campaignBalance: CampaignBalance,
            repository: MapDefinitionRepository = MapDefinitionRepository.bundled,
        ): CampaignLevelRegistry =
            CampaignLevelRegistry(defaultLevels(campaignBalance, repository))

        fun using(
            campaignBalance: CampaignBalance,
            repository: MapDefinitionRepository,
        ): CampaignLevelRegistry = CampaignLevelRegistry(
            defaultLevels(campaignBalance, repository),
        )

        private fun defaultLevels(
            campaignBalance: CampaignBalance,
            repository: MapDefinitionRepository,
        ): Map<Int, CampaignLevelDefinition> =
            (1..CampaignLevelDefinition.MAX_LEVELS).associateWith { level ->
                CampaignLevelDefinition.forLevel(level, campaignBalance, repository).also {
                    MapDefinitionValidator.requireValid(it.mapDefinition)
                }
            }
    }
}
