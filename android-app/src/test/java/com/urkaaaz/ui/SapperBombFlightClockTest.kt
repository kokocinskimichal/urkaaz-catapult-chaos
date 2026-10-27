package com.urkaaaz.ui

import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.WorldScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SapperBombFlightClockTest {
    @Test
    fun launchesAtBombReleaseAndFollowsAnArcToFortress() {
        val clock = SapperBombFlightClock()
        val sapper = sapper()
        val beforeRelease = renderState(units = listOf(sapper))
        clock.update(beforeRelease, nowMilliseconds = 1_000L)

        val release = renderState(
            units = listOf(sapper.copy(sapperHasBomb = false)),
        )
        val launch = clock.update(release, nowMilliseconds = 1_100L).single()

        assertEquals(sapper.x + 12f, launch.x, 0f)
        assertEquals(sapper.y - 70f, launch.y, 0f)
        assertEquals(0, launch.frameIndex)

        val halfway = clock.update(release, nowMilliseconds = 1_350L).single()
        val targetX = release.redFortressX + FortressHitboxProfile.rectangles.first().left
        val targetY = release.redFortressFoundationY - WorldScale.FORTRESS_HEIGHT / 2f
        assertEquals((sapper.x + 12f + targetX) / 2f, halfway.x, 0.01f)
        assertEquals((sapper.y - 70f + targetY) / 2f - 72f, halfway.y, 0.01f)
        assertEquals(16, halfway.frameIndex)
    }

    @Test
    fun pausesFlightAndUsesThePreviousBastionPositionWhenItIsDestroyed() {
        val clock = SapperBombFlightClock()
        val sapper = sapper(targetId = "red-bastion")
        val bastion = RunicBastionRenderState(
            entityId = "red-bastion",
            teamLabel = "RED",
            centerX = 6_300f,
            centerY = 3_300f,
            foundationY = 3_408f,
            health = 1,
            maxHealth = 360,
        )
        clock.update(
            renderState(units = listOf(sapper), runicBastions = listOf(bastion)),
            nowMilliseconds = 1_000L,
        )
        val releasedState = renderState(
            units = listOf(sapper.copy(sapperHasBomb = false)),
        )
        clock.update(releasedState, nowMilliseconds = 1_100L)

        val beforePause = clock.update(releasedState, nowMilliseconds = 1_300L).single()
        val pausedState = releasedState.copy(isPaused = true)
        val pauseStart = clock.update(pausedState, nowMilliseconds = 1_300L).single()
        assertEquals(beforePause, pauseStart)
        val whilePaused = clock.update(pausedState, nowMilliseconds = 2_000L).single()
        assertEquals(beforePause, whilePaused)

        val resumed = clock.update(releasedState, nowMilliseconds = 3_000L).single()
        assertEquals(beforePause, resumed)

        val halfway = clock.update(releasedState, nowMilliseconds = 3_050L).single()
        assertEquals((sapper.x + 12f + bastion.centerX) / 2f, halfway.x, 0.01f)
        assertEquals((sapper.y - 70f + bastion.centerY) / 2f - 72f, halfway.y, 0.01f)
        assertTrue(halfway.frameIndex > beforePause.frameIndex)
    }

    private fun renderState(
        units: List<UnitRenderState>,
        runicBastions: List<RunicBastionRenderState> = emptyList(),
        isPaused: Boolean = false,
    ) = RenderState(
        statusLabel = "RUNNING",
        phaseLabel = "ACTIVE",
        activeTeamLabel = "BLUE",
        projectileCount = 0,
        terrainRevision = 0L,
        terrainBiome = "GREEN_FRONTIER",
        projectiles = emptyList(),
        blueCatapultX = 450f,
        blueCatapultY = 3_600f,
        redCatapultX = 6_750f,
        redCatapultY = 3_600f,
        blueFortressX = 450f,
        blueFortressFoundationY = 3_600f,
        redFortressX = 6_750f,
        redFortressFoundationY = 3_600f,
        playerAngleDegrees = 45f,
        playerPower = 50f,
        blueFortressHealth = 500,
        blueFortressMaxHealth = 500,
        redFortressHealth = 500,
        redFortressMaxHealth = 500,
        windStrength = 0f,
        impact = null,
        outcomeLabel = null,
        playerReloadRemainingSeconds = 0f,
        enemyReloadRemainingSeconds = 0f,
        playerProjectileActive = false,
        playerAmmunition = emptyMap(),
        playerGold = 0,
        playerSupply = 0,
        units = units,
        runicBastions = runicBastions,
        matchTimeRemainingMilliseconds = 300_000L,
        windDirection = 0f,
        combatFeedback = emptyList(),
        matchId = "test-match",
        isPaused = isPaused,
    )

    private fun sapper(targetId: String = "red-fortress") = UnitRenderState(
        entityId = "blue-sapper",
        teamLabel = "BLUE",
        unitType = "SAPPER",
        x = 6_000f,
        y = 3_600f,
        health = 20,
        maxHealth = 20,
        moving = false,
        attackType = "SIEGE_MISSION",
        actionState = "ATTACKING",
        targetId = targetId,
        attackGroupId = null,
        attackCycleId = 1L,
        attackProgress = 1f,
        facingDirection = 1f,
        sapperHasBomb = true,
    )
}
