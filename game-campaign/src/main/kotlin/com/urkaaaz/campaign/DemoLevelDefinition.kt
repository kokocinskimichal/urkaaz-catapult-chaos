package com.urkaaaz.campaign

import com.urkaaaz.contracts.TerrainBiome
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.WindSnapshot

data class DemoLevelDefinition(
    val mapDefinition: MapDefinition,
    val biome: TerrainBiome,
    val initialWind: WindSnapshot,
)

object DemoLevelDefinitions {
    val default: DemoLevelDefinition = fromRepository(MapDefinitionRepository.bundled)

    fun fromRepository(repository: MapDefinitionRepository): DemoLevelDefinition {
        val mapDefinition = repository.require("demo-map")
        return DemoLevelDefinition(
            mapDefinition = mapDefinition,
            biome = TerrainBiome.fromMapId(mapDefinition.biome)
            ?: error("demo-map references an unsupported biome"),
            initialWind = WindSnapshot(),
        )
    }
}
