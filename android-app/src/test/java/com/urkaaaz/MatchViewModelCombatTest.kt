package com.urkaaaz

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchViewModelCombatTest {
    @Test
    fun matchViewModelExposesTheCurrentSeededReplay() {
        val viewModel = MatchViewModel()

        viewModel.dispatch(MatchUiAction.StartMatch)

        assertNotNull(viewModel.recordedReplay()?.randomSeed)
    }

    @Test
    fun campaignMatchUsesCommittedLoadoutPlayerIdByDefault() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        val viewModel = MatchViewModel(campaignLevel = 1, playerLoadout = loadout)

        assertNotNull(viewModel.renderState)
    }

    @Test
    fun campaignMatchOnlyOffersAmmunitionFromTheCommittedLoadout() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        val viewModel = MatchViewModel(campaignLevel = 1, playerLoadout = loadout)

        assertEquals(setOf("ROCK"), viewModel.selectableAmmunitionTypes)
    }

    @Test
    fun equippedFortressShieldCanBeCastAndRejectedWhenAlreadyActive() {
        val viewModel = MatchViewModel(
            campaignLevel = 1,
            playerLoadout = PlayerLoadoutConfig(
                playerId = PlayerId("player"),
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        viewModel.dispatch(MatchUiAction.StartMatch)

        val active = viewModel.dispatch(MatchUiAction.CastFortressShield)

        assertEquals(20, active.playerMana)
        assertEquals(80, active.blueFortressShieldHealth)
        assertEquals("SHIELD ACTIVATED", active.spellFeedbackLabel)

        val rejected = viewModel.dispatch(MatchUiAction.CastFortressShield)

        assertEquals(20, rejected.playerMana)
        assertEquals(80, rejected.blueFortressShieldHealth)
        assertEquals("SHIELD ALREADY ACTIVE", rejected.spellFeedbackLabel)
    }

    @Test
    fun fortressShieldActivationWithoutEquippedSpellIsReported() {
        val viewModel = MatchViewModel(campaignLevel = 1)
        viewModel.dispatch(MatchUiAction.StartMatch)

        val rejected = viewModel.dispatch(MatchUiAction.CastFortressShield)

        assertEquals(100, rejected.playerMana)
        assertEquals(null, rejected.blueFortressShieldHealth)
        assertEquals("SHIELD NOT EQUIPPED", rejected.spellFeedbackLabel)
    }

    @Test
    fun campaignMatchOnlyShowsUnitsAvailableAtTheSelectedLevel() {
        val viewModel = MatchViewModel(campaignLevel = 1)

        assertEquals(setOf("DEFENDER"), viewModel.selectableUnitTypes)
    }

    @Test
    fun campaignMatchUnitAvailabilityIsRestrictedByTheCommittedLoadout() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val viewModel = MatchViewModel(campaignLevel = 8, playerLoadout = loadout)

        assertEquals(setOf("DEFENDER"), viewModel.selectableUnitTypes)
    }

    @Test
    fun demoMatchDoesNotFilterUnitTypesByCampaignAvailability() {
        val viewModel = MatchViewModel(demoMode = true)

        assertEquals(null, viewModel.selectableUnitTypes)
    }

    @Test
    fun campaignAiUsesLevelUnitsRatherThanThePlayersSelectedLoadout() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
            spellTypes = listOf("FORTRESS_SHIELD", "HASTE"),
        )

        val viewModel = MatchViewModel(campaignLevel = 8, playerLoadout = loadout)

        assertEquals(
            setOf("DEFENDER", "SAPPER", "DEMOLISHER"),
            viewModel.aiAllowedUnitIds(),
        )
    }

    @Test
    fun levelOneCampaignAiUsesOnlyLevelAmmunition() {
        val viewModel = MatchViewModel(campaignLevel = 1)

        assertEquals(setOf("ROCK", "SIEGE_BOMB"), viewModel.aiAllowedAmmunitionIds())
    }

    @Test
    fun campaignLevelsUseStandardFortressHealthAndDemoKeepsItsBlueBonus() {
        val levelOne = MatchViewModel(campaignLevel = 1)
        levelOne.dispatch(MatchUiAction.StartMatch)

        assertEquals(500, levelOne.renderState.blueFortressMaxHealth)
        assertEquals(500, levelOne.renderState.redFortressMaxHealth)

        val levelTwo = MatchViewModel(campaignLevel = 2)
        levelTwo.dispatch(MatchUiAction.StartMatch)

        assertEquals(500, levelTwo.renderState.blueFortressMaxHealth)
        assertEquals(500, levelTwo.renderState.redFortressMaxHealth)

        val demo = MatchViewModel(demoMode = true)
        demo.dispatch(MatchUiAction.StartMatch)

        assertEquals(5_000, demo.renderState.blueFortressMaxHealth)
        assertEquals(500, demo.renderState.redFortressMaxHealth)
    }

    @Test
    fun defenderHealthChangesThroughTheAndroidMatchPipeline() {
        val viewModel = MatchViewModel(demoMode = true)

        viewModel.setAiUnitAutomationEnabled(false)
        viewModel.dispatch(MatchUiAction.StartMatch)
        viewModel.dispatch(MatchUiAction.DeployUnit("DEFENDER"))
        viewModel.debugRecruitEnemyUnit("DEFENDER")
        viewModel.dispatch(MatchUiAction.SendWave)
        viewModel.debugSendEnemyWave()

        var damaged = false
        for (step in 0 until MAX_SIMULATION_STEPS) {
            viewModel.dispatch(MatchUiAction.AdvanceSimulation)
            damaged = damaged || viewModel.renderState.units.any {
                it.unitType == "DEFENDER" && it.health < it.maxHealth
            }
            if (damaged) break
        }

        assertTrue(damaged)
    }

    private companion object {
        const val MAX_SIMULATION_STEPS = 3_000
    }
}
