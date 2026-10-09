package com.urkaaaz.application

import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMatchGatewayTest {
    @Test
    fun localGatewayRunsAndAdvancesAnInMemoryMatch() {
        val gateway = LocalMatchGateway()
        val start = MatchCommand.Start(
            CommandId("start"),
            MatchId("match-1"),
            PlayerId("player-1"),
        )

        assertEquals(MatchStatus.RUNNING, gateway.dispatch(start).status)
        gateway.dispatch(
            MatchCommand.Fire(
                CommandId("fire"),
                MatchId("match-1"),
                PlayerId("player-1"),
                EntityId("blue-catapult"),
            ),
        )
        val advanced = gateway.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance"),
                MatchId("match-1"),
                PlayerId("player-1"),
                100,
            ),
        )

        assertTrue(advanced.projectiles.isNotEmpty())
    }

    @Test
    fun stoppingAndRestartingCreatesFreshState() {
        val gateway = LocalMatchGateway()
        gateway.dispatch(
            MatchCommand.Start(CommandId("start"), MatchId("match-1"), PlayerId("player-1")),
        )
        gateway.stop()

        val restarted = gateway.restart(MatchId("match-2"), PlayerId("player-1"))

        assertEquals(MatchStatus.RUNNING, restarted.status)
        assertEquals(MatchId("match-2"), restarted.matchId)
        assertTrue(restarted.projectiles.isEmpty())
    }
}
