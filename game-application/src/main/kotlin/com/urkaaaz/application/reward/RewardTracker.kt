package com.urkaaaz.application.reward

import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.campaign.RewardCondition
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.ProjectileTargetKind
import com.urkaaaz.contracts.Team

fun interface MatchEventObserver {
    fun onEvents(events: List<MatchEvent>)
    fun onMatchGenerationStarted(matchId: MatchId) = Unit
    fun rewardResult(): LevelRewardResult? = null
}

data class EarnedReward(
    val id: String,
    val gold: Long,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*")))
        require(gold >= 0L)
    }
}

data class RewardNotification(
    val id: String,
    val achievementId: String,
    val gold: Long,
    val x: Float,
    val y: Float,
)

enum class RewardTrackingIssue {
    INCOMPLETE_TARGET_METADATA,
}

data class LevelRewardResult(
    val level: Int,
    val matchId: String,
    val rewards: List<EarnedReward>,
    val totalGold: Long,
    val issues: Set<RewardTrackingIssue>,
) {
    init {
        require(totalGold >= 0L)
        require(rewards.sumOf { it.gold } == totalGold)
    }
}

class RewardTracker(
    private val level: Int,
    private val registry: CampaignRewardRegistry = CampaignRewardRegistry.default(),
) : MatchEventObserver {
    private var trackedMatchId: MatchId? = null
    private val processedEvents = mutableSetOf<EventId>()
    private val projectileToShot = mutableMapOf<EntityId, EntityId>()
    private val shots = mutableMapOf<EntityId, ShotState>()
    private val hitUnitPairs = mutableSetOf<Pair<EntityId, EntityId>>()
    private val issues = mutableSetOf<RewardTrackingIssue>()
    private val pendingNotifications = mutableListOf<RewardNotification>()
    private var blueHitEnemyUnit = false
    private var blueFortressDamaged = false
    private var unknownFortressDamage = false
    private var completed: LevelRewardResult? = null

    override fun onEvents(events: List<MatchEvent>) {
        var matchFinished: MatchEvent.MatchFinished? = null
        events.forEach { event ->
            if (event is MatchEvent.MatchFinished) {
                if (trackedMatchId == null) trackedMatchId = event.matchId
                if (
                    event.matchId != trackedMatchId ||
                    !processedEvents.add(event.eventId) ||
                    completed != null
                ) {
                    return@forEach
                }
                if (matchFinished == null) matchFinished = event
            } else {
                onEvent(event)
            }
        }
        matchFinished?.let(::onMatchFinished)
    }

    override fun onMatchGenerationStarted(matchId: MatchId) {
        reset()
        trackedMatchId = matchId
    }

    override fun rewardResult(): LevelRewardResult? = completed

    fun consumeNotifications(): List<RewardNotification> =
        pendingNotifications.toList().also { pendingNotifications.clear() }

    private fun onEvent(event: MatchEvent) {
        if (event is MatchEvent.MatchStarted) {
            if (trackedMatchId == null) trackedMatchId = event.matchId
            if (event.matchId != trackedMatchId || !processedEvents.add(event.eventId)) return
            return
        }
        if (trackedMatchId == null) trackedMatchId = event.matchId
        if (event.matchId != trackedMatchId) return
        if (!processedEvents.add(event.eventId) || completed != null) return

        when (event) {
            is MatchEvent.ProjectileFired -> onProjectileFired(event)
            is MatchEvent.ProjectileSplit -> onProjectileSplit(event)
            is MatchEvent.ProjectileHitEntity -> onProjectileHit(event)
            is MatchEvent.DamageApplied -> onDamageApplied(event)
            is MatchEvent.ProjectileResolved -> onProjectileResolved(event)
            else -> Unit
        }
    }

    private fun onProjectileFired(event: MatchEvent.ProjectileFired) {
        when (event.shooterTeam) {
            Team.BLUE -> {
                if (event.projectileId in projectileToShot) return
                projectileToShot[event.projectileId] = event.projectileId
                shots[event.projectileId] = ShotState(
                    activeProjectiles = mutableSetOf(event.projectileId),
                )
            }
            Team.RED -> Unit
            null -> issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
        }
    }

    private fun onProjectileSplit(event: MatchEvent.ProjectileSplit) {
        val shotId = projectileToShot[event.projectileId] ?: return
        val shot = shots[shotId] ?: return
        event.fragmentProjectileIds.forEach { fragmentId ->
            projectileToShot[fragmentId] = shotId
            shot.activeProjectiles += fragmentId
        }
    }

    private fun onProjectileHit(event: MatchEvent.ProjectileHitEntity) {
        val shotId = projectileToShot[event.projectileId] ?: return
        when (event.targetKind) {
            ProjectileTargetKind.UNIT -> when (event.targetTeam) {
                Team.RED -> {
                    if (hitUnitPairs.add(event.projectileId to event.targetId)) {
                        if (!blueHitEnemyUnit) {
                            blueHitEnemyUnit = true
                            registry.forLevel(level)?.achievements
                                ?.filter { it.condition == RewardCondition.DirectEnemyUnitHit }
                                ?.forEach { achievement ->
                                    pendingNotifications += RewardNotification(
                                        id = "${event.eventId.value}:${achievement.id}",
                                        achievementId = achievement.id,
                                        gold = achievement.gold,
                                        x = event.x,
                                        y = event.y,
                                    )
                                }
                        }
                    }
                }
                Team.BLUE -> Unit
                null -> issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
            }
            ProjectileTargetKind.FORTRESS -> {
                when (event.targetTeam) {
                    Team.RED -> shots[shotId]?.apply {
                        directEnemyFortressHit = true
                        fortressHitX = event.x
                        fortressHitY = event.y
                    }
                    Team.BLUE -> Unit
                    null -> issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
                }
            }
            ProjectileTargetKind.FORTRESS_SHIELD,
            ProjectileTargetKind.BASTION,
            -> Unit
            null -> issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
        }
    }

    private fun onDamageApplied(event: MatchEvent.DamageApplied) {
        when (event.targetKind) {
            ProjectileTargetKind.FORTRESS -> {
                if (event.amount > 0) {
                    when (event.targetTeam) {
                        Team.BLUE -> blueFortressDamaged = true
                        Team.RED -> Unit
                        null -> {
                            unknownFortressDamage = true
                            issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
                        }
                    }
                }
            }
            null -> {
                if (event.amount > 0) unknownFortressDamage = true
                issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
            }
            else -> if (event.targetTeam == null) {
                issues += RewardTrackingIssue.INCOMPLETE_TARGET_METADATA
            }
        }
    }

    private fun onProjectileResolved(event: MatchEvent.ProjectileResolved) {
        val shotId = projectileToShot[event.projectileId] ?: return
        val shot = shots[shotId] ?: return
        shot.activeProjectiles.remove(event.projectileId)
        if (shot.activeProjectiles.isEmpty()) {
            resolveShot(shotId, shot)
        }
    }

    private fun resolveShot(shotId: EntityId, shot: ShotState) {
        shots.remove(shotId)
        projectileToShot.entries.removeAll { it.value == shotId }
        val resolvedStreak = if (shot.directEnemyFortressHit) {
            consecutiveFortressHits + 1
        } else {
            0
        }
        consecutiveFortressHits = resolvedStreak
        if (resolvedStreak >= 2) {
            rewardedStreakLengths += resolvedStreak
            registry.forLevel(level)?.achievements
                ?.filter { achievement ->
                    val condition = achievement.condition as? RewardCondition.FortressHitStreak
                    condition != null && resolvedStreak >= condition.minimumHits
                }
                ?.forEach { achievement ->
                    pendingNotifications += RewardNotification(
                        id = "${shotId.value}:$resolvedStreak:${achievement.id}",
                        achievementId = achievement.id,
                        gold = achievement.gold * (resolvedStreak - 1).toLong(),
                        x = shot.fortressHitX,
                        y = shot.fortressHitY,
                    )
                }
        }
    }

    private var consecutiveFortressHits = 0
    private val rewardedStreakLengths = mutableListOf<Int>()

    private fun onMatchFinished(event: MatchEvent.MatchFinished) {
        if (event.outcome != MatchOutcome.BLUE_WIN) {
            completed = LevelRewardResult(level, event.matchId.value, emptyList(), 0L, issues.toSet())
            return
        }
        val definition = registry.forLevel(level)
            ?: error("reward definition missing for level $level")
        val earned = mutableListOf(EarnedReward("level_win", definition.winGold))
        definition.achievements.forEach { achievement ->
            val condition = achievement.condition
            val achieved = when (condition) {
                RewardCondition.DirectEnemyUnitHit -> blueHitEnemyUnit
                is RewardCondition.FastWin ->
                    event.simulationTimeMilliseconds <= condition.maximumTimeMilliseconds
                RewardCondition.UntouchedFortress ->
                    !blueFortressDamaged && !unknownFortressDamage
                is RewardCondition.FortressHitStreak ->
                    rewardedStreakLengths.any { it >= condition.minimumHits }
            }
            if (achieved && condition is RewardCondition.FortressHitStreak) {
                val streakGold = rewardedStreakLengths
                    .filter { it >= condition.minimumHits }
                    .sumOf { hitCount ->
                        achievement.gold * (hitCount - 1).toLong()
                    }
                if (streakGold > 0) earned += EarnedReward(achievement.id, streakGold)
            } else if (achieved) {
                earned += EarnedReward(achievement.id, achievement.gold)
            }
        }
        completed = LevelRewardResult(
            level = level,
            matchId = event.matchId.value,
            rewards = earned.toList(),
            totalGold = earned.sumOf { it.gold },
            issues = issues.toSet(),
        )
    }

    private fun reset() {
        trackedMatchId = null
        processedEvents.clear()
        projectileToShot.clear()
        shots.clear()
        hitUnitPairs.clear()
        issues.clear()
        pendingNotifications.clear()
        rewardedStreakLengths.clear()
        consecutiveFortressHits = 0
        blueHitEnemyUnit = false
        blueFortressDamaged = false
        unknownFortressDamage = false
        completed = null
    }

    private data class ShotState(
        val activeProjectiles: MutableSet<EntityId>,
        var directEnemyFortressHit: Boolean = false,
        var fortressHitX: Float = 0f,
        var fortressHitY: Float = 0f,
    )
}
