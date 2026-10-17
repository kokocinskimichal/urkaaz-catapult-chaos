package com.urkaaaz.ui

data class PlagueCauldronEffectRenderState(
    val effectId: String,
    val sourceProjectileId: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val remainingMilliseconds: Long,
)

data class PlagueCauldronCloudAnimation(
    val effectId: String,
    val sourceProjectileId: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val frameIndex: Int,
    val growthProgress: Float,
    val opacity: Float,
)

data class PlagueCauldronPresentationFrame(
    val clouds: List<PlagueCauldronCloudAnimation> = emptyList(),
)

class PlagueCauldronPresentationClock {
    private data class ActiveCloud(
        var effect: PlagueCauldronEffectRenderState,
        var elapsedMilliseconds: Long = 0L,
    )

    private val activeClouds = linkedMapOf<String, ActiveCloud>()
    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var wasPaused = false

    fun update(state: RenderState, nowMilliseconds: Long): PlagueCauldronPresentationFrame {
        val matchChanged = initialized && state.matchId != matchId
        val generationChanged = initialized && state.matchGeneration != matchGeneration
        val matchRestarted = state.matchStartEventId != null &&
            state.matchStartEventId != matchStartEventId
        val reset = !initialized || matchChanged || generationChanged || matchRestarted
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
            activeClouds.values.forEach { it.elapsedMilliseconds += elapsed }
        }

        val currentEffects = state.plagueCauldronEffects
            .filter { it.remainingMilliseconds > 0L }
            .associateBy(PlagueCauldronEffectRenderState::effectId)
        activeClouds.keys.retainAll(currentEffects.keys)
        currentEffects.forEach { (effectId, effect) ->
            val active = activeClouds[effectId]
            if (active == null) {
                activeClouds[effectId] = ActiveCloud(effect)
            } else {
                active.effect = effect
            }
        }

        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused

        return PlagueCauldronPresentationFrame(
            clouds = activeClouds.values.map { active ->
                val growthProgress = (
                    active.elapsedMilliseconds.toFloat() /
                        PlagueCauldronVisualContract.GROWTH_DURATION_MILLISECONDS
                    ).coerceIn(0f, 1f)
                PlagueCauldronCloudAnimation(
                    effectId = active.effect.effectId,
                    sourceProjectileId = active.effect.sourceProjectileId,
                    x = active.effect.x,
                    y = active.effect.y,
                    radius = active.effect.radius,
                    frameIndex = cloudFrameIndex(active.elapsedMilliseconds, growthProgress),
                    growthProgress = growthProgress,
                    opacity = cloudOpacity(active.effect.remainingMilliseconds),
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

    private fun cloudFrameIndex(elapsedMilliseconds: Long, growthProgress: Float): Int =
        if (growthProgress < 1f) {
            val frameOffset = (
                growthProgress *
                    (PlagueCauldronVisualContract.GROWTH_LAST_FRAME -
                        PlagueCauldronVisualContract.CLOUD_FIRST_FRAME)
                ).toInt()
            PlagueCauldronVisualContract.CLOUD_FIRST_FRAME + frameOffset
        } else {
            val loopFrame = (
                (elapsedMilliseconds -
                    PlagueCauldronVisualContract.GROWTH_DURATION_MILLISECONDS) /
                    PlagueCauldronVisualContract.LOOP_FRAME_DURATION_MILLISECONDS
                ).toInt() % PlagueCauldronVisualContract.LOOP_FRAME_COUNT
            PlagueCauldronVisualContract.LOOP_FIRST_FRAME + loopFrame
        }

    private fun cloudOpacity(remainingMilliseconds: Long): Float {
        val fadeProgress = (
            remainingMilliseconds.toFloat() /
                PlagueCauldronVisualContract.FADE_DURATION_MILLISECONDS
            ).coerceIn(0f, 1f)
        return PlagueCauldronVisualContract.MAX_OPACITY * fadeProgress
    }

    private fun clearRuntimeState() {
        activeClouds.clear()
        lastUpdateMilliseconds = null
        wasPaused = false
        matchStartEventId = null
    }
}

internal object PlagueCauldronVisualContract {
    const val CLOUD_FIRST_FRAME = 5
    const val GROWTH_LAST_FRAME = 10
    const val LOOP_FIRST_FRAME = 11
    const val LOOP_FRAME_COUNT = 13
    const val GROWTH_DURATION_MILLISECONDS = 1_200L
    const val LOOP_FRAME_DURATION_MILLISECONDS = 200L
    const val FADE_DURATION_MILLISECONDS = 1_000L
    const val MAX_OPACITY = 0.5f
    const val GROWTH_START_WIDTH_TO_RADIUS = 0.6f
    const val GROWTH_START_HEIGHT_TO_RADIUS = 0.38f
    const val CANVAS_WIDTH_TO_RADIUS = 8f / 3f
    const val CANVAS_HEIGHT_TO_RADIUS = 1.7f
    const val IMPACT_GROWTH_END_FRAME = 8
    const val IMPACT_OPACITY_FIRST_FRAME = 3
    const val IMPACT_OPACITY_LAST_FRAME = 7
}
