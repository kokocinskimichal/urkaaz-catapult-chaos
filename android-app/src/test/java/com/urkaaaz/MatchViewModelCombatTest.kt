package com.urkaaaz

import com.urkaaaz.android.R
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.Team
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchViewModelCombatTest {
    @Test
    fun trajectoryPresentationUsesTheSelectedAmmunitionAndRuntimePhysicsBalance() {
        val catalog = LoadoutCatalog.bundled()
        val viewModel = MatchViewModel(demoMode = true, loadoutCatalog = catalog)

        assertEquals(catalog.gravityAccelerationY, viewModel.renderState.gravityAccelerationY)
        assertEquals(catalog.windAccelerationScale, viewModel.renderState.windAccelerationScale)
        assertEquals(catalog.initialAimDirectionDegrees, viewModel.renderState.playerAngleDegrees)
        assertEquals(catalog.initialAimPower, viewModel.renderState.playerPower)

        viewModel.dispatch(MatchUiAction.StartMatch)
        val selected = viewModel.dispatch(MatchUiAction.SelectAmmo("SIEGE_BOMB"))
        val ammunition = requireNotNull(catalog.findAmmunition("SIEGE_BOMB"))

        assertEquals(ammunition.initialSpeedMultiplier, selected.projectileInitialSpeedMultiplier)
        assertEquals(ammunition.windResponse, selected.projectileWindResponse)
        assertEquals(ammunition.gravityResponse, selected.projectileGravityResponse)
        assertEquals(ammunition.drag, selected.projectileDrag)
    }

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

        assertEquals(listOf("ROCK"), viewModel.selectableAmmunitionTypes)
    }

    @Test
    fun demoMatchLimitsDisplayedAmmunitionChoicesToThree() {
        val viewModel = MatchViewModel(demoMode = true)

        assertEquals(PlayerLoadoutConfig.AMMUNITION_SLOT_COUNT, viewModel.selectableAmmunitionTypes.size)
    }

    @Test
    fun selectingAmmunitionUpdatesThePresentationState() {
        val viewModel = MatchViewModel(demoMode = true)
        viewModel.dispatch(MatchUiAction.StartMatch)

        val state = viewModel.dispatch(MatchUiAction.SelectAmmo("SIEGE_BOMB"))

        assertEquals("SIEGE_BOMB", state.selectedAmmunitionType)
    }

    @Test
    fun retreatFinishesTheMatchWithTheSurrenderOutcome() {
        val viewModel = MatchViewModel()
        viewModel.dispatch(MatchUiAction.StartMatch)

        val state = viewModel.dispatch(MatchUiAction.Retreat)

        assertEquals("FINISHED", state.statusLabel)
        assertEquals("SURRENDER", state.outcomeLabel)
    }

    @Test
    fun equippedSpellCanBeCastThroughTheSharedSpellAction() {
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

        val active = viewModel.dispatch(MatchUiAction.CastSpell("FORTRESS_SHIELD"))

        assertEquals(20, active.playerMana)
        assertEquals(150, active.blueFortressShieldHealth)
        assertEquals(R.string.match_spell_feedback_cast, active.spellFeedbackLabel)

        val rejected = viewModel.dispatch(MatchUiAction.CastSpell("FORTRESS_SHIELD"))

        assertEquals(20, rejected.playerMana)
        assertEquals(150, rejected.blueFortressShieldHealth)
        assertEquals(R.string.match_spell_feedback_already_active, rejected.spellFeedbackLabel)
    }

    @Test
    fun hasteShowsItsTimerAndCanBeRefreshedFromTheSharedSpellAction() {
        val viewModel = MatchViewModel(
            campaignLevel = 8,
            playerLoadout = PlayerLoadoutConfig(
                playerId = PlayerId("player"),
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("HASTE"),
            ),
        )
        viewModel.dispatch(MatchUiAction.StartMatch)

        val active = viewModel.dispatch(MatchUiAction.CastSpell("HASTE"))
        assertEquals(50, active.playerMana)
        assertTrue("HASTE" in active.activeSpellTypes)
        assertEquals(15_000L, active.activeSpellRemainingMilliseconds["HASTE"])

        val refreshed = viewModel.dispatch(MatchUiAction.CastSpell("HASTE"))
        assertEquals(0, refreshed.playerMana)
        assertTrue("HASTE" in refreshed.activeSpellTypes)
        assertEquals(15_000L, refreshed.activeSpellRemainingMilliseconds["HASTE"])
        assertEquals(R.string.match_spell_feedback_cast, refreshed.spellFeedbackLabel)
    }

    @Test
    fun spellActivationWithoutEquippedSpellIsReported() {
        val viewModel = MatchViewModel(campaignLevel = 1)
        viewModel.dispatch(MatchUiAction.StartMatch)

        val rejected = viewModel.dispatch(MatchUiAction.CastSpell("FORTRESS_SHIELD"))

        assertEquals(100, rejected.playerMana)
        assertEquals(null, rejected.blueFortressShieldHealth)
        assertEquals(R.string.match_spell_feedback_not_equipped, rejected.spellFeedbackLabel)
    }

    @Test
    fun campaignMatchOnlyShowsUnitsAvailableAtTheSelectedLevel() {
        val viewModel = MatchViewModel(campaignLevel = 1)

        assertEquals(listOf("DEFENDER"), viewModel.selectableUnitTypes)
        assertEquals(
            mapOf(
                Team.BLUE to setOf("DEFENDER"),
                Team.RED to setOf("DEFENDER"),
            ),
            viewModel.unitAnimationPreloadTypesByTeam,
        )
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

        assertEquals(listOf("DEFENDER"), viewModel.selectableUnitTypes)
        assertEquals(
            mapOf(
                Team.BLUE to setOf("DEFENDER"),
                Team.RED to setOf("DEFENDER", "SAPPER", "DEMOLISHER"),
            ),
            viewModel.unitAnimationPreloadTypesByTeam,
        )
    }

    @Test
    fun demoMatchLimitsDisplayedUnitChoicesToLoadoutCapacity() {
        val viewModel = MatchViewModel(demoMode = true)

        assertEquals(PlayerLoadoutConfig.MAX_UNITS, viewModel.selectableUnitTypes.size)
        assertEquals(
            viewModel.selectableUnitTypes.distinct(),
            viewModel.selectableUnitTypes,
        )
        assertEquals(
            mapOf(
                Team.BLUE to viewModel.selectableUnitTypes.toSet(),
                Team.RED to
                    setOf("DEFENDER", "SAPPER", "DEMOLISHER", "RAGING_BOAR", "SLINGMASTER"),
            ),
            viewModel.unitAnimationPreloadTypesByTeam,
        )
    }

    @Test
    fun demoMatchUsesTheCommittedLoadoutForAllPlayerChoices() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("SAPPER"),
            ammunitionSlots = listOf("ROCK", "POWDER_BARREL", null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )

        val viewModel = MatchViewModel(demoMode = true, playerLoadout = loadout)

        assertEquals(listOf("SAPPER"), viewModel.selectableUnitTypes)
        assertEquals(listOf("ROCK", "POWDER_BARREL"), viewModel.selectableAmmunitionTypes)
        assertEquals(listOf("FORTRESS_SHIELD"), viewModel.selectableSpellTypes)
    }

    @Test
    fun demoRuntimeRejectsUnitsOutsideTheCommittedLoadout() {
        val viewModel = MatchViewModel(
            demoMode = true,
            playerLoadout = PlayerLoadoutConfig(
                playerId = PlayerId("player"),
                unitTypes = listOf("SAPPER"),
                ammunitionSlots = listOf("ROCK", null, null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        viewModel.setAiUnitAutomationEnabled(false)
        viewModel.dispatch(MatchUiAction.StartMatch)

        viewModel.dispatch(MatchUiAction.DeployUnit("DEFENDER"))
        assertTrue(viewModel.renderState.units.none { it.teamLabel == "BLUE" })

        viewModel.dispatch(MatchUiAction.DeployUnit("SAPPER"))
        assertTrue(viewModel.renderState.units.any {
            it.teamLabel == "BLUE" && it.unitType == "SAPPER"
        })
    }

    @Test
    fun spellChoicesFollowCommittedLoadoutOrderAndCampaignAvailability() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = listOf("FORTRESS_SHIELD", "HASTE"),
        )
        val viewModel = MatchViewModel(campaignLevel = 8, playerLoadout = loadout)

        assertEquals(listOf("FORTRESS_SHIELD", "HASTE"), viewModel.selectableSpellTypes)
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
