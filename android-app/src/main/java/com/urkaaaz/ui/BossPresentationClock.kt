package com.urkaaaz.ui

import com.urkaaaz.contracts.BossPhase
import kotlin.math.abs

data class BossPresentationFrame(
    val idleFrameIndex: Int? = null,
    val walkFrameIndex: Int? = null,
    val attackDirection: BossAttackDirection? = null,
    val attackFrameIndex: Int? = null,
    val deathAnimationFrameIndex: Int? = null,
    val deathPresentationActive: Boolean = false,
    val deathPresentationComplete: Boolean = false,
)

class BossPresentationClock {
    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var activeBoss: Pair<String, String>? = null
    private var lastBossX: Float? = null
    private var recentMovementGraceMilliseconds = 0L
    private var idleElapsedMilliseconds = 0L
    private var walkElapsedMilliseconds = 0L
    private var attackEventId: String? = null
    private var attackDirection: BossAttackDirection? = null
    private var attackElapsedMilliseconds = 0L
    private var deathBossId: String? = null
    private var deathElapsedMilliseconds = 0L
    private var deathDurationMilliseconds = 0L
    private var deathFallbackIdleFrameIndex: Int? = null
    private var deathStarted = false
    private var wasPaused = false

    fun isDeathPresentationComplete(bossId: String): Boolean =
        deathStarted &&
            deathBossId == bossId &&
            deathElapsedMilliseconds >= deathDurationMilliseconds

    fun update(state: RenderState, nowMilliseconds: Long): BossPresentationFrame {
        val matchChanged = initialized && state.matchId != matchId
        val generationChanged = initialized && state.matchGeneration != matchGeneration
        val matchRestarted = state.matchStartEventId != null &&
            state.matchStartEventId != matchStartEventId
        val reset = !initialized || matchChanged || generationChanged || matchRestarted
        if (reset) {
            initialized = true
            matchId = state.matchId
            matchGeneration = state.matchGeneration
            matchStartEventId = state.matchStartEventId
            lastUpdateMilliseconds = null
            activeBoss = null
            lastBossX = null
            recentMovementGraceMilliseconds = 0L
            idleElapsedMilliseconds = 0L
            walkElapsedMilliseconds = 0L
            clearAttack()
            clearDeath()
        }

        val boss = state.boss
        val bossKey = boss?.let { it.entityId to it.presentationKey }
        val bossChanged = bossKey != activeBoss
        if (bossChanged) {
            lastBossX = boss?.x
            recentMovementGraceMilliseconds = 0L
            idleElapsedMilliseconds = 0L
            walkElapsedMilliseconds = 0L
            clearAttack()
            clearDeath()
        }
        if (boss != null && boss.health > 0 && deathStarted) {
            clearDeath()
        }

        val elapsed = if (
            !reset &&
            !bossChanged &&
            bossKey != null &&
            !state.isPaused &&
            !wasPaused &&
            lastUpdateMilliseconds != null
        ) {
            (nowMilliseconds - requireNotNull(lastUpdateMilliseconds)).coerceAtLeast(0L)
        } else {
            0L
        }
        if (boss == null) {
            lastBossX = null
            recentMovementGraceMilliseconds = 0L
        } else {
            val movedSinceLastUpdate = !bossChanged &&
                lastBossX?.let { abs(boss.x - it) > MIN_MOVEMENT_WORLD_UNITS } == true
            recentMovementGraceMilliseconds = if (movedSinceLastUpdate) {
                MOVEMENT_GRACE_MILLISECONDS
            } else {
                (recentMovementGraceMilliseconds - elapsed).coerceAtLeast(0L)
            }
            lastBossX = boss.x
        }
        val bossIsWalking = boss?.let {
            it.phase == BossPhase.MARCHING ||
                (it.phase == BossPhase.ENRAGED && recentMovementGraceMilliseconds > 0L)
        } == true
        val deathWasActiveForBoss =
            boss != null && deathStarted && deathBossId == boss.entityId
        if (bossKey != null) {
            if (deathWasActiveForBoss) {
                deathElapsedMilliseconds = (
                    deathElapsedMilliseconds + elapsed
                    ).coerceAtMost(deathDurationMilliseconds)
            } else if (boss.health > 0) {
                idleElapsedMilliseconds += elapsed
                attackElapsedMilliseconds += elapsed
                walkElapsedMilliseconds = if (bossIsWalking) {
                    walkElapsedMilliseconds + elapsed
                } else {
                    0L
                }
            }
        } else {
            idleElapsedMilliseconds = 0L
            walkElapsedMilliseconds = 0L
            clearAttack()
            clearDeath()
        }

        activeBoss = bossKey
        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused
        val definition = boss?.let { BossPresentationCatalog.definition(it.presentationKey) }
            ?: return BossPresentationFrame()

        if (boss.health <= 0) {
            if (!deathWasActiveForBoss) {
                val deathAnimation = definition.deathAnimation()
                deathStarted = true
                deathBossId = boss.entityId
                deathElapsedMilliseconds = 0L
                deathDurationMilliseconds =
                    deathAnimation?.durationMilliseconds
                        ?: DEFAULT_DEATH_PRESENTATION_DURATION_MILLISECONDS
                deathFallbackIdleFrameIndex =
                    definition.idleAnimation?.frameIndex(idleElapsedMilliseconds)
                walkElapsedMilliseconds = 0L
                clearAttack()
            }
            val deathAnimation = definition.deathAnimation()
            return BossPresentationFrame(
                idleFrameIndex = if (deathAnimation == null) {
                    deathFallbackIdleFrameIndex
                } else {
                    null
                },
                deathAnimationFrameIndex = deathAnimation?.frameIndex(deathElapsedMilliseconds),
                deathPresentationActive = true,
                deathPresentationComplete = isDeathPresentationComplete(boss.entityId),
            )
        }

        val attack = state.bossAttack
        if (attack != null && attack.eventId != attackEventId) {
            attackEventId = attack.eventId
            if (definition.attackAnimation(attack.direction) != null) {
                attackDirection = attack.direction
                attackElapsedMilliseconds = 0L
            } else {
                attackDirection = null
            }
        }
        val attackAnimation = attackDirection?.let { definition.attackAnimation(it) }
        if (attackAnimation != null && attackAnimation.isFinished(attackElapsedMilliseconds)) {
            attackDirection = null
        }
        val activeAttack = attackDirection?.let { direction ->
            val animation = requireNotNull(definition.attackAnimation(direction))
            direction to animation.frameIndex(attackElapsedMilliseconds)
        }

        return BossPresentationFrame(
            idleFrameIndex = definition.idleAnimation?.frameIndex(idleElapsedMilliseconds),
            walkFrameIndex = definition.walkAnimation
                ?.takeIf { bossIsWalking }
                ?.frameIndex(walkElapsedMilliseconds),
            attackDirection = activeAttack?.first,
            attackFrameIndex = activeAttack?.second,
        )
    }

    private fun clearAttack() {
        attackEventId = null
        attackDirection = null
        attackElapsedMilliseconds = 0L
    }

    private fun clearDeath() {
        deathBossId = null
        deathElapsedMilliseconds = 0L
        deathDurationMilliseconds = 0L
        deathFallbackIdleFrameIndex = null
        deathStarted = false
    }

    companion object {
        const val DEFAULT_DEATH_PRESENTATION_DURATION_MILLISECONDS = 4_000L
        private const val MIN_MOVEMENT_WORLD_UNITS = 0.01f
        private const val MOVEMENT_GRACE_MILLISECONDS = 150L
    }
}
