package com.urkaaaz.application.reward

import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId

class CompositeMatchEventObserver(
    private val observers: List<MatchEventObserver>,
) : MatchEventObserver {
    override fun onEvents(events: List<MatchEvent>) {
        observers.forEach { it.onEvents(events) }
    }

    override fun onMatchGenerationStarted(matchId: MatchId) {
        observers.forEach { it.onMatchGenerationStarted(matchId) }
    }

    override fun rewardResult(): LevelRewardResult? =
        observers.firstNotNullOfOrNull { it.rewardResult() }
}
