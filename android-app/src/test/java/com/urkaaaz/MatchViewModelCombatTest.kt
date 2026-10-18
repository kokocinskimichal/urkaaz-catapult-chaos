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
