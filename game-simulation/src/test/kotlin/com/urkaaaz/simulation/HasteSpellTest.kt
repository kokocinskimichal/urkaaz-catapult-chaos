package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.StatusState
import com.urkaaaz.domain.UnitConfigurations
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.UnitStatus
import com.urkaaaz.domain.WorldBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HasteSpellTest {
    private val config = SimulationConfig(
        bounds = WorldBounds(width = 10_000f, height = 600f),
        gravityAccelerationY = 0f,
    )

    private fun simulation(id: String, startingMana: Int = 100) =
        DeterministicMatchSimulation(
            MatchId(id),
            config.copy(startingMana = startingMana),
        ).apply { start() }

    private fun unitType() =
        UnitFactory.create(UnitConfigurations.DEFENDER.copy(speed = 40f, supplyCost = 0))

    @Test
    fun hasteAppliesToDeployedAlliesIncludingUnitsSentAfterCasting() {
        val simulation = simulation("haste-targets")
        assertNull(simulation.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null))

        val blueUnit = simulation.deployUnit(unitType(), Team.BLUE).unitId
        val redUnit = simulation.deployUnit(unitType(), Team.RED).unitId
        assertFalse(
            simulation.snapshot().units.single { it.entityId == blueUnit }
                .statuses.containsKey(UnitStatus.HASTED.name),
        )

        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val snapshot = simulation.snapshot()
        assertTrue(snapshot.units.single { it.entityId == blueUnit }.statuses.containsKey("HASTED"))
        assertFalse(snapshot.units.single { it.entityId == redUnit }.statuses.containsKey("HASTED"))
        assertEquals(Team.BLUE, snapshot.activeSpells.single().team)
        assertEquals(15_000L, snapshot.activeSpells.single().remainingMilliseconds)
    }

    @Test
    fun hasteDoublesDeployedMovementForFifteenSecondsThenExpires() {
        val normal = simulation("haste-normal")
        val hasted = simulation("haste-boosted")
        val normalUnit = normal.deployUnit(unitType(), Team.BLUE).unitId
        val hastedUnit = hasted.deployUnit(unitType(), Team.BLUE).unitId
        normal.sendWave(Team.BLUE)
        hasted.sendWave(Team.BLUE)
        assertNull(hasted.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null))

        val normalStart = normal.snapshot().units.single { it.entityId == normalUnit }.x
        val hastedStart = hasted.snapshot().units.single { it.entityId == hastedUnit }.x
        normal.advance(15_000L)
        hasted.advance(15_000L)

        val normalDistance = normal.snapshot().units.single { it.entityId == normalUnit }.x - normalStart
        val hastedDistance = hasted.snapshot().units.single { it.entityId == hastedUnit }.x - hastedStart
        assertEquals(normalDistance * 2f, hastedDistance, 0.01f)
        assertTrue(hasted.snapshot().activeSpells.isEmpty())
        assertFalse(
            hasted.snapshot().units.single { it.entityId == hastedUnit }
                .statuses.containsKey(UnitStatus.HASTED.name),
        )

        val normalAfterExpiry = normal.snapshot().units.single { it.entityId == normalUnit }.x
        val hastedAfterExpiry = hasted.snapshot().units.single { it.entityId == hastedUnit }.x
        normal.advance(1_000L)
        hasted.advance(1_000L)
        assertEquals(
            normal.snapshot().units.single { it.entityId == normalUnit }.x - normalAfterExpiry,
            hasted.snapshot().units.single { it.entityId == hastedUnit }.x - hastedAfterExpiry,
            0.01f,
        )
    }

    @Test
    fun recastingRefreshesTheTimerWithoutStackingMovementSpeed() {
        val singleCast = simulation("haste-single-cast")
        val refreshed = simulation("haste-refreshed")
        val singleUnit = singleCast.deployUnit(unitType(), Team.BLUE).unitId
        val refreshedUnit = refreshed.deployUnit(unitType(), Team.BLUE).unitId
        singleCast.sendWave(Team.BLUE)
        refreshed.sendWave(Team.BLUE)
        assertNull(singleCast.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null))
        assertNull(refreshed.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null))

        singleCast.advance(10_000L)
        refreshed.advance(10_000L)
        assertEquals(5_000L, singleCast.snapshot().activeSpells.single().remainingMilliseconds)
        assertNull(refreshed.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null))
        assertEquals(15_000L, refreshed.snapshot().activeSpells.single().remainingMilliseconds)

        singleCast.advance(100L)
        refreshed.advance(100L)
        assertEquals(
            singleCast.snapshot().units.single { it.entityId == singleUnit }.x,
            refreshed.snapshot().units.single { it.entityId == refreshedUnit }.x,
            0.01f,
        )
    }

    @Test
    fun invalidTargetAndInsufficientManaDoNotConsumeResourcesOrActivateHaste() {
        val invalidTarget = simulation("haste-invalid-target")
        assertEquals(
            MatchError.INVALID_SPELL_TARGET,
            invalidTarget.castHaste(
                PlayerId("blue-player"),
                Team.BLUE,
                targetId = EntityId("blue-defender"),
            ),
        )
        assertEquals(100, invalidTarget.snapshot().resourcesByTeam.getValue(Team.BLUE).mana)
        assertTrue(invalidTarget.snapshot().activeSpells.isEmpty())

        val lowMana = simulation("haste-low-mana", startingMana = 49)
        assertEquals(
            MatchError.INSUFFICIENT_RESOURCE,
            lowMana.castHaste(PlayerId("blue-player"), Team.BLUE, targetId = null),
        )
        assertEquals(49, lowMana.snapshot().resourcesByTeam.getValue(Team.BLUE).mana)
        assertTrue(lowMana.snapshot().activeSpells.isEmpty())
    }

    @Test
    fun hasteAndSlowMultipliersMultiply() {
        val multiplier = UnitMovementSpeed.statusMultiplier(
            statuses = mapOf(UnitStatus.SLOWED to StatusState(remainingSeconds = 2f, strength = 0.5f)),
            hasteMultiplier = 2f,
        )

        assertEquals(1f, multiplier)
    }
}
