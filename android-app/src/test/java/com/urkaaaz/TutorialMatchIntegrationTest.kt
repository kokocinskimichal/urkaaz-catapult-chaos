package com.urkaaaz

import com.urkaaaz.application.tutorial.TutorialPlayerActionKind
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialMatchIntegrationTest {
    @Test
    fun tutorialSimulationPauseDoesNotAdvanceOrRecordReplayCommands() {
        val viewModel = MatchViewModel(demoMode = true)
        viewModel.dispatch(MatchUiAction.StartMatch)
        val initialCommandCount = requireNotNull(viewModel.recordedReplay())
            .steps.count { it is com.urkaaaz.application.MatchReplayStep.Command }

        viewModel.setTutorialSimulationPaused(true)
        assertTrue(viewModel.dispatch(MatchUiAction.AdvanceSimulation).isPaused)

        val pausedCommandCount = requireNotNull(viewModel.recordedReplay())
            .steps.count { it is com.urkaaaz.application.MatchReplayStep.Command }
        assertEquals(initialCommandCount, pausedCommandCount)

        viewModel.setTutorialSimulationPaused(false)
        viewModel.dispatch(MatchUiAction.AdvanceSimulation)
        val resumedCommandCount = requireNotNull(viewModel.recordedReplay())
            .steps.count { it is com.urkaaaz.application.MatchReplayStep.Command }
        assertTrue(resumedCommandCount > pausedCommandCount)
    }

    @Test
    fun tutorialEffectsLeaveAcceptedPlayerReplayCommandsUnchanged() {
        val ordinary = MatchViewModel(demoMode = true)
        val tutored = MatchViewModel(demoMode = true)
        ordinary.dispatch(MatchUiAction.StartMatch)
        tutored.dispatch(MatchUiAction.StartMatch)
        tutored.setTutorialSimulationPaused(true)
        tutored.setTutorialAiSuspended(true)
        tutored.setTutorialSimulationPaused(false)
        tutored.setTutorialAiSuspended(false)

        listOf(ordinary, tutored).forEach { viewModel ->
            viewModel.dispatch(MatchUiAction.Aim(42f, 0.65f))
            viewModel.dispatch(MatchUiAction.FireRock)
        }

        val ordinaryPlayerCommands = requireNotNull(ordinary.recordedReplay())
            .steps.filterIsInstance<com.urkaaaz.application.MatchReplayStep.Command>()
            .map { it.value }
            .filterNot { it is com.urkaaaz.contracts.MatchCommand.Start }
        val tutoredPlayerCommands = requireNotNull(tutored.recordedReplay())
            .steps.filterIsInstance<com.urkaaaz.application.MatchReplayStep.Command>()
            .map { it.value }
            .filterNot { it is com.urkaaaz.contracts.MatchCommand.Start }
        assertEquals(ordinaryPlayerCommands, tutoredPlayerCommands)
    }

    @Test
    fun acceptedAmmoSelectionAndFireCommandsAreReportedToTheTutorial() {
        val acceptedActions = mutableListOf<TutorialPlayerActionKind>()
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("tutorial-player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "POWDER_BARREL", null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val inventory = PlayerInventoryConfig(
            playerId = loadout.playerId,
            ammunitionStocks = mapOf("POWDER_BARREL" to 0),
            specialSpellStocks = emptyMap(),
        )
        val viewModel = MatchViewModel(
            demoMode = true,
            playerLoadout = loadout,
            playerInventory = inventory,
        )

        viewModel.dispatch(MatchUiAction.StartMatch)
        viewModel.dispatch(MatchUiAction.SelectAmmo("POWDER_BARREL"))
        viewModel.dispatch(MatchUiAction.FireRock)
        viewModel.dispatch(MatchUiAction.SelectAmmo("ROCK"))
        viewModel.dispatch(MatchUiAction.Aim(45f, 0.6f))
        viewModel.dispatch(MatchUiAction.FireRock)
        acceptedActions += viewModel.consumeTutorialPlayerActions()

        assertEquals(
            listOf(
                TutorialPlayerActionKind.SELECT_AMMUNITION,
                TutorialPlayerActionKind.SELECT_AMMUNITION,
                TutorialPlayerActionKind.FIRE,
            ),
            acceptedActions,
        )
    }

    @Test
    fun acceptedUnitDeploymentAndWaveSendAreReportedToTheTutorial() {
        val loadout = PlayerLoadoutConfig(
            playerId = PlayerId("tutorial-player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val viewModel = MatchViewModel(
            demoMode = true,
            playerLoadout = loadout,
        )

        viewModel.dispatch(MatchUiAction.StartMatch)
        viewModel.dispatch(MatchUiAction.DeployUnit("DEFENDER"))
        viewModel.dispatch(MatchUiAction.SendWave)

        assertEquals(
            listOf(
                TutorialPlayerActionKind.DEPLOY_UNIT,
                TutorialPlayerActionKind.SEND_WAVE,
            ),
            viewModel.consumeTutorialPlayerActions(),
        )
    }

    @Test
    fun unavailableUnitDeploymentDoesNotReportTutorialAction() {
        val viewModel = MatchViewModel(demoMode = true)
        viewModel.dispatch(MatchUiAction.StartMatch)

        viewModel.dispatch(MatchUiAction.DeployUnit("UNAVAILABLE_UNIT"))

        assertTrue(viewModel.consumeTutorialPlayerActions().isEmpty())
    }
}
