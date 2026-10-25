package com.urkaaaz.ui

import com.urkaaaz.contracts.RunicBastionAnimationTiming

data class RunicBastionPresentationFrame(
    val creationFrameIndices: Map<String, Int> = emptyMap(),
    val idleFrameIndices: Map<String, Int> = emptyMap(),
    val destructionAnimations: List<RunicBastionDestructionAnimationFrame> = emptyList(),
)

data class RunicBastionDestructionAnimationFrame(
    val bastion: RunicBastionRenderState,
    val frameIndex: Int,
)

object RunicBastionPresentation {
    const val FRAME_COUNT = 20
    const val FRAME_WIDTH = 640
    const val FRAME_HEIGHT = 360
    const val FRAME_DURATION_MILLISECONDS = 120L
    const val CREATION_DURATION_MILLISECONDS =
        FRAME_COUNT * FRAME_DURATION_MILLISECONDS
    const val IDLE_FRAME_COUNT = 24
    const val IDLE_FRAME_DURATION_MILLISECONDS = 167L
    const val DESTRUCTION_FRAME_COUNT =
        RunicBastionAnimationTiming.DESTRUCTION_FRAME_COUNT
    const val DESTRUCTION_FRAME_DURATION_MILLISECONDS =
        RunicBastionAnimationTiming.DESTRUCTION_FRAME_DURATION_MILLISECONDS
    const val DESTRUCTION_DURATION_MILLISECONDS =
        RunicBastionAnimationTiming.DESTRUCTION_DURATION_MILLISECONDS

    internal const val VISIBLE_LEFT = 83f
    internal const val VISIBLE_TOP = 46f
    internal const val VISIBLE_RIGHT = 566f
    internal const val VISIBLE_BOTTOM = 328f
    internal val DEFAULT_VISIBLE_ART_BOUNDS = RunicBastionVisibleArtBounds(
        left = VISIBLE_LEFT,
        top = VISIBLE_TOP,
        right = VISIBLE_RIGHT,
        bottom = VISIBLE_BOTTOM,
    )
    internal val DESTRUCTION_VISIBLE_ART_BOUNDS = RunicBastionVisibleArtBounds(
        left = 201f,
        top = 112f,
        right = 444f,
        bottom = 254f,
    )

    fun frameIndex(elapsedMilliseconds: Long): Int =
        (elapsedMilliseconds.coerceAtLeast(0L) / FRAME_DURATION_MILLISECONDS)
            .coerceAtMost(FRAME_COUNT - 1L)
            .toInt()

    fun idleFrameIndex(elapsedMilliseconds: Long): Int {
        val elapsedFrames =
            elapsedMilliseconds.coerceAtLeast(0L) / IDLE_FRAME_DURATION_MILLISECONDS
        return (elapsedFrames % IDLE_FRAME_COUNT).toInt()
    }

    fun destructionFrameIndex(elapsedMilliseconds: Long): Int =
        (elapsedMilliseconds.coerceAtLeast(0L) / DESTRUCTION_FRAME_DURATION_MILLISECONDS)
            .coerceAtMost(DESTRUCTION_FRAME_COUNT - 1L)
            .toInt()

    fun spriteBounds(
        centerX: Float,
        foundationY: Float,
        visibleWidth: Float,
        visibleHeight: Float,
        visibleArtBounds: RunicBastionVisibleArtBounds = DEFAULT_VISIBLE_ART_BOUNDS,
    ): RunicBastionSpriteBounds {
        require(visibleWidth > 0f) { "visible width must be positive" }
        require(visibleHeight > 0f) { "visible height must be positive" }

        val scaleX = visibleWidth / (visibleArtBounds.right - visibleArtBounds.left)
        val scaleY = visibleHeight / (visibleArtBounds.bottom - visibleArtBounds.top)
        val left =
            centerX - (visibleArtBounds.left + visibleArtBounds.right) / 2f * scaleX
        val top = foundationY - visibleArtBounds.bottom * scaleY
        return RunicBastionSpriteBounds(
            left = left,
            top = top,
            right = left + FRAME_WIDTH * scaleX,
            bottom = top + FRAME_HEIGHT * scaleY,
        )
    }
}

data class RunicBastionVisibleArtBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class RunicBastionSpriteBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

class RunicBastionPresentationClock {
    private val elapsedByEntityId = linkedMapOf<String, Long>()
    private val previousBastionsByEntityId = linkedMapOf<String, RunicBastionRenderState>()
    private val destroyedBastionsByEntityId = linkedMapOf<String, RunicBastionRenderState>()
    private val destructionElapsedByEntityId = linkedMapOf<String, Long>()

    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var wasPaused = false

    fun update(state: RenderState, nowMilliseconds: Long): RunicBastionPresentationFrame {
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
            elapsedByEntityId.replaceAll { _, elapsedMilliseconds ->
                elapsedMilliseconds + elapsed
            }
            destructionElapsedByEntityId.replaceAll { _, elapsedMilliseconds ->
                elapsedMilliseconds + elapsed
            }
        }

        val activeBastions = state.runicBastions.associateBy { it.entityId }
        val activeEntityIds = activeBastions.keys
        if (!reset) {
            previousBastionsByEntityId.forEach { (entityId, bastion) ->
                if (entityId !in activeEntityIds) {
                    destroyedBastionsByEntityId[entityId] = bastion
                    destructionElapsedByEntityId[entityId] = 0L
                }
            }
        }

        elapsedByEntityId.keys.retainAll(activeEntityIds)
        activeEntityIds.forEach { entityId ->
            destroyedBastionsByEntityId.remove(entityId)
            destructionElapsedByEntityId.remove(entityId)
            elapsedByEntityId.putIfAbsent(entityId, 0L)
        }
        val completedDestructionIds = destructionElapsedByEntityId
            .filterValues {
                it >= RunicBastionPresentation.DESTRUCTION_DURATION_MILLISECONDS
            }
            .keys
        completedDestructionIds.forEach { entityId ->
            destroyedBastionsByEntityId.remove(entityId)
            destructionElapsedByEntityId.remove(entityId)
        }

        previousBastionsByEntityId.clear()
        previousBastionsByEntityId.putAll(activeBastions)

        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused

        return RunicBastionPresentationFrame(
            creationFrameIndices = elapsedByEntityId.mapValues { (_, elapsedMilliseconds) ->
                RunicBastionPresentation.frameIndex(elapsedMilliseconds)
            },
            idleFrameIndices = elapsedByEntityId.mapNotNull { (entityId, elapsedMilliseconds) ->
                if (elapsedMilliseconds < RunicBastionPresentation.CREATION_DURATION_MILLISECONDS) {
                    null
                } else {
                    entityId to RunicBastionPresentation.idleFrameIndex(
                        elapsedMilliseconds -
                            RunicBastionPresentation.CREATION_DURATION_MILLISECONDS,
                    )
                }
            }.toMap(),
            destructionAnimations = destructionElapsedByEntityId.mapNotNull {
                    (entityId, elapsedMilliseconds) ->
                destroyedBastionsByEntityId[entityId]?.let { bastion ->
                    RunicBastionDestructionAnimationFrame(
                        bastion = bastion,
                        frameIndex = RunicBastionPresentation.destructionFrameIndex(
                            elapsedMilliseconds,
                        ),
                    )
                }
            },
        )
    }

    fun clear() {
        clearRuntimeState()
        initialized = false
        matchId = null
        matchGeneration = 0L
        matchStartEventId = null
    }

    private fun clearRuntimeState() {
        elapsedByEntityId.clear()
        previousBastionsByEntityId.clear()
        destroyedBastionsByEntityId.clear()
        destructionElapsedByEntityId.clear()
        lastUpdateMilliseconds = null
        wasPaused = false
        matchStartEventId = null
    }
}
