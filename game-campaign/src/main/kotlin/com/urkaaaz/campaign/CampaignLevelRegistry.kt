package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig

class CampaignLevelRegistry(
    private val levels: Map<Int, CampaignLevelDefinition> =
        defaultLevels(MapDefinitionRepository.bundled),
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
        fun default(): CampaignLevelRegistry = CampaignLevelRegistry()

        fun using(repository: MapDefinitionRepository): CampaignLevelRegistry =
            CampaignLevelRegistry(defaultLevels(repository))

        private fun defaultLevels(repository: MapDefinitionRepository): Map<Int, CampaignLevelDefinition> =
            (1..CampaignLevelDefinition.MAX_LEVELS).associateWith { level ->
                CampaignLevelDefinition.forLevel(level, repository).also {
                    MapDefinitionValidator.requireValid(it.mapDefinition)
                }
            }
    }
}
