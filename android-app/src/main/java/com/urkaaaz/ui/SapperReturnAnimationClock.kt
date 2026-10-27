package com.urkaaaz.ui

internal data class SapperReturnAnimationFrame(
    val phase: Phase,
    val index: Int,
) {
    enum class Phase {
        TURN,
        RETURN,
    }
}

class SapperReturnAnimationClock {
    private val elapsedTicksByEntity = mutableMapOf<String, Int>()

    fun advance(units: List<UnitRenderState>, isPaused: Boolean) {
        val returningSappers = units
            .asSequence()
            .filter { it.unitType == "SAPPER" && it.actionState == "RETURNING" }
            .map { it.entityId }
            .toSet()
        elapsedTicksByEntity.keys.retainAll(returningSappers)
        returningSappers.forEach { entityId ->
            val elapsedTicks = elapsedTicksByEntity[entityId]
            if (elapsedTicks == null) {
                elapsedTicksByEntity[entityId] = 0
            } else if (!isPaused) {
                elapsedTicksByEntity[entityId] = if (elapsedTicks < TURN_DURATION_TICKS) {
                    elapsedTicks + 1
                } else {
                    TURN_DURATION_TICKS +
                        (elapsedTicks - TURN_DURATION_TICKS + 1) % RETURN_DURATION_TICKS
                }
            }
        }
    }

    internal fun frame(
        entityId: String,
        turnFrameCount: Int,
        returnFrameCount: Int,
    ): SapperReturnAnimationFrame {
        require(turnFrameCount > 0) { "Sapper turn animation must contain frames" }
        require(returnFrameCount > 0) { "Sapper return animation must contain frames" }
        val turnDurationTicks = turnFrameCount * TICKS_PER_ANIMATION_FRAME
        val elapsedTicks = elapsedTicksByEntity[entityId] ?: 0
        return if (elapsedTicks < turnDurationTicks) {
            SapperReturnAnimationFrame(
                phase = SapperReturnAnimationFrame.Phase.TURN,
                index = elapsedTicks / TICKS_PER_ANIMATION_FRAME,
            )
        } else {
            SapperReturnAnimationFrame(
                phase = SapperReturnAnimationFrame.Phase.RETURN,
                index = (
                    (elapsedTicks - turnDurationTicks) / TICKS_PER_ANIMATION_FRAME
                    ) % returnFrameCount,
            )
        }
    }

    fun reset() {
        elapsedTicksByEntity.clear()
    }

    private companion object {
        const val FRAME_COUNT = 15
        const val TICKS_PER_ANIMATION_FRAME = 3
        const val TURN_DURATION_TICKS = FRAME_COUNT * TICKS_PER_ANIMATION_FRAME
        const val RETURN_DURATION_TICKS = FRAME_COUNT * TICKS_PER_ANIMATION_FRAME
    }
}
