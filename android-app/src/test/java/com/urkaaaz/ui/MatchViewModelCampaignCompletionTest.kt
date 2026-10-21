package com.urkaaaz.ui

import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.application.LocalMatchGateway
import com.urkaaaz.application.MatchSession
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchViewModelCampaignCompletionTest {
    @Test
    fun campaignMatchFinishedEventIsForwardedOnceWithItsLevelContext() {
        val forwarded = mutableListOf<Pair<Int, MatchEvent.MatchFinished>>()
        val viewModel = MatchViewModel(
            campaignLevel = 5,
            onMatchFinished = { level, event -> forwarded += level to event },
        )
        val event = finishedEvent()

        viewModel.forwardCampaignCompletionEvents(listOf(event))
        viewModel.forwardCampaignCompletionEvents(listOf(event))

        assertEquals(listOf(5 to event), forwarded)
    }

    @Test
    fun demoMatchDoesNotForwardCampaignCompletion() {
        var forwarded = false
        val viewModel = MatchViewModel(
            campaignLevel = 1,
            demoMode = true,
            onMatchFinished = { _, _ -> forwarded = true },
        )

        viewModel.forwardCampaignCompletionEvents(listOf(finishedEvent()))

        assertEquals(false, forwarded)
    }

    @Test
    fun terminalTickRecoveryForwardsQueuedCampaignCompletionEvent() {
        val forwarded = mutableListOf<MatchEvent.MatchFinished>()
        val gateway = LocalMatchGateway(
            session = MatchSession(
                blueFortressMaxHealth = 1,
                randomSeedProvider = { 17L },
            ),
        )
        val viewModel = MatchViewModel(
            gateway = gateway,
            onMatchFinished = { _, event -> forwarded += event },
        )
        val matchId = MatchId("local-match")
        val redPlayerId = PlayerId("local-ai")
        viewModel.dispatch(MatchUiAction.StartMatch)
        gateway.dispatch(
            MatchCommand.DeployUnit(
                CommandId("terminal-recovery-deploy"),
                matchId,
                redPlayerId,
                "DEFENDER",
            ),
        )
        gateway.dispatch(
            MatchCommand.SendWave(
                CommandId("terminal-recovery-wave"),
                matchId,
                redPlayerId,
            ),
        )

        var tick = 0
        while (gateway.snapshot().status == MatchStatus.RUNNING && tick < 600) {
            gateway.dispatch(
                MatchCommand.AdvanceSimulation(
                    CommandId("terminal-recovery-tick-$tick"),
                    matchId,
                    redPlayerId,
                    100,
                ),
            )
            tick++
        }

        assertEquals(MatchStatus.FINISHED, gateway.snapshot().status)
        viewModel.dispatch(MatchUiAction.AdvanceSimulation)

        assertEquals(1, forwarded.size)
        assertEquals(MatchOutcome.RED_WIN, forwarded.single().outcome)
    }

    private fun finishedEvent() = MatchEvent.MatchFinished(
        eventId = EventId("finished"),
        matchId = MatchId("match-1"),
        simulationTimeMilliseconds = 1L,
        outcome = MatchOutcome.BLUE_WIN,
    )
}
