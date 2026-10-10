package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.WindSnapshot
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
    fun initialWindIsAppliedToTheSimulationAndSnapshot() {
        val session = MatchSession(
            initialWind = WindSnapshot(direction = -1f, strength = 0.5f),
        )
        val matchId = MatchId("match-wind")
        val playerId = PlayerId("player-wind")

        session.dispatch(MatchCommand.Start(CommandId("start-wind"), matchId, playerId))
        val snapshot = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance-wind"),
                matchId,
                playerId,
                33,
            ),
        )

        assertEquals(-1f, snapshot.wind.direction)
        assertEquals(0.5f, snapshot.wind.strength)
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

    @Test
    fun deployingUnitConsumesGoldAndAddsUnitToSnapshot() {
        val session = MatchSession()
        val matchId = MatchId("match-units")
        val playerId = PlayerId("player-units")
        session.dispatch(MatchCommand.Start(CommandId("start-units"), matchId, playerId))

        val snapshot = session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("deploy-units"),
                matchId,
                playerId,
                "DEFENDER",
            ),
        )

        assertEquals(1, snapshot.units.size)
        assertEquals("DEFENDER", snapshot.units.single().unitType)
        assertEquals(15, snapshot.resourcesByTeam[com.urkaaaz.contracts.Team.BLUE]?.supply)
        assertTrue(session.consumeEvents().any { it is com.urkaaaz.contracts.MatchEvent.UnitDeployed })
    }

    @Test
    fun sendingWaveMovesDeployedUnitsAndMarksThemAsMoving() {
        val session = MatchSession()
        val matchId = MatchId("match-wave")
        val playerId = PlayerId("player-wave")
        session.dispatch(MatchCommand.Start(CommandId("start-wave"), matchId, playerId))
        session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("deploy-wave"),
                matchId,
                playerId,
                "SAPPER",
            ),
        )
        val before = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("before-wave"),
                matchId,
                playerId,
                33,
            ),
        )
        val after = session.dispatch(
            MatchCommand.SendWave(CommandId("send-wave"), matchId, playerId),
        )
        val advanced = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("after-wave"),
                matchId,
                playerId,
                33,
            ),
        )

        assertTrue(after.units.single().moving)
        assertTrue(advanced.units.single().x > before.units.single().x)
    }
}
