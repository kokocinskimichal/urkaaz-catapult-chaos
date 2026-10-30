package com.urkaaaz.ui

internal class DemolisherAttackAnimationClock {
    private val frameIndicesByUnit = mutableMapOf<String, Int>()

    fun update(
        units: List<UnitRenderState>,
        frameCount: Int,
    ) {
        frameIndicesByUnit.clear()
        if (frameCount <= 0) {
            return
        }

        units.asSequence()
            .filter { it.unitType == "DEMOLISHER" && it.actionState == "THROWING" }
            .forEach { unit ->
                DemolisherAttackAnimation.frameIndex(
                    progress = unit.attackProgress,
                    frameCount = frameCount,
                )?.let { frameIndicesByUnit[unit.entityId] = it }
            }
    }

    fun frameIndex(entityId: String, frameCount: Int): Int? {
        if (frameCount <= 0) return null
        return frameIndicesByUnit[entityId]?.coerceIn(0, frameCount - 1)
    }

    fun activeEntityIds(): Set<String> = frameIndicesByUnit.keys.toSet()

    fun presentationUnits(
        units: List<UnitRenderState>,
        frameCount: Int,
    ): List<UnitRenderState> {
        return units.map { unit ->
            unit.copy(
                demolisherThrowFrameIndex = frameIndex(unit.entityId, frameCount),
            )
        }
    }

    fun reset() {
        frameIndicesByUnit.clear()
    }
}

internal object DemolisherAttackAnimation {
    internal const val THROW_START_FRAME_INDEX = 41
    internal const val THROW_END_FRAME_INDEX = 50

    fun frameIndex(progress: Float, frameCount: Int): Int? {
        if (frameCount <= 0) return null
        val firstFrame = THROW_START_FRAME_INDEX.coerceAtMost(frameCount - 1)
        val lastFrame = THROW_END_FRAME_INDEX.coerceAtMost(frameCount - 1)
        val progressInRange = progress.coerceIn(0f, 1f)
        return (
            firstFrame + ((lastFrame - firstFrame) * progressInRange).toInt()
            ).coerceIn(firstFrame, lastFrame)
    }
}
