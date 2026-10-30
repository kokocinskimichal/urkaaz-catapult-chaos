package com.urkaaaz.ui

class UnitDeathAnimationClock {
    private val elapsedRenderTicksByUnit = mutableMapOf<String, Int>()

    fun advance(units: List<UnitRenderState>) {
        val animatedUnitIds = units.asSequence()
            .filter { it.actionState == "DEAD" }
            .map { it.entityId }
            .toSet()

        elapsedRenderTicksByUnit.keys.retainAll(animatedUnitIds)
        units.asSequence()
            .filter { it.entityId in animatedUnitIds }
            .forEach { unit ->
                val entityId = unit.entityId
                elapsedRenderTicksByUnit[entityId] =
                    (elapsedRenderTicksByUnit[entityId]?.plus(1) ?: 0)
                        .coerceAtMost(MAX_ELAPSED_RENDER_TICKS)
            }
    }

    fun frameIndex(entityId: String, frameCount: Int): Int? {
        if (frameCount <= 0) return null
        val elapsedTicks = elapsedRenderTicksByUnit[entityId] ?: return 0
        return (elapsedTicks / RENDER_TICKS_PER_FRAME).coerceIn(0, frameCount - 1)
    }

    internal fun hasElapsed(entityId: String, frameCount: Int): Boolean =
        elapsedRenderTicksByUnit[entityId]
            ?.let { it >= frameCount * RENDER_TICKS_PER_FRAME }
            ?: false

    fun reset() {
        elapsedRenderTicksByUnit.clear()
    }

    private companion object {
        const val RENDER_TICKS_PER_FRAME = 3
        const val MAX_ELAPSED_RENDER_TICKS = Int.MAX_VALUE - RENDER_TICKS_PER_FRAME
    }
}

internal class UnitDeathAnimationRetention(
    private val animationClock: UnitDeathAnimationClock,
) {
    private val retainedUnits = linkedMapOf<String, UnitRenderState>()

    fun update(units: List<UnitRenderState>): List<UnitRenderState> {
        val presentationUnits = units.filterNot {
            it.unitType == "DEMOLISHER" &&
                it.actionState == "DEAD" &&
                it.demolisherThrowFrameIndex == null
        }
        val currentUnitsById = presentationUnits.associateBy(UnitRenderState::entityId)
        presentationUnits.asSequence()
            .filter { it.actionState == "DEAD" && deathFrameCount(it) != null }
            .forEach { retainedUnits[it.entityId] = it }

        retainedUnits.entries.removeAll { (entityId, _) ->
            currentUnitsById[entityId]?.actionState?.let { it != "DEAD" } == true
        }

        val unitsToRender = presentationUnits + retainedUnits.values.filterNot {
            it.entityId in currentUnitsById
        }
        animationClock.advance(unitsToRender)
        retainedUnits.entries.removeAll { (entityId, unit) ->
            entityId !in currentUnitsById &&
                deathFrameCount(unit)?.let { animationClock.hasElapsed(entityId, it) } == true
        }
        return unitsToRender
    }

    fun reset() {
        retainedUnits.clear()
        animationClock.reset()
    }

    private fun deathFrameCount(unit: UnitRenderState): Int? =
        when (unit.unitType) {
            "DEFENDER" ->
                DEFENDER_DEATH_FRAME_COUNT.takeIf {
                    unit.teamLabel == "BLUE" || unit.teamLabel == "RED"
                }
            else -> null
        }

    private companion object {
        const val DEFENDER_DEATH_FRAME_COUNT = 60
    }
}
