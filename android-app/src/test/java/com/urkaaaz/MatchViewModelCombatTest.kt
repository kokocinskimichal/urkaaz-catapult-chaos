package com.urkaaaz

import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchViewModelCombatTest {
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
