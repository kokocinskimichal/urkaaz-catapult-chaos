package com.urkaaaz

import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchViewModelCombatTest {
    @Test
    fun defenderHealthChangesThroughTheAndroidMatchPipeline() {
        val viewModel = MatchViewModel()

        viewModel.setAiUnitAutomationEnabled(false)
        viewModel.dispatch(MatchUiAction.StartMatch)
        viewModel.dispatch(MatchUiAction.DeployUnit("DEFENDER"))
        viewModel.debugRecruitEnemyUnit("DEFENDER")
        viewModel.dispatch(MatchUiAction.SendWave)
        viewModel.debugSendEnemyWave()

        var damaged = false
        repeat(1_000) {
            viewModel.dispatch(MatchUiAction.AdvanceSimulation)
            damaged = damaged || viewModel.renderState.units.any {
                it.unitType == "DEFENDER" && it.health < it.maxHealth
            }
        }

        assertTrue(damaged)
    }
}
