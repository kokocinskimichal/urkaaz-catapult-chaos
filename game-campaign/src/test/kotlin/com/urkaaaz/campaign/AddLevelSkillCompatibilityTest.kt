package com.urkaaaz.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddLevelSkillCompatibilityTest {
    @Test
    fun registryUsesOnlyValidatedBundledJsonForCampaignLevels() {
        val registry = CampaignLevelRegistry.default()
        val first = registry.mapDefinition(1)
        val levelThree = registry.mapDefinition(3)
        val levelFour = registry.mapDefinition(4)
        val last = registry.mapDefinition(CampaignLevelDefinition.MAX_LEVELS)

        assertEquals("demo-map", first.mapId)
        assertEquals("chapter1-level3", levelThree.mapId)
        assertEquals("chapter1-level4", levelFour.mapId)
        assertEquals(first, last)
        assertTrue(MapDefinitionValidator.validate(first).isValid)
        assertTrue(MapDefinitionValidator.validate(levelFour).isValid)
    }

    @Test
    fun demoDefinitionComesFromTheBundledMapRepository() {
        val demo = DemoLevelDefinitions.default

        assertEquals("demo-map", demo.mapDefinition.mapId)
        assertEquals("green_frontier", demo.mapDefinition.biome)
        assertTrue(MapDefinitionValidator.validate(demo.mapDefinition).isValid)
    }
}
