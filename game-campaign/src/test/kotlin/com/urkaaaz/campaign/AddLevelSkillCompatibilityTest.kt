package com.urkaaaz.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddLevelSkillCompatibilityTest {
    @Test
    fun registryUsesOnlyValidatedBundledJsonForCampaignLevels() {
        val registry = CampaignLevelRegistry.default()
        val first = registry.mapDefinition(1)
        val last = registry.mapDefinition(CampaignLevelDefinition.MAX_LEVELS)

        assertEquals("demo-map", first.mapId)
        assertEquals(first, last)
        assertTrue(MapDefinitionValidator.validate(first).isValid)
    }

    @Test
    fun demoDefinitionComesFromTheBundledMapRepository() {
        val demo = DemoLevelDefinitions.default

        assertEquals("demo-map", demo.mapDefinition.mapId)
        assertEquals("green_frontier", demo.mapDefinition.biome)
        assertTrue(MapDefinitionValidator.validate(demo.mapDefinition).isValid)
    }
}
