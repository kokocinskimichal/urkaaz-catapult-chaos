package com.urkaaaz.ui

data class FireRainSplitRenderState(
    val eventId: String,
    val projectileId: String,
    val fragmentProjectileIds: List<String>,
    val x: Float,
    val y: Float,
)

data class FireRainEffectRenderState(
    val effectId: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val remainingMilliseconds: Long,
)

data class FireRainSplitFlash(
    val x: Float,
    val y: Float,
    val frameIndex: Int,
)

data class FireRainFieldAnimation(
    val effectId: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val frameIndex: Int,
)

data class FireRainPresentationFrame(
    val projectileFrameIndex: Int = 0,
    val hiddenProjectileIds: Set<String> = emptySet(),
    val fragmentProjectileIds: Set<String> = emptySet(),
    val splitFlashes: List<FireRainSplitFlash> = emptyList(),
    val fireFields: List<FireRainFieldAnimation> = emptyList(),
)

class FireRainPresentationClock {
    private data class ActiveSplit(
        val event: FireRainSplitRenderState,
        var elapsedMilliseconds: Long = 0L,
    )

    private data class ActiveField(
        var effect: FireRainEffectRenderState,
        var elapsedMilliseconds: Long = 0L,
    )

    private val seenSplitEventIds = mutableSetOf<String>()
    private val activeSplits = linkedMapOf<String, ActiveSplit>()
    private val hiddenProjectiles = mutableMapOf<String, Boolean>()
    private val fragmentProjectiles = mutableMapOf<String, Boolean>()
    private val activeFields = linkedMapOf<String, ActiveField>()

    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var wasPaused = false
    private var projectileElapsedMilliseconds = 0L

    fun update(state: RenderState, nowMilliseconds: Long): FireRainPresentationFrame {
        val matchChanged = initialized && state.matchId != matchId
        val matchRestartedByGeneration =
            initialized && state.matchGeneration != matchGeneration
        val matchRestarted = state.matchStartEventId != null &&
            state.matchStartEventId != matchStartEventId
        val reset = !initialized || matchChanged || matchRestartedByGeneration || matchRestarted
        if (reset) {
            clearRuntimeState()
            initialized = true
            matchId = state.matchId
            matchGeneration = state.matchGeneration
        }
        state.matchStartEventId?.let { matchStartEventId = it }

        val elapsed = if (
            !reset &&
            !state.isPaused &&
            !wasPaused &&
            lastUpdateMilliseconds != null
        ) {
            (nowMilliseconds - requireNotNull(lastUpdateMilliseconds)).coerceAtLeast(0L)
        } else {
            0L
        }
        if (elapsed > 0L) {
            projectileElapsedMilliseconds += elapsed
            activeSplits.values.forEach { it.elapsedMilliseconds += elapsed }
            activeFields.values.forEach { it.elapsedMilliseconds += elapsed }
        }

        activeSplits.entries.removeAll {
            it.value.elapsedMilliseconds >= SPLIT_FLASH_DURATION_MILLISECONDS
        }
        state.fireRainSplits.forEach { event ->
            if (seenSplitEventIds.add(event.eventId)) {
                activeSplits[event.eventId] = ActiveSplit(event)
                hiddenProjectiles.putIfAbsent(event.projectileId, false)
                event.fragmentProjectileIds.forEach {
                    fragmentProjectiles.putIfAbsent(it, false)
                }
            }
        }

        val currentProjectileIds = state.projectiles.mapTo(mutableSetOf()) {
            it.projectileId
        }
        updateProjectileRetention(hiddenProjectiles, currentProjectileIds)
        updateProjectileRetention(fragmentProjectiles, currentProjectileIds)

        val currentEffects = state.fireRainEffects.associateBy(FireRainEffectRenderState::effectId)
        activeFields.keys.retainAll(currentEffects.keys)
        currentEffects.forEach { (effectId, effect) ->
            val active = activeFields[effectId]
            if (active == null) {
                activeFields[effectId] = ActiveField(effect)
            } else {
                active.effect = effect
            }
        }

        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused

        return FireRainPresentationFrame(
            projectileFrameIndex = (
                projectileElapsedMilliseconds / PROJECTILE_FRAME_DURATION_MILLISECONDS
                ).toInt(),
            hiddenProjectileIds = hiddenProjectiles
                .filterValues { it }
                .keys
                .toSet(),
            fragmentProjectileIds = fragmentProjectiles
                .filterValues { it }
                .keys
                .toSet(),
            splitFlashes = activeSplits.values.map { active ->
                FireRainSplitFlash(
                    x = active.event.x,
                    y = active.event.y,
                    frameIndex = frameIndex(
                        active.elapsedMilliseconds,
                        SPLIT_FLASH_DURATION_MILLISECONDS,
                        SPLIT_FLASH_FRAME_COUNT,
                    ),
                )
            },
            fireFields = activeFields.values.map { active ->
                FireRainFieldAnimation(
                    effectId = active.effect.effectId,
                    x = active.effect.x,
                    y = active.effect.y,
                    radius = active.effect.radius,
                    frameIndex = fireFieldFrameIndex(
                        elapsedMilliseconds = active.elapsedMilliseconds,
                        remainingMilliseconds = active.effect.remainingMilliseconds,
                    ),
                )
            },
        )
    }

    fun clear() {
        clearRuntimeState()
        initialized = false
        matchId = null
        matchGeneration = 0L
        matchStartEventId = null
        lastUpdateMilliseconds = null
        wasPaused = false
    }

    private fun updateProjectileRetention(
        retainedProjectiles: MutableMap<String, Boolean>,
        currentProjectileIds: Set<String>,
    ) {
        retainedProjectiles.entries.removeAll { (projectileId, wasObserved) ->
            wasObserved && projectileId !in currentProjectileIds
        }
        retainedProjectiles.replaceAll { projectileId, wasObserved ->
            wasObserved || projectileId in currentProjectileIds
        }
    }

    private fun clearRuntimeState() {
        seenSplitEventIds.clear()
        activeSplits.clear()
        hiddenProjectiles.clear()
        fragmentProjectiles.clear()
        activeFields.clear()
        projectileElapsedMilliseconds = 0L
        lastUpdateMilliseconds = null
        wasPaused = false
        matchStartEventId = null
    }

    private fun frameIndex(elapsedMilliseconds: Long, durationMilliseconds: Long, frameCount: Int): Int =
        ((elapsedMilliseconds * frameCount) / durationMilliseconds)
            .toInt()
            .coerceIn(0, frameCount - 1)

    private fun fireFieldFrameIndex(
        elapsedMilliseconds: Long,
        remainingMilliseconds: Long,
    ): Int {
        if (remainingMilliseconds <= FIRE_FIELD_FADE_DURATION_MILLISECONDS) {
            val fadeProgress = (
                FIRE_FIELD_FADE_DURATION_MILLISECONDS -
                    remainingMilliseconds.coerceAtLeast(0L)
                ).coerceAtMost(FIRE_FIELD_FADE_DURATION_MILLISECONDS)
            val fadeFrame = (
                fadeProgress * FIRE_FIELD_FADE_FRAME_COUNT /
                    FIRE_FIELD_FADE_DURATION_MILLISECONDS
                ).toInt().coerceAtMost(FIRE_FIELD_FADE_FRAME_COUNT - 1)
            return FIRE_FIELD_FADE_FIRST_FRAME + fadeFrame
        }

        if (elapsedMilliseconds < FIRE_FIELD_IGNITION_DURATION_MILLISECONDS) {
            return (elapsedMilliseconds / FIRE_FIELD_IGNITION_FRAME_DURATION_MILLISECONDS)
                .toInt()
                .coerceAtMost(FIRE_FIELD_IGNITION_LAST_FRAME)
        }

        val loopFrame = (
            (elapsedMilliseconds - FIRE_FIELD_IGNITION_DURATION_MILLISECONDS) /
                FIRE_FIELD_LOOP_FRAME_DURATION_MILLISECONDS
            ).toInt() % FIRE_FIELD_LOOP_FRAME_COUNT
        return FIRE_FIELD_LOOP_FIRST_FRAME + loopFrame
    }

    private companion object {
        const val PROJECTILE_FRAME_DURATION_MILLISECONDS = 100L
        const val SPLIT_FLASH_DURATION_MILLISECONDS = 600L
        const val SPLIT_FLASH_FRAME_COUNT = 12
        const val FIRE_FIELD_IGNITION_DURATION_MILLISECONDS = 250L
        const val FIRE_FIELD_IGNITION_FRAME_DURATION_MILLISECONDS = 125L
        const val FIRE_FIELD_IGNITION_LAST_FRAME = 1
        const val FIRE_FIELD_LOOP_FIRST_FRAME = 2
        const val FIRE_FIELD_LOOP_FRAME_COUNT = 11
        const val FIRE_FIELD_LOOP_FRAME_DURATION_MILLISECONDS = 100L
        const val FIRE_FIELD_FADE_FIRST_FRAME = 19
        const val FIRE_FIELD_FADE_FRAME_COUNT = 5
        const val FIRE_FIELD_FADE_DURATION_MILLISECONDS = 500L
    }
}
