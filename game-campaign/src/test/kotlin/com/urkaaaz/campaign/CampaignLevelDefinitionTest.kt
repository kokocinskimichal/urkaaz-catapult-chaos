package com.urkaaaz.campaign

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignLevelDefinitionTest {
    @Test
    fun earlyLevelExposesOnlyEarlyGameplayOptions() {
        val level = CampaignLevelDefinition.forLevel(1)

        assertEquals("green_frontier", level.mapDefinition.biome)
        assertTrue(level.availableUnits.any { it.id == UnitFactory.defender().id })
        assertFalse(level.availableUnits.any { it.id == UnitFactory.sapper().id })
        assertFalse(AmmunitionType.POWDER_BARREL in level.availableAmmunition)
        assertFalse(SpellType.ICE_TRAP in level.availableSpells)
        assertTrue(level.tutorial.enabled)
    }

    @Test
    fun chapterOneLevelThreeUsesImportedMapWithoutOverridingCampaignSettings() {
        val level = CampaignLevelDefinition.forLevel(3)

        assertEquals("chapter1-level3", level.mapDefinition.mapId)
        assertEquals(CampaignAiDifficulty.MEDIUM, level.enemy.difficulty)
        assertEquals(12, level.startingAmmunition)
        assertEquals(1f, level.initialWind.direction)
        assertEquals(0.55f, level.initialWind.strength)
        assertFalse(level.tutorial.enabled)
    }

    @Test
    fun chapterOneLevelFourUsesImportedMapWithoutOverridingCampaignSettings() {
        val level = CampaignLevelDefinition.forLevel(4)

        assertEquals("chapter1-level4", level.mapDefinition.mapId)
        assertEquals(CampaignAiDifficulty.MEDIUM, level.enemy.difficulty)
        assertEquals(0.92f, level.enemy.aimErrorScale)
        assertEquals(0.88f, level.enemy.healthMultiplier)
        assertEquals(0.88f, level.enemy.fortressScale)
        assertEquals(14, level.startingAmmunition)
        assertEquals(-1f, level.initialWind.direction)
        assertEquals(0.7f, level.initialWind.strength, 0.0001f)
        assertFalse(level.tutorial.enabled)
    }

    @Test
    fun milestoneUnlocksRemainDeterministicWhileMapAssignmentIsTemporary() {
        val level = CampaignLevelDefinition.forLevel(41)

        assertEquals("demo-map", level.mapDefinition.mapId)
        assertEquals("green_frontier", level.mapDefinition.biome)
        assertTrue(level.availableUnits.any { it.id == UnitFactory.slingmaster().id })
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
        assertTrue("DEFENDER" in scenario.availableUnits)
        assertTrue(scenario.initialUnits.isEmpty())
        assertTrue("SIEGE_BOMB" in scenario.availableAmmunition)
        assertTrue(scenario.initialResources.ammunition.isNotEmpty())
    }

    @Test
    fun scenarioKeepsPlayerSelectionSeparateFromLevelAvailability() {
        val level = CampaignLevelDefinition.forLevel(8)
        val selected = PlayerLoadoutConfig(
            playerId = PlayerId("blue-player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
            spellTypes = listOf("HASTE"),
        )

        val scenario = level.toMatchScenario(
            playerId = PlayerId("blue-player"),
            opponentId = PlayerId("red-player"),
            playerLoadout = selected,
        )

        assertEquals(listOf("DEFENDER", "DEMOLISHER", "SAPPER"), scenario.availableUnits)
        assertEquals(listOf("DEFENDER"), scenario.playerLoadout?.unitTypes)
        assertEquals(listOf("HASTE"), scenario.playerLoadout?.spellTypes)
    }
}
