package com.urkaaaz.domain

import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.Team

/**
 * Immutable aggregate state for the domain-level lifecycle of one match.
 *
 * Detailed time-based behavior belongs to the simulation layer; this model
 * only enforces valid lifecycle transitions and keeps domain entities grouped.
 */
data class MatchState(
    val matchId: MatchId,
    val phase: MatchPhase = MatchPhase.CREATED,
    val activeTeam: Team? = null,
    val catapults: List<Catapult> = emptyList(),
    val fortresses: List<Fortress> = emptyList(),
    val units: List<Unit> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val outcome: MatchOutcome? = null,
) {
    init {
        require(fortresses.map { it.team }.distinct().size <= 2) {
            "a match may contain at most two fortress teams"
        }
    }

    val isFinished: Boolean
        get() = phase == MatchPhase.FINISHED

    fun start(firstTeam: Team): MatchState {
        check(phase == MatchPhase.CREATED) { "only a created match can start" }
        return copy(phase = MatchPhase.PLAYER_TURN, activeTeam = firstTeam)
    }

    fun finish(result: MatchOutcome): MatchState =
        copy(phase = MatchPhase.FINISHED, activeTeam = null, outcome = result)
}
