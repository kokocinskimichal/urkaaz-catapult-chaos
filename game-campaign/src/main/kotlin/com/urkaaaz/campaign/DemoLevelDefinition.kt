package com.urkaaaz.campaign

import com.urkaaaz.contracts.TerrainBiome
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.CampaignBalance

data class DemoLevelDefinition(
    val mapDefinition: MapDefinition,
    val biome: TerrainBiome,
    val initialWind: WindSnapshot,
)

object DemoLevelDefinitions {
    val terrainBiome: TerrainBiome = TerrainBiome.SUNKEN_MARSHES

    fun default(campaignBalance: CampaignBalance): DemoLevelDefinition =
        fromRepository(MapDefinitionRepository.bundled, campaignBalance)

    fun fromRepository(
        repository: MapDefinitionRepository,
        campaignBalance: CampaignBalance,
    ): DemoLevelDefinition {
        val mapDefinition = repository.require("demo-map").copy(
            biome = terrainBiome.mapId,
        )
        return DemoLevelDefinition(
            mapDefinition = mapDefinition,
            biome = terrainBiome,
            initialWind = WindSnapshot(
                direction = 1f,
                strength = campaignBalance.demoInitialWindStrength,
            ),
        )
    }
}
