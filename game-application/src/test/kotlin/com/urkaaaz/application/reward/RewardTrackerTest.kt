package com.urkaaaz.application.reward

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.ProjectileResolution
import com.urkaaaz.contracts.ProjectileTargetKind
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RewardTrackerTest {
    @Test
    fun bonusNotificationsCarryAmountsAndHitPositions() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("unit-hit"),
                hit(
                    "unit-hit",
                    "red-unit",
                    Team.RED,
                    ProjectileTargetKind.UNIT,
                    x = 420f,
                    y = 880f,
                ),
                resolved("unit-hit"),
            ),
        )
        val unitBonus = tracker.consumeNotifications().single()
        assertEquals(10L, unitBonus.gold)
        assertEquals(420f, unitBonus.x)
        assertEquals(880f, unitBonus.y)

        tracker.onEvents(
            listOf(
                fire("fortress-one"),
                hit(
                    "fortress-one",
                    "red-fortress",
                    Team.RED,
                    ProjectileTargetKind.FORTRESS,
                    x = 6900f,
                    y = 3600f,
                ),
                resolved("fortress-one"),
            ),
        )
        assertEquals(emptyList(), tracker.consumeNotifications())

        tracker.onEvents(
            listOf(
                fire("fortress-two"),
                hit(
                    "fortress-two",
                    "red-fortress",
                    Team.RED,
                    ProjectileTargetKind.FORTRESS,
                    x = 6900f,
                    y = 3600f,
                ),
                resolved("fortress-two"),
                fire("fortress-three"),
                hit(
                    "fortress-three",
                    "red-fortress",
                    Team.RED,
                    ProjectileTargetKind.FORTRESS,
                    x = 6900f,
                    y = 3600f,
                ),
                resolved("fortress-three"),
            ),
        )
        assertEquals(listOf(10L, 20L), tracker.consumeNotifications().map { it.gold })
    }

    @Test
    fun consecutiveDirectFortressHitsGrantIncreasingOneTimeBonuses() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("p1"),
                hit("p1", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p1"),
                fire("p2"),
                hit("p2", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p2"),
                fire("p3"),
                hit("p3", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p3"),
                finished(MatchOutcome.BLUE_WIN, time = 100_000),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(180L, result.totalGold)
        assertEquals(
            setOf(
                "level_win",
                "fast_win",
                "untouched_fortress",
                "fortress_hit_streak",
            ),
            result.rewards.map { it.id }.toSet(),
        )
    }

    @Test
    fun winningFortressHitIsResolvedBeforeRewardsAreSettled() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("first"),
                hit("first", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("first"),
                fire("winning"),
                hit("winning", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
                resolved("winning"),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(135L, result.totalGold)
        assertEquals(
            setOf("level_win", "untouched_fortress", "fortress_hit_streak"),
            result.rewards.map { it.id }.toSet(),
        )
    }

    @Test
    fun aMissAndBastionHitResetFortressStreakAndUnitHitIsDirectOnly() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("p1"),
                hit("p1", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p1"),
                fire("p2"),
                hit("p2", "red-bastion", Team.RED, ProjectileTargetKind.BASTION),
                resolved("p2"),
                fire("p3"),
                hit("p3", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p3"),
                fire("p4"),
                hit("p4", "red-unit", Team.RED, ProjectileTargetKind.UNIT),
                resolved("p4"),
                fire("p5"),
                hit("p5", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("p5"),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(135L, result.totalGold)
        assertEquals(
            setOf(
                "level_win",
                "direct_enemy_unit_hit",
                "untouched_fortress",
            ),
            result.rewards.map { it.id }.toSet(),
        )
    }

    @Test
    fun shieldDoesNotCountAsFortressHitAndBlueDamageBlocksUntouchedAchievement() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("p1"),
                hit("p1", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS_SHIELD),
                resolved("p1"),
                MatchEvent.DamageApplied(
                    eventId = EventId("damage"),
                    matchId = MatchId("match"),
                    simulationTimeMilliseconds = 1,
                    targetId = EntityId("blue-fortress"),
                    amount = 1,
                    targetTeam = Team.BLUE,
                    targetKind = ProjectileTargetKind.FORTRESS,
                ),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(100L, result.totalGold)
        assertEquals(listOf("level_win"), result.rewards.map { it.id })
    }

    @Test
    fun knownNonFortressDamageDoesNotBlockUntouchedFortressAchievement() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                MatchEvent.DamageApplied(
                    eventId = EventId("unit-damage"),
                    matchId = MatchId("match"),
                    simulationTimeMilliseconds = 1,
                    targetId = EntityId("unit"),
                    amount = 5,
                    targetKind = ProjectileTargetKind.UNIT,
                ),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(125L, result.totalGold)
        assertEquals(
            setOf("level_win", "untouched_fortress"),
            result.rewards.map { it.id }.toSet(),
        )
    }

    @Test
    fun fragmentsBelongToTheirParentShotUntilEveryFragmentResolves() {
        val tracker = RewardTracker(level = 1)
        tracker.onEvents(
            listOf(
                fire("parent"),
                MatchEvent.ProjectileSplit(
                    EventId("split"),
                    MatchId("match"),
                    1,
                    EntityId("parent"),
                    listOf(EntityId("fragment-1"), EntityId("fragment-2")),
                    "SPLIT",
                    0f,
                    0f,
                ),
                hit("parent", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("parent"),
                resolved("fragment-1"),
            ),
        )
        assertNull(tracker.rewardResult())
        tracker.onEvents(
            listOf(
                resolved("fragment-2"),
                fire("second"),
                hit("second", "red-fortress", Team.RED, ProjectileTargetKind.FORTRESS),
                resolved("second"),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
            ),
        )
        val result = assertNotNull(tracker.rewardResult())
        assertEquals(135L, result.totalGold)
        assertEquals(
            setOf("level_win", "untouched_fortress", "fortress_hit_streak"),
            result.rewards.map { it.id }.toSet(),
        )
    }

    @Test
    fun nonVictoryNeverPaysAndDuplicateEventIdsAreIgnored() {
        val tracker = RewardTracker(level = 1)
        val directHit = hit("p1", "red-unit", Team.RED, ProjectileTargetKind.UNIT)
        tracker.onEvents(listOf(fire("p1"), directHit, directHit, resolved("p1")))
        tracker.onEvents(listOf(finished(MatchOutcome.RED_WIN, time = 1)))

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(0L, result.totalGold)
        assertEquals(emptyList(), result.rewards)
    }

    @Test
    fun newGenerationResetsStateAndForeignMatchEventsAreIgnored() {
        val tracker = RewardTracker(level = 1)
        tracker.onMatchGenerationStarted(MatchId("match"))
        tracker.onEvents(
            listOf(
                MatchEvent.MatchStarted(
                    EventId("event-1"),
                    MatchId("match"),
                    0,
                    1,
                ),
                fire("p1"),
                hit("p1", "red-unit", Team.RED, ProjectileTargetKind.UNIT),
            ),
        )

        tracker.onMatchGenerationStarted(MatchId("match"))
        tracker.onEvents(
            listOf(
                MatchEvent.MatchStarted(
                    EventId("event-1"),
                    MatchId("match"),
                    0,
                    1,
                ),
                fire("p2"),
                hit("p2", "red-unit", Team.RED, ProjectileTargetKind.UNIT)
                    .copy(matchId = MatchId("another-match")),
                resolved("p2"),
                finished(MatchOutcome.BLUE_WIN, time = 200_000),
            ),
        )

        val result = assertNotNull(tracker.rewardResult())
        assertEquals(125L, result.totalGold)
        assertEquals(
            setOf("level_win", "untouched_fortress"),
            result.rewards.map { it.id }.toSet(),
        )
    }

    private fun fire(projectile: String) = MatchEvent.ProjectileFired(
        eventId = EventId("fire-$projectile"),
        matchId = MatchId("match"),
        simulationTimeMilliseconds = 1,
        projectileId = EntityId(projectile),
        catapultId = EntityId("blue-catapult"),
        shooterTeam = Team.BLUE,
    )

    private fun hit(
        projectile: String,
        target: String,
        team: Team,
        kind: ProjectileTargetKind,
        x: Float = 0f,
        y: Float = 0f,
    ) = MatchEvent.ProjectileHitEntity(
        eventId = EventId("hit-$projectile-$target"),
        matchId = MatchId("match"),
        simulationTimeMilliseconds = 1,
        projectileId = EntityId(projectile),
        targetId = EntityId(target),
        x = x,
        y = y,
        targetTeam = team,
        targetKind = kind,
    )

    private fun resolved(projectile: String) = MatchEvent.ProjectileResolved(
        eventId = EventId("resolved-$projectile"),
        matchId = MatchId("match"),
        simulationTimeMilliseconds = 2,
        projectileId = EntityId(projectile),
        reason = ProjectileResolution.TERRAIN_IMPACT,
    )

    private fun finished(outcome: MatchOutcome, time: Long) = MatchEvent.MatchFinished(
        eventId = EventId("finished-$outcome"),
        matchId = MatchId("match"),
        simulationTimeMilliseconds = time,
        outcome = outcome,
    )
}
