package com.urkaaaz.campaign

import com.urkaaaz.campaign.chapter1.chapter1Level1MapDefinition
import com.urkaaaz.contracts.InitialCameraAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddLevelSkillCompatibilityTest {
    @Test
    fun authoringPipelineUsesTheProductionBuilderValidatorRegistryAndScenario() {
        val source = chapter1Level1MapDefinition
        val request = LevelAuthoringRequest(
            levelId = source.levelId,
            displayName = "Skill fixture",
            biome = TerrainBiome.GREEN_FRONTIER,
            chapter = 1,
            levelNumber = 1,
            worldWidth = source.worldWidth,
            worldHeight = source.worldHeight,
            terrainSamples = source.terrain.samples,
            protectedTerrainZones = source.protectedTerrainZones,
            fortresses = source.fortresses,
            catapults = source.catapults,
            objects = source.objects,
            initialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
            seed = source.generationMetadata.seed,
            generatorVersion = source.generationMetadata.generatorVersion,
            authoringVersion = source.generationMetadata.authoringVersion,
            source = "addLevel.compatibility-fixture",
        )

        val generated = LevelDefinitionBuilder.buildValidated(request)
        val registry = CampaignLevelRegistry(
            levels = mapOf(
                1 to CampaignLevelDefinition.forLevel(1).copy(mapDefinition = generated),
            ),
        )
        val scenario = registry.scenario(1)

        assertTrue(MapDefinitionValidator.validate(generated).isValid)
        assertEquals(generated, registry.mapDefinition(1))
        assertEquals(generated, scenario.mapDefinition)
    }

    @Test
    fun demoDefinitionIsValidAndNotRegisteredAsCampaignLevel() {
        assertTrue(MapDefinitionValidator.validate(DemoLevelDefinitions.default.mapDefinition).isValid)
        assertEquals("demo-map", DemoLevelDefinitions.default.mapDefinition.levelId)
        assertTrue(CampaignLevelRegistry.default().mapDefinition(1).levelId != "demo-map")
    }
}
