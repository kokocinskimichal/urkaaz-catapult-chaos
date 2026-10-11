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
import kotlin.test.assertFailsWith
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
    fun deployingAnotherRedUnitMovesExistingRedUnitsIntoMirroredGarrisonSlots() {
        val simulation = DeterministicMatchSimulation(MatchId("red-garrison-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.advance(15_000)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)

        val before = simulation.snapshot()
        assertTrue(before.units.filter { it.team == Team.RED }.all { it.moving })
        assertEquals(config.bounds.width - 230f, before.units.last().x)

        simulation.advance(1_000)
        val after = simulation.snapshot()
        val redBefore = before.units.filter { it.team == Team.RED }.map { it.x }
        val redAfter = after.units.filter { it.team == Team.RED }.map { it.x }
        assertTrue(redAfter.zip(redBefore).all { (afterX, beforeX) -> afterX < beforeX })
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
        assertTrue(units.size <= 2)
        assertTrue(units.isEmpty() || units.all { it.health < 260 })
    }

    @Test
    fun combatLuckCanProduceAWinningDefenderInsteadOfAForcedDraw() {
        val simulation = DeterministicMatchSimulation(MatchId("defender-luck-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        var defeatEvents = emptyList<MatchEvent.UnitDefeated>()
        for (step in 0 until 200) {
            defeatEvents = simulation.advance(100)
                .filterIsInstance<MatchEvent.UnitDefeated>()
            if (defeatEvents.isNotEmpty()) break
        }

        assertEquals(1, defeatEvents.size)
        assertEquals(1, simulation.snapshot().units.count { it.health > 0 })
    }

    @Test
    fun multipleUnitsKeepFormationSpacingAndEnterCombatInsteadOfRunningThroughEachOther() {
        val simulation = DeterministicMatchSimulation(MatchId("formation-combat-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(75) { simulation.advance(100) }

        val units = simulation.snapshot().units
        val bluePositions = units.filter { it.team == Team.BLUE }.map { it.x }.sorted()
        val redPositions = units.filter { it.team == Team.RED }.map { it.x }.sorted()
        assertTrue(
            bluePositions.zipWithNext().all { (left, right) -> right - left >= 20f },
            "blue positions: $bluePositions",
        )
        assertTrue(
            redPositions.zipWithNext().all { (left, right) -> right - left >= 20f },
            "red positions: $redPositions",
        )
        assertTrue(units.any { it.actionState == "ATTACKING" || it.actionState == "RECOVERING" })
    }

    @Test
    fun rearUnitsJoinTheFrontUnitsAttackGroupInsteadOfRemainingIdle() {
        val simulation = DeterministicMatchSimulation(MatchId("rear-group-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(55) { simulation.advance(100) }

        val blueUnits = simulation.snapshot().units.filter { it.team == Team.BLUE }
        assertTrue(blueUnits.size >= 2)
        assertTrue(
            blueUnits.all { it.targetId != null && it.attackGroupId == "melee-${blueUnits.first().targetId?.value}" },
            "blue units did not join one attack group: $blueUnits",
        )
        assertTrue(
            blueUnits.none { !it.moving && it.actionState == "GARRISONED" },
            "rear blue units remained idle: $blueUnits",
        )
        assertTrue(
            blueUnits.zipWithNext().all { (left, right) ->
                kotlin.math.abs(right.x - left.x) >= 16f
            },
            "blue units overlap while joining the group: $blueUnits",
        )
    }

    @Test
    fun rearGroupMembersStartMeleeAttacksFromTheirFormationSlots() {
        val simulation = DeterministicMatchSimulation(MatchId("rear-slot-attack-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(80) { simulation.advance(100) }

        val units = simulation.snapshot().units
        val groupedAttackers = units.filter {
            it.attackGroupId != null && it.targetId != null
        }
        assertTrue(groupedAttackers.any { it.attackCycleId > 0 })
        assertTrue(
            groupedAttackers.any { attacker ->
                attacker.attackCycleId > 0 &&
                    kotlin.math.abs(
                        units.first { target -> target.entityId == attacker.targetId }.x - attacker.x,
                    ) > config.unitCollisionRadius
            },
            "no rear group member attacked from a formation slot: $groupedAttackers",
        )
    }

    @Test
    fun combatLoggerCapturesUniqueUnitsStateAndTargetDistance() {
        val records = mutableListOf<CombatLogRecord>()
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("combat-log-match"),
            config = config,
            combatLogSink = CombatLogSink { records += it },
        )
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(60) { simulation.advance(100) }

        assertTrue(records.isNotEmpty())
        val combatRecord = records.last { record ->
            record.units.any { it.targetId != null }
        }
        assertEquals(2, combatRecord.units.size)
        assertEquals(
            combatRecord.units.size,
            combatRecord.units.map { it.id }.toSet().size,
        )
        assertTrue(combatRecord.units.all { it.action.isNotBlank() })
        assertTrue(combatRecord.units.any { it.distanceToTarget != null })
    }

    @Test
    fun attackStartsBeforeItsHitIsResolved() {
        val simulation = DeterministicMatchSimulation(MatchId("attack-timing-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val events = buildList {
            repeat(120) { addAll(simulation.advance(100)) }
        }

        val started = events.filterIsInstance<MatchEvent.AttackStarted>()
        val hits = events.filterIsInstance<MatchEvent.AttackHit>()

        assertTrue(started.isNotEmpty())
        assertTrue(hits.isNotEmpty())
        assertTrue(
            hits.all { hit ->
                started.any { attack ->
                    attack.attackerId == hit.attackerId &&
                        attack.attackCycleId == hit.attackCycleId &&
                        attack.simulationTimeMilliseconds < hit.simulationTimeMilliseconds
                }
            },
        )
    }

    @Test
    fun defeatedUnitsRemainVisibleForTheirDeathAnimationWindow() {
        val simulation = DeterministicMatchSimulation(MatchId("death-window-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        var defeatEvents = emptyList<MatchEvent.UnitDefeated>()
        repeat(120) {
            if (defeatEvents.isEmpty()) {
                defeatEvents = simulation.advance(100)
                    .filterIsInstance<MatchEvent.UnitDefeated>()
            }
        }

        assertTrue(defeatEvents.isNotEmpty())
        assertTrue(
            simulation.snapshot().units.any {
                it.entityId == defeatEvents.first().unitId &&
                    it.actionState == "DEAD" &&
                    it.health == 0
            },
        )
    }

    @Test
    fun onlyOneSapperCanBeActivePerTeam() {
        val simulation = DeterministicMatchSimulation(MatchId("sapper-limit-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.sapper(), Team.BLUE)

        assertFailsWith<IllegalStateException> {
            simulation.deployUnit(com.urkaaaz.domain.UnitFactory.sapper(), Team.BLUE)
        }
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

    @Test
    fun aMeleeUnitDamagesTheEnemyFortress() {
        val simulation = DeterministicMatchSimulation(MatchId("melee-fortress-attack-match"), config)
        simulation.start()
        simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        repeat(120) { simulation.advance(100) }

        val redFortress = simulation.snapshot().fortresses.first { it.team == Team.RED }
        assertTrue(
            redFortress.health < redFortress.maxHealth,
            "fortress=${redFortress.health}/${redFortress.maxHealth}, units=${simulation.snapshot().units}",
        )
    }

    @Test
    fun combatDamageIsSeededByMatchIdAndReplaysExactly() {
        fun trace(): Pair<List<MatchEvent>, com.urkaaaz.contracts.MatchSnapshot> {
            val simulation = DeterministicMatchSimulation(MatchId("seeded-combat"), config)
            val events = buildList {
                addAll(simulation.start())
                add(simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.BLUE))
                add(simulation.deployUnit(com.urkaaaz.domain.UnitFactory.defender(), Team.RED))
                simulation.sendWave(Team.BLUE)
                simulation.sendWave(Team.RED)
                repeat(90) { addAll(simulation.advance(100)) }
            }
            return events to simulation.snapshot()
        }

        assertEquals(trace(), trace())
    }

}
