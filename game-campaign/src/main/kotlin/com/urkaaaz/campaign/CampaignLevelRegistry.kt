package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerId

class CampaignLevelRegistry(
    private val levels: Map<Int, CampaignLevelDefinition> = defaultLevels(),
) {
    fun level(levelNumber: Int): CampaignLevelDefinition =
        levels[levelNumber] ?: error("unknown campaign level: $levelNumber")

    fun mapDefinition(levelNumber: Int): MapDefinition = level(levelNumber).mapDefinition

    fun scenario(
        levelNumber: Int,
        playerId: PlayerId = PlayerId("player"),
        opponentId: PlayerId = PlayerId("opponent"),
    ): MatchScenario = level(levelNumber).toMatchScenario(playerId, opponentId)

    fun contains(levelNumber: Int): Boolean = levelNumber in levels

    companion object {
        fun default(): CampaignLevelRegistry = CampaignLevelRegistry()

        private fun defaultLevels(): Map<Int, CampaignLevelDefinition> =
            (1..CampaignLevelDefinition.MAX_LEVELS).associateWith { level ->
                CampaignLevelDefinition.forLevel(level).also {
                    MapDefinitionValidator.requireValid(it.mapDefinition)
                }
            }
    }
}
