package com.urkaaaz.ui

class SapperMoveAnimationClock {
    private val previousMovingByEntity = mutableMapOf<String, Boolean>()
    private val arrivalGraceTicksByEntity = mutableMapOf<String, Int>()

    fun advance(units: List<UnitRenderState>, isPaused: Boolean) {
        val sappers = units.filter { it.unitType == "SAPPER" }
        val sapperIds = sappers.map { it.entityId }.toSet()
        previousMovingByEntity.keys.retainAll(sapperIds)
        arrivalGraceTicksByEntity.keys.retainAll(sapperIds)

        sappers.forEach { unit ->
            val wasMoving = previousMovingByEntity.put(unit.entityId, unit.moving)
            if (unit.moving || !isArrivalGraceCandidate(unit)) {
                arrivalGraceTicksByEntity.remove(unit.entityId)
            } else if (wasMoving == true) {
                arrivalGraceTicksByEntity[unit.entityId] = ARRIVAL_GRACE_RENDER_TICKS
            } else if (!isPaused) {
                val remainingTicks = arrivalGraceTicksByEntity[unit.entityId] ?: return@forEach
                if (remainingTicks <= 1) {
                    arrivalGraceTicksByEntity.remove(unit.entityId)
                } else {
                    arrivalGraceTicksByEntity[unit.entityId] = remainingTicks - 1
                }
            }
        }
    }

    internal fun shouldUseMoveAnimation(
        unit: UnitRenderState,
        attacking: Boolean,
    ): Boolean =
        unit.unitType == "SAPPER" &&
            !attacking &&
            (
                unit.moving ||
                    (
                        isArrivalGraceCandidate(unit) &&
                            arrivalGraceTicksByEntity[unit.entityId] != null
                        )
                )

    fun reset() {
        previousMovingByEntity.clear()
        arrivalGraceTicksByEntity.clear()
    }

    private fun isArrivalGraceCandidate(unit: UnitRenderState): Boolean =
        unit.unitType == "SAPPER" &&
            !unit.moving &&
            unit.actionState == "MOVING" &&
            unit.sapperHasBomb

    private companion object {
        const val ARRIVAL_GRACE_RENDER_TICKS = 4
    }
}
