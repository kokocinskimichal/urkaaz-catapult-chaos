package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchSessionTest {
    @Test
    fun startCommandProducesRunningSnapshot() {
        val snapshot = MatchSession().dispatch(
            MatchCommand.Start(
                commandId = CommandId("command-1"),
                matchId = MatchId("match-1"),
                playerId = PlayerId("player-1"),
            ),
        )

        assertEquals(MatchStatus.RUNNING, snapshot.status)
    }

    @Test
    fun selectedAmmunitionIsUsedByTheNextProjectile() {
        val session = MatchSession()
        val matchId = MatchId("match-ammo")
        val playerId = PlayerId("player-1")
        session.dispatch(MatchCommand.Start(CommandId("start"), matchId, playerId))
        session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )

        val snapshot = session.dispatch(
            MatchCommand.Fire(
                CommandId("fire"),
                matchId,
                playerId,
                EntityId("blue-catapult"),
            ),
        )

        assertEquals("SIEGE_BOMB", snapshot.projectiles.single().ammunitionType)
        assertEquals(5.5f, snapshot.reloadRemainingSeconds[com.urkaaaz.contracts.Team.BLUE])
    }

    @Test
    fun eventsAreConsumedExactlyOnce() {
        val session = MatchSession()
        session.dispatch(
            MatchCommand.Start(
                commandId = CommandId("start-events"),
                matchId = MatchId("match-events"),
                playerId = PlayerId("player-events"),
            ),
        )

        val firstRead = session.consumeEvents()
        val secondRead = session.consumeEvents()

        assertTrue(firstRead.isNotEmpty())
        assertTrue(secondRead.isEmpty())
    }
}
