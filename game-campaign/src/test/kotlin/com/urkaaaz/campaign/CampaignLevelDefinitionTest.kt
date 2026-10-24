package com.urkaaaz.campaign

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.AiProfileId
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
        assertEquals(
            setOf(AmmunitionType.ROCK, AmmunitionType.SIEGE_BOMB),
            level.availableAmmunition,
        )
        assertEquals(setOf(SpellType.FORTRESS_SHIELD), level.availableSpells)
        assertFalse(SpellType.ICE_TRAP in level.availableSpells)
        assertTrue(level.tutorial.enabled)
        assertEquals(AiProfileId.CADET, level.enemy.aiProfile.profileId)
    }

    @Test
    fun powderBarrelBecomesAvailableAtLevelTen() {
        assertFalse(
            AmmunitionType.POWDER_BARREL in
                CampaignLevelDefinition.forLevel(9).availableAmmunition,
        )
        assertTrue(
            AmmunitionType.POWDER_BARREL in
                CampaignLevelDefinition.forLevel(10).availableAmmunition,
        )
    }

    @Test
    fun campaignAiDifficultyProgressesFromCadetToVeteranToElite() {
        assertEquals(AiProfileId.CADET, CampaignLevelDefinition.forLevel(1).enemy.aiProfile.profileId)
        assertEquals(AiProfileId.CADET, CampaignLevelDefinition.forLevel(10).enemy.aiProfile.profileId)
        assertEquals(AiProfileId.VETERAN, CampaignLevelDefinition.forLevel(11).enemy.aiProfile.profileId)
        assertEquals(AiProfileId.VETERAN, CampaignLevelDefinition.forLevel(30).enemy.aiProfile.profileId)
        assertEquals(AiProfileId.ELITE, CampaignLevelDefinition.forLevel(31).enemy.aiProfile.profileId)
        assertEquals(AiProfileId.ELITE, CampaignLevelDefinition.forLevel(100).enemy.aiProfile.profileId)
    }

    @Test
    fun milestoneSpellsBecomeAvailableAtTheirConfiguredLevels() {
        assertFalse(SpellType.HASTE in CampaignLevelDefinition.forLevel(5).availableSpells)
        assertTrue(SpellType.HASTE in CampaignLevelDefinition.forLevel(6).availableSpells)
        assertTrue(SpellType.HASTE in CampaignLevelDefinition.forLevel(7).availableSpells)
        assertFalse(SpellType.HEAL in CampaignLevelDefinition.forLevel(7).availableSpells)
        assertTrue(SpellType.HEAL in CampaignLevelDefinition.forLevel(8).availableSpells)
        assertTrue(SpellType.HEAL in CampaignLevelDefinition.forLevel(9).availableSpells)
    }

    @Test
    fun chapterOneLevelThreeUsesImportedMapWithoutOverridingCampaignSettings() {
        val level = CampaignLevelDefinition.forLevel(3)

        assertEquals("chapter1-level3", level.mapDefinition.mapId)
        assertEquals(AiProfileId.CADET, level.enemy.aiProfile.profileId)
        assertEquals(0, level.enemy.aiProfile.progressionPercent)
        assertEquals(12, level.startingAmmunition)
        assertEquals(1f, level.initialWind.direction)
        assertEquals(0.55f, level.initialWind.strength)
        assertFalse(level.tutorial.enabled)
    }

    @Test
    fun chapterOneLevelFourUsesImportedMapWithoutOverridingCampaignSettings() {
        val level = CampaignLevelDefinition.forLevel(4)

        assertEquals("chapter1-level4", level.mapDefinition.mapId)
        assertEquals(AiProfileId.CADET, level.enemy.aiProfile.profileId)
        assertEquals(14, level.startingAmmunition)
        assertEquals(-1f, level.initialWind.direction)
        assertEquals(0.7f, level.initialWind.strength, 0.0001f)
        assertFalse(level.tutorial.enabled)
    }

    @Test
    fun allChapterTwoLevelsUseTheFrostboundBiome() {
        (11..20).forEach { levelNumber ->
            assertEquals(
                "frostbound",
                CampaignLevelDefinition.forLevel(levelNumber).mapDefinition.biome,
                "level $levelNumber should use Frostbound",
            )
        }
        assertEquals("green_frontier", CampaignLevelDefinition.forLevel(10).mapDefinition.biome)
        assertEquals("green_frontier", CampaignLevelDefinition.forLevel(21).mapDefinition.biome)
    }

    @Test
    fun milestoneUnlocksRemainDeterministicWhileMapAssignmentIsTemporary() {
        val level = CampaignLevelDefinition.forLevel(41)

        assertEquals("demo-map", level.mapDefinition.mapId)
        assertEquals("green_frontier", level.mapDefinition.biome)
        assertTrue(level.availableUnits.any { it.id == UnitFactory.slingmaster().id })
        assertTrue(AmmunitionType.PLAGUE_CAULDRON in level.availableAmmunition)
        assertTrue(SpellType.RUNIC_BASTION in level.availableSpells)
        assertEquals(AiProfileId.ELITE, level.enemy.aiProfile.profileId)
        assertEquals(14, level.enemy.aiProfile.progressionPercent)
        assertEquals(0, CampaignLevelDefinition.forLevel(31).enemy.aiProfile.progressionPercent)
        assertEquals(100, CampaignLevelDefinition.forLevel(100).enemy.aiProfile.progressionPercent)
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
        assertEquals(AiProfileId.CADET, scenario.aiProfile.profileId)
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
        assertEquals(
            level.availableSpells.map { it.name }.sorted(),
            scenario.availableSpells,
        )
        assertEquals(listOf("DEFENDER"), scenario.playerLoadout?.unitTypes)
        assertEquals(listOf("HASTE"), scenario.playerLoadout?.spellTypes)
    }
}
