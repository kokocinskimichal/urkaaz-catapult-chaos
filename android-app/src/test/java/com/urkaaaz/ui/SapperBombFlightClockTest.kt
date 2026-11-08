package com.urkaaaz.ui

import com.urkaaaz.contracts.CatapultPresentationContract
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.SapperBombFlightTiming
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SapperBombFlightClockTest {
    @Test
    fun launchesAtVisibleReleaseFrameAndImpactsAfterTheFlight() {
        val clock = SapperBombFlightClock()
        val sapper = sapper().copy(attackProgress = 0f)
        val beforeRelease = renderState(units = listOf(sapper))
        clock.update(beforeRelease, nowMilliseconds = 1_000L, impactFrameCount = 32)

        val midAttack = renderState(
            units = listOf(sapper.copy(attackProgress = 0.5f, actionState = "ATTACKING")),
        )
        assertTrue(
            clock.update(midAttack, nowMilliseconds = 1_050L, impactFrameCount = 32)
                .flights.isEmpty(),
        )

        val release = renderState(
            units = listOf(
                sapper.copy(
                    sapperHasBomb = false,
                    attackProgress = SapperBombFlightTiming.BOMB_RELEASE_PROGRESS,
                    actionState = "ATTACKING",
                ),
            ),
        )
        val launch = clock.update(
            release,
            nowMilliseconds = 1_100L,
            impactFrameCount = 32,
        ).flights.single()

        assertEquals(sapper.x + 12f, launch.x, 0f)
        assertEquals(sapper.y - 70f, launch.y, 0f)
        assertEquals(0, launch.frameIndex)

        val halfway = clock.update(
            release,
            nowMilliseconds = 1_350L,
            impactFrameCount = 32,
        ).flights.single()
        val targetX = release.redFortressX + FortressHitboxProfile.rectangles.first().left
        val targetY = release.redFortressFoundationY - WorldScale.FORTRESS_HEIGHT / 2f
        assertEquals((sapper.x + 12f + targetX) / 2f, halfway.x, 0.01f)
        assertEquals((sapper.y - 70f + targetY) / 2f - 24f, halfway.y, 0.01f)
        assertEquals(16, halfway.frameIndex)

        val impact = clock.update(
            release,
            nowMilliseconds = 1_600L,
            impactFrameCount = 32,
        )
        assertTrue(impact.flights.isEmpty())
        assertEquals(1, impact.impacts.size)
        assertEquals(targetX, impact.impacts.single().x, 0f)
        assertEquals(targetY, impact.impacts.single().y, 0f)
        assertEquals(0, impact.impacts.single().frameIndex)

        val nextImpactFrame = clock.update(
            release,
            nowMilliseconds = 1_633L,
            impactFrameCount = 32,
        ).impacts.single()
        assertEquals(1, nextImpactFrame.frameIndex)
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
            impactFrameCount = 32,
        )
        val releasedState = renderState(
            units = listOf(sapper.copy(sapperHasBomb = false)),
        )
        clock.update(releasedState, nowMilliseconds = 1_100L, impactFrameCount = 32)

        val beforePause = clock.update(
            releasedState,
            nowMilliseconds = 1_300L,
            impactFrameCount = 32,
        ).flights.single()
        val pausedState = releasedState.copy(isPaused = true)
        val pauseStart = clock.update(
            pausedState,
            nowMilliseconds = 1_300L,
            impactFrameCount = 32,
        ).flights.single()
        assertEquals(beforePause, pauseStart)
        val whilePaused = clock.update(
            pausedState,
            nowMilliseconds = 2_000L,
            impactFrameCount = 32,
        ).flights.single()
        assertEquals(beforePause, whilePaused)

        val resumed = clock.update(
            releasedState,
            nowMilliseconds = 3_000L,
            impactFrameCount = 32,
        ).flights.single()
        assertEquals(beforePause, resumed)

        val halfway = clock.update(
            releasedState,
            nowMilliseconds = 3_050L,
            impactFrameCount = 32,
        ).flights.single()
        assertEquals((sapper.x + 12f + bastion.centerX) / 2f, halfway.x, 0.01f)
        assertEquals((sapper.y - 70f + bastion.centerY) / 2f - 24f, halfway.y, 0.01f)
        assertTrue(halfway.frameIndex > beforePause.frameIndex)
    }

    @Test
    fun launchesBombTowardBossUsingItsCollisionCenter() {
        val clock = SapperBombFlightClock()
        val sapper = sapper(targetId = "boss-mobile")
        val boss = BossRenderState(
            entityId = "boss-mobile",
            profileId = "boss.mobile-test.level-10",
            presentationKey = "test_boss",
            archetype = BossArchetype.MOBILE_SIEGE_BOSS,
            phase = BossPhase.MARCHING,
            x = 6_500f,
            groundY = 3_300f,
            collisionWidth = 400f,
            collisionHeight = 500f,
            health = 1_000,
            maxHealth = 1_000,
            blueDamage = 0,
            redDamage = 0,
        )
        val beforeRelease = renderState(units = listOf(sapper), boss = boss)
        clock.update(beforeRelease, nowMilliseconds = 1_000L, impactFrameCount = 32)

        val release = renderState(
            units = listOf(sapper.copy(sapperHasBomb = false)),
            boss = boss,
        )
        val launch = clock.update(
            release,
            nowMilliseconds = 1_100L,
            impactFrameCount = 32,
        ).flights.single()
        val targetX = boss.x
        val targetY = boss.groundY - boss.collisionHeight / 2f
        val halfway = clock.update(
            release,
            nowMilliseconds = 1_350L,
            impactFrameCount = 32,
        ).flights.single()

        assertEquals((sapper.x + 12f + targetX) / 2f, halfway.x, 0.01f)
        assertEquals((sapper.y - 70f + targetY) / 2f - 24f, halfway.y, 0.01f)
        assertEquals(0, launch.frameIndex)
    }

    private fun renderState(
        units: List<UnitRenderState>,
        runicBastions: List<RunicBastionRenderState> = emptyList(),
        isPaused: Boolean = false,
        boss: BossRenderState? = null,
    ) = RenderState(
        statusLabel = "RUNNING",
        phaseLabel = "ACTIVE",
        activeTeamLabel = "BLUE",
        projectileCount = 0,
        terrainRevision = 0L,
        terrainBiome = "GREEN_FRONTIER",
        projectiles = emptyList(),
        blueCatapultX = CatapultPresentationContract.catapultCenterX(
            Team.BLUE,
            WorldDimensions.WIDTH,
        ),
        blueCatapultY = 3_600f,
        blueCatapultAnchorX = CatapultPresentationContract.projectileAnchorX(
            Team.BLUE,
            WorldDimensions.WIDTH,
        ),
        redCatapultX = CatapultPresentationContract.catapultCenterX(
            Team.RED,
            WorldDimensions.WIDTH,
        ),
        redCatapultY = 3_600f,
        redCatapultAnchorX = CatapultPresentationContract.projectileAnchorX(
            Team.RED,
            WorldDimensions.WIDTH,
        ),
        blueFortressX = CatapultPresentationContract.FORTRESS_CENTER_MARGIN,
        blueFortressFoundationY = 3_600f,
        redFortressX = WorldDimensions.WIDTH - CatapultPresentationContract.FORTRESS_CENTER_MARGIN,
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
        boss = boss,
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
