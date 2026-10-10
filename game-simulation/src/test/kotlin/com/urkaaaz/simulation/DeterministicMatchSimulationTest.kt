package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.WorldBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicMatchSimulationTest {
    private val config = SimulationConfig(
        bounds = WorldBounds(width = 1_000f, height = 600f),
        gravityAccelerationY = 0f,
        windAccelerationX = 2f,
    )

    @Test
    fun startProducesDeterministicLifecycleEventsAndSnapshot() {
        val simulation = DeterministicMatchSimulation(MatchId("match-1"), config)

        val events = simulation.start()
        val snapshot = simulation.snapshot()

        assertEquals(listOf("event-1", "event-2"), events.map { it.eventId.value })
        assertEquals(MatchStatus.RUNNING, snapshot.status)
        assertEquals(MatchPhase.PLAYER_TURN, snapshot.phase)
        assertEquals(Team.BLUE, snapshot.activeTeam)
    }

    @Test
    fun projectileAdvancesWithWindAndCanBeInspectedInSnapshot() {
        val simulation = DeterministicMatchSimulation(MatchId("match-1"), config)
        simulation.start()

        val fired = simulation.fire(EntityId("blue-catapult"))
        simulation.advance(100)
        val snapshot = simulation.snapshot()

        assertTrue(fired.single() is MatchEvent.ProjectileFired)
        assertEquals(1, snapshot.projectiles.size)
        assertTrue(snapshot.projectiles.single().velocityX > 0f)
        assertEquals(0L, snapshot.terrain.revision)
    }

    @Test
    fun terrainImpactEmitsImpactAndDeformationAndIncrementsRevision() {
        val simulation = DeterministicMatchSimulation(
            MatchId("match-1"),
            SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"))

        val events = simulation.advance(2_000)

        assertTrue(events.any { it is MatchEvent.ProjectileHitTerrain })
        assertTrue(events.any { it is MatchEvent.TerrainDeformed })
        assertEquals(1L, simulation.snapshot().terrain.revision)
    }

    @Test
    fun plagueImpactDoesNotCreateTerrainCrater() {
        val simulation = DeterministicMatchSimulation(
            MatchId("plague-match"),
            SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.PLAGUE_CAULDRON)

        val events = simulation.advance(2_000)

        assertTrue(events.any { it is MatchEvent.ProjectileHitTerrain })
        assertEquals(0L, simulation.snapshot().terrain.revision)
    }

    @Test
    fun fireRainCreatesAVisibleLingeringEffectAfterImpact() {
        val simulation = DeterministicMatchSimulation(
            MatchId("fire-rain-match"),
            SimulationConfig(
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)

        simulation.advance(2_000)

        assertEquals(listOf("FIRE_RAIN"), simulation.snapshot().effects.map { it.effectType })
    }

    @Test
    fun supplyRegeneratesByOneEveryThreeSecondsUpToTwenty() {
        val simulation = DeterministicMatchSimulation(MatchId("supply-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)

        assertEquals(15, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(2_999)
        assertEquals(15, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(1)
        assertEquals(16, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
    }

    @Test
    fun deployingAnotherUnitMovesTheExistingUnitToTheNextGarrisonSlot() {
        val simulation = DeterministicMatchSimulation(MatchId("garrison-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.advance(15_000)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)

        val before = simulation.snapshot()
        assertTrue(before.units[0].moving)
        assertTrue(before.units[1].moving)
        assertTrue(before.units.last().moving)
        assertEquals(230f, before.units.last().x)

        simulation.advance(1_000)
        val after = simulation.snapshot()
        assertTrue(after.units[0].x > before.units[0].x)
        assertTrue(after.units[1].x > before.units[1].x)
        assertTrue(after.units.last().x > before.units.last().x)
    }

    @Test
    fun opposingDefendersStopAndDamageEachOtherAfterTheirWavesMeet() {
        val simulation = DeterministicMatchSimulation(MatchId("unit-combat-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(120) { simulation.advance(100) }

        val units = simulation.snapshot().units
        assertEquals(2, units.size)
        assertTrue(units.all { it.health < 260 })
    }

    @Test
    fun alliedUnitsKeepVisibleSpacingWhenAFasterSapperCatchesADefender() {
        val simulation = DeterministicMatchSimulation(MatchId("allied-spacing-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.sapper(), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        repeat(60) { simulation.advance(100) }

        val blueUnits = simulation.snapshot().units.filter { it.team == Team.BLUE }
        assertEquals(2, blueUnits.size)
        assertTrue(
            kotlin.math.abs(blueUnits[0].x - blueUnits[1].x) >= 12f,
            "blue unit positions: ${blueUnits.map { it.x }}",
        )
    }

    @Test
    fun aSapperDamagesTheEnemyFortressAndReturnsWithAnEmptyBombSlot() {
        val simulation = DeterministicMatchSimulation(MatchId("sapper-attack-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.sapper(), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        repeat(90) { simulation.advance(100) }

        val redFortress = simulation.snapshot().fortresses.first { it.team == Team.RED }
        assertTrue(redFortress.health < redFortress.maxHealth)
        assertTrue(simulation.snapshot().units.single().moving)
    }

}
