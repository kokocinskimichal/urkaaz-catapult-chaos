package com.urkaaaz.ui

import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
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

    private fun finishedEvent() = MatchEvent.MatchFinished(
        eventId = EventId("finished"),
        matchId = MatchId("match-1"),
        simulationTimeMilliseconds = 1L,
        outcome = MatchOutcome.BLUE_WIN,
    )
}
