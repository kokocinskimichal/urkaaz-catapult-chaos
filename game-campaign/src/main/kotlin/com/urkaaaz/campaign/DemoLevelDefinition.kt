package com.urkaaaz.campaign

import com.urkaaaz.campaign.chapter1.chapter1Level1MapDefinition
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.WindSnapshot

data class DemoLevelDefinition(
    val mapDefinition: MapDefinition,
    val biome: TerrainBiome,
    val initialWind: WindSnapshot,
)

object DemoLevelDefinitions {
    val default: DemoLevelDefinition = DemoLevelDefinition(
        mapDefinition = MapDefinitionValidator.requireValid(
            chapter1Level1MapDefinition.copy(
                levelId = "demo-map",
                generationMetadata = chapter1Level1MapDefinition.generationMetadata.copy(
                    source = "demo.map",
                    authoringVersion = "lds-demo-1",
                ),
            ),
        ),
        biome = TerrainBiome.GREEN_FRONTIER,
        initialWind = WindSnapshot(),
    )
}
