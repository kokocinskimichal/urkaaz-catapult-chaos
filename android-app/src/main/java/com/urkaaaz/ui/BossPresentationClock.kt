package com.urkaaaz.ui

data class BossPresentationFrame(
    val idleFrameIndex: Int? = null,
    val attackDirection: BossAttackDirection? = null,
    val attackFrameIndex: Int? = null,
)

class BossPresentationClock {
    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var activeBoss: Pair<String, String>? = null
    private var idleElapsedMilliseconds = 0L
    private var attackEventId: String? = null
    private var attackDirection: BossAttackDirection? = null
    private var attackElapsedMilliseconds = 0L
    private var wasPaused = false

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
            idleElapsedMilliseconds = 0L
            clearAttack()
        }

        val boss = state.boss?.takeIf { it.health > 0 }
        val bossKey = boss?.let { it.entityId to it.presentationKey }
        val bossChanged = bossKey != activeBoss
        if (bossChanged) {
            idleElapsedMilliseconds = 0L
            clearAttack()
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
        if (bossKey != null) {
            idleElapsedMilliseconds += elapsed
            attackElapsedMilliseconds += elapsed
        } else {
            idleElapsedMilliseconds = 0L
            clearAttack()
        }

        activeBoss = bossKey
        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused
        val definition = boss?.let { BossPresentationCatalog.definition(it.presentationKey) }
            ?: return BossPresentationFrame()

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
            attackDirection = activeAttack?.first,
            attackFrameIndex = activeAttack?.second,
        )
    }

    private fun clearAttack() {
        attackEventId = null
        attackDirection = null
        attackElapsedMilliseconds = 0L
    }
}
