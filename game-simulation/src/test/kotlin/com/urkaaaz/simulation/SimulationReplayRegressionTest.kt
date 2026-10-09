package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.AmmunitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimulationReplayRegressionTest {
    @Test
    fun identicalCommandAndTickSequenceProducesIdenticalTrace() {
        val first = runTrace()
        val second = runTrace()

        assertEquals(first.snapshots, second.snapshots)
        assertEquals(first.events, second.events)
    }

    @Test
    fun replayTraceKeepsProjectileAndTerrainTransitionsObservable() {
        val trace = runTrace()

        assertEquals(4, trace.snapshots.size)
        assertEquals(1, trace.snapshots[1].projectiles.size)
        assertEquals(1L, trace.snapshots.last().terrain.revision)
        assertEquals(
            listOf(
                MatchEvent.MatchStarted::class,
                MatchEvent.TurnChanged::class,
                MatchEvent.ProjectileFired::class,
                MatchEvent.ProjectileHitTerrain::class,
                MatchEvent.TerrainDeformed::class,
            ),
            trace.events.map { it::class },
        )
    }

    @Test
    fun limitedAmmunitionIsExposedAndConsumedWhenFired() {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("ammo-match"),
            config = SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                startingLimitedAmmunition = 2,
            ),
        )
        simulation.start()

        assertEquals(
            2,
            simulation.snapshot().resourcesByTeam[com.urkaaaz.contracts.Team.BLUE]
                ?.ammunition
                ?.get("POWDER_BARREL"),
        )

        simulation.fire(EntityId("blue-catapult"), AmmunitionType.POWDER_BARREL)

        assertEquals(
            1,
            simulation.snapshot().resourcesByTeam[com.urkaaaz.contracts.Team.BLUE]
                ?.ammunition
                ?.get("POWDER_BARREL"),
        )
    }

    @Test
    fun unavailableLimitedAmmunitionCannotBeFired() {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("empty-ammo-match"),
            config = SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                startingLimitedAmmunition = 0,
            ),
        )
        simulation.start()

        assertFailsWith<IllegalStateException> {
            simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)
        }
    }

    private fun runTrace(): Trace {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("regression-match"),
            config = SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
                windAccelerationX = 2f,
            ),
        )
        val events = mutableListOf<MatchEvent>()
        val snapshots = mutableListOf<MatchSnapshot>()
        events += simulation.start()
        snapshots += simulation.snapshot()
        events += simulation.fire(EntityId("blue-catapult"))
        snapshots += simulation.snapshot()
        events += simulation.advance(100)
        snapshots += simulation.snapshot()
        events += simulation.advance(2_000)
        snapshots += simulation.snapshot()
        return Trace(snapshots, events)
    }

    private data class Trace(
        val snapshots: List<MatchSnapshot>,
        val events: List<MatchEvent>,
    )
}
