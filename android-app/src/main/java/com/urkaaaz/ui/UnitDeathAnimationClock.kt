package com.urkaaaz.ui

class UnitDeathAnimationClock {
    private val elapsedRenderTicksByUnit = mutableMapOf<String, Int>()
    private val prestartedDemolisherIds = mutableSetOf<String>()

    fun advance(units: List<UnitRenderState>) {
        val animatedUnitIds = units.asSequence()
            .filter { it.actionState == "DEAD" || it.deathAnimationPrestarted }
            .map { it.entityId }
            .toSet()

        elapsedRenderTicksByUnit.keys.retainAll(animatedUnitIds)
        prestartedDemolisherIds.retainAll(animatedUnitIds)
        units.asSequence()
            .filter { it.entityId in animatedUnitIds }
            .forEach { unit ->
                val entityId = unit.entityId
                val elapsedTicks = elapsedRenderTicksByUnit[entityId]
                elapsedRenderTicksByUnit[entityId] = if (unit.actionState == "DEAD") {
                    val nextTicks = elapsedTicks?.plus(1) ?: 0
                    val firstExplosionTick = if (entityId in prestartedDemolisherIds) {
                        DEMOLISHER_EXPLOSION_FRAME_INDEX * RENDER_TICKS_PER_FRAME
                    } else {
                        0
                    }
                    prestartedDemolisherIds.remove(entityId)
                    maxOf(nextTicks, firstExplosionTick)
                        .coerceAtMost(MAX_ELAPSED_RENDER_TICKS)
                } else {
                    if (unit.unitType == "DEMOLISHER") {
                        prestartedDemolisherIds += entityId
                    }
                    (elapsedTicks ?: -1)
                        .plus(1)
                        .coerceAtMost(PRE_IMPACT_HOLD_RENDER_TICK)
                }
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

    internal fun resetUnit(entityId: String) {
        elapsedRenderTicksByUnit.remove(entityId)
        prestartedDemolisherIds.remove(entityId)
    }

    fun reset() {
        elapsedRenderTicksByUnit.clear()
        prestartedDemolisherIds.clear()
    }

    private companion object {
        const val RENDER_TICKS_PER_FRAME = 3
        const val DEMOLISHER_EXPLOSION_FRAME_INDEX = 13
        const val PRE_IMPACT_HOLD_RENDER_TICK =
            DEMOLISHER_EXPLOSION_FRAME_INDEX * RENDER_TICKS_PER_FRAME - 1
        const val MAX_ELAPSED_RENDER_TICKS = Int.MAX_VALUE - RENDER_TICKS_PER_FRAME
    }
}

internal class UnitDeathAnimationRetention(
    private val animationClock: UnitDeathAnimationClock,
) {
    private val retainedUnits = linkedMapOf<String, UnitRenderState>()
    private val deathAnimationPredictor = DemolisherDeathAnimationPredictor()
    private var anticipatedTargetsByDemolisher = emptyMap<String, String>()

    fun update(
        units: List<UnitRenderState>,
        nowMilliseconds: Long = 0L,
        allowAnticipation: Boolean = true,
    ): List<UnitRenderState> {
        val currentUnitsById = units.associateBy(UnitRenderState::entityId)
        val anticipatedTargets = deathAnimationPredictor.update(
            units = units,
            nowMilliseconds = nowMilliseconds,
            allowPrediction = allowAnticipation,
        )
        anticipatedTargetsByDemolisher.forEach { (entityId, previousTargetId) ->
            if (
                anticipatedTargets[entityId] != null &&
                anticipatedTargets[entityId] != previousTargetId &&
                currentUnitsById[entityId]?.actionState != "DEAD"
            ) {
                animationClock.resetUnit(entityId)
            }
        }
        anticipatedTargetsByDemolisher = anticipatedTargets

        val presentationUnits = units.map { unit ->
            unit.copy(
                deathAnimationPrestarted =
                    unit.actionState != "DEAD" &&
                        anticipatedTargets.containsKey(unit.entityId),
            )
        }
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
        anticipatedTargetsByDemolisher = emptyMap()
        deathAnimationPredictor.reset()
        animationClock.reset()
    }

    private fun deathFrameCount(unit: UnitRenderState): Int? =
        when (unit.unitType) {
            "DEFENDER" ->
                DEFENDER_DEATH_FRAME_COUNT.takeIf {
                    unit.teamLabel == "BLUE" || unit.teamLabel == "RED"
                }
            "DEMOLISHER" ->
                DEMOLISHER_DEATH_FRAME_COUNT.takeIf {
                    unit.teamLabel == "BLUE" || unit.teamLabel == "RED"
                }
            else -> null
        }

    private companion object {
        const val DEFENDER_DEATH_FRAME_COUNT = 60
        const val DEMOLISHER_DEATH_FRAME_COUNT = 32
    }
}
