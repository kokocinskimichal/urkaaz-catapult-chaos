package com.urkaaaz.domain

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainModelsTest {
    @Test
    fun healthCannotExceedItsMaximum() {
        val health = Health(maximum = 100, current = 20)

        assertEquals(100, health.heal(500).current)
        assertEquals(0, health.damage(500).current)
    }

    @Test
    fun catapultAimIsValidatedAndMuzzleIsAboveBase() {
        val catapult = Catapult(
            id = EntityId("catapult-1"),
            team = Team.BLUE,
            position = WorldPosition(100f, 300f),
        )

        assertEquals(266f, catapult.muzzlePosition().y)
        assertFailsWith<IllegalArgumentException> {
            Aim(directionDegrees = 181f, power = 50f)
        }
    }

    @Test
    fun limitedAmmunitionIsConsumedButUnlimitedAmmunitionIsNot() {
        val limited = ResourceWallet(
            ammunition = mapOf(AmmunitionType.POWDER_BARREL to 2),
        )
        val afterUse = limited.consume(AmmunitionType.POWDER_BARREL)

        assertEquals(1, afterUse.ammunitionCount(AmmunitionType.POWDER_BARREL))
        assertTrue(afterUse.canUse(AmmunitionType.ROCK))
        assertEquals(
            Int.MAX_VALUE,
            afterUse.ammunitionCount(AmmunitionType.ROCK),
        )
    }

    @Test
    fun projectileAdvancesUsingVelocityAndWind() {
        val projectile = Projectile(
            id = EntityId("projectile-1"),
            ammunition = AmmunitionType.ROCK,
            firedBy = Team.BLUE,
            position = WorldPosition(0f, 0f),
            velocity = Velocity(10f, -4f),
        )

        val advanced = projectile.advance(deltaSeconds = 0.5f, windAccelerationX = 2f)

        assertEquals(5.5f, advanced.position.x)
        assertEquals(-2f, advanced.position.y)
        assertEquals(11f, advanced.velocity.x)
    }

    @Test
    fun fortressUsesIrregularRectangleHitboxInsteadOfCenterCircle() {
        val fortress = Fortress(
            id = EntityId("fortress-1"),
            team = Team.RED,
            center = WorldPosition(500f, 400f),
        )

        assertTrue(fortress.contains(WorldPosition(500f, 320f)))
        assertTrue(fortress.contains(WorldPosition(410f, 450f)))
        assertFalse(fortress.contains(WorldPosition(500f, 300f)))
        assertFalse(fortress.contains(WorldPosition(350f, 400f)))
    }

    @Test
    fun matchStateHasExplicitLifecycle() {
        val state = MatchState(matchId = MatchId("match-1"))
        val running = state.start(Team.BLUE)
        val finished = running.finish(MatchOutcome.BLUE_WIN)

        assertEquals(MatchPhase.PLAYER_TURN, running.phase)
        assertFalse(running.isFinished)
        assertEquals(MatchPhase.FINISHED, finished.phase)
        assertTrue(finished.isFinished)
    }
}
