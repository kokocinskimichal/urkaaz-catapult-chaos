package com.urkaaaz.ui

import com.urkaaaz.contracts.WorldScale
import kotlin.math.abs

internal class DemolisherDeathAnimationPredictor(
    private val collisionRadius: Float = WorldScale.UNIT_COLLISION_RADIUS,
) {
    private var previousUnitsById = emptyMap<String, UnitRenderState>()
    private var previousTimestampMilliseconds: Long? = null
    private var anticipatedTargetsByDemolisher = emptyMap<String, String>()

    fun update(
        units: List<UnitRenderState>,
        nowMilliseconds: Long,
        allowPrediction: Boolean = true,
    ): Map<String, String> {
        val currentUnitsById = units.associateBy(UnitRenderState::entityId)
        if (!allowPrediction) {
            previousUnitsById = currentUnitsById
            previousTimestampMilliseconds = nowMilliseconds
            anticipatedTargetsByDemolisher = anticipatedTargetsByDemolisher
                .filter { (demolisherId, targetId) ->
                    isValidPair(currentUnitsById, demolisherId, targetId)
                }
            return anticipatedTargetsByDemolisher
        }

        val elapsedMilliseconds = previousTimestampMilliseconds
            ?.let { nowMilliseconds - it }
        val hasUsableSample = elapsedMilliseconds != null &&
            elapsedMilliseconds in 1..MAX_SAMPLE_INTERVAL_MILLISECONDS
        val livingUnits = units.filter { it.health > 0 && it.actionState != "DEAD" }
        val livingUnitsById = livingUnits.associateBy(UnitRenderState::entityId)
        val nextAnticipatedTargets = if (hasUsableSample) {
            mutableMapOf()
        } else {
            anticipatedTargetsByDemolisher.filter { (demolisherId, targetId) ->
                isValidPair(currentUnitsById, demolisherId, targetId)
            }.toMutableMap()
        }

        livingUnits.asSequence()
            .filter { it.unitType == "DEMOLISHER" }
            .forEach { demolisher ->
                val target = selectTarget(demolisher, livingUnits, livingUnitsById)
                    ?: return@forEach
                val gap = abs(target.x - demolisher.x)
                val isWithinContactBand = gap <= collisionRadius + CONTACT_HOLD_MARGIN

                if (!hasUsableSample) {
                    if (isWithinContactBand) {
                        nextAnticipatedTargets[demolisher.entityId] = target.entityId
                    }
                    return@forEach
                }

                val previousDemolisher = previousUnitsById[demolisher.entityId]
                    ?: return@forEach
                val previousTarget = previousUnitsById[target.entityId]
                    ?: return@forEach
                val previousGap = abs(previousTarget.x - previousDemolisher.x)
                val seconds = elapsedMilliseconds!! / 1_000f
                val closingSpeed = (previousGap - gap) / seconds
                val timeToContactMilliseconds = if (closingSpeed > 0f) {
                    ((gap - collisionRadius).coerceAtLeast(0f) / closingSpeed) *
                        1_000f
                } else {
                    Float.POSITIVE_INFINITY
                }
                val isClosing = (demolisher.moving || target.moving) &&
                    closingSpeed >= MIN_CLOSING_SPEED
                val contactPredicted = isClosing &&
                    timeToContactMilliseconds <= PRE_IMPACT_DURATION_MILLISECONDS

                if (contactPredicted || isWithinContactBand) {
                    nextAnticipatedTargets[demolisher.entityId] = target.entityId
                }
            }

        previousUnitsById = currentUnitsById
        previousTimestampMilliseconds = nowMilliseconds
        anticipatedTargetsByDemolisher = nextAnticipatedTargets
        return nextAnticipatedTargets
    }

    fun reset() {
        previousUnitsById = emptyMap()
        previousTimestampMilliseconds = null
        anticipatedTargetsByDemolisher = emptyMap()
    }

    private fun selectTarget(
        demolisher: UnitRenderState,
        livingUnits: List<UnitRenderState>,
        livingUnitsById: Map<String, UnitRenderState>,
    ): UnitRenderState? {
        demolisher.targetId
            ?.let(livingUnitsById::get)
            ?.takeIf { it.teamLabel != demolisher.teamLabel }
            ?.let { return it }

        return livingUnits.asSequence()
            .filter { it.teamLabel != demolisher.teamLabel }
            .filter { (it.x - demolisher.x) * demolisher.facingDirection >= 0f }
            .minByOrNull { abs(it.x - demolisher.x) }
    }

    private fun isValidPair(
        unitsById: Map<String, UnitRenderState>,
        demolisherId: String,
        targetId: String,
    ): Boolean {
        val demolisher = unitsById[demolisherId] ?: return false
        val target = unitsById[targetId] ?: return false
        return demolisher.unitType == "DEMOLISHER" &&
            demolisher.health > 0 &&
            demolisher.actionState != "DEAD" &&
            target.health > 0 &&
            target.actionState != "DEAD" &&
            target.teamLabel != demolisher.teamLabel
    }

    private companion object {
        const val MAX_SAMPLE_INTERVAL_MILLISECONDS = 250L
        const val MIN_CLOSING_SPEED = 10f
        const val CONTACT_HOLD_MARGIN = 6f
        const val PRE_IMPACT_DURATION_MILLISECONDS =
            13L * 3L * 33L
    }
}
