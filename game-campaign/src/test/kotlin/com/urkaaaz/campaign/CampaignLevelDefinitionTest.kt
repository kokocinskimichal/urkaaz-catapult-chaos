package com.urkaaaz.campaign

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignLevelDefinitionTest {
    @Test
    fun earlyLevelExposesOnlyEarlyGameplayOptions() {
        val level = CampaignLevelDefinition.forLevel(1)

        assertEquals(TerrainBiome.GREEN_FRONTIER, level.terrain.biome)
        assertTrue(UnitType.DEFENDER in level.availableUnits)
        assertFalse(UnitType.SAPPER in level.availableUnits)
        assertFalse(AmmunitionType.POWDER_BARREL in level.availableAmmunition)
        assertFalse(SpellType.ICE_TRAP in level.availableSpells)
        assertTrue(level.tutorial.enabled)
    }

    @Test
    fun milestoneUnlocksAndBiomeScheduleAreDeterministic() {
        val level = CampaignLevelDefinition.forLevel(41)

        assertEquals(TerrainBiome.SUNKEN_MARSHES, level.terrain.biome)
        assertTrue(UnitType.SLINGMASTER in level.availableUnits)
        assertTrue(AmmunitionType.PLAGUE_CAULDRON in level.availableAmmunition)
        assertTrue(SpellType.RUNIC_BASTION in level.availableSpells)
        assertEquals(CampaignAiDifficulty.HARD, level.enemy.difficulty)
    }

    @Test
    fun levelDefinitionProducesContractScenarioWithoutProgressionState() {
        val scenario = CampaignLevelDefinition.forLevel(6).toMatchScenario(
            playerId = PlayerId("blue-player"),
            opponentId = PlayerId("red-player"),
        )

        assertEquals("campaign-6", scenario.scenarioId)
        assertEquals(2, scenario.players.size)
        assertEquals("blue-player", scenario.players.first().playerId.value)
        assertTrue("SIEGE_BOMB" in scenario.availableAmmunition)
        assertTrue(scenario.initialResources.ammunition.isNotEmpty())
        assertEquals("DEFENDER", scenario.initialUnits.first().unitType)
    }
}
