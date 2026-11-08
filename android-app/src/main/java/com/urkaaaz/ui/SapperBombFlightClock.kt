package com.urkaaaz.ui

import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.SapperBombFlightTiming
import com.urkaaaz.contracts.WorldScale
import kotlin.math.PI
import kotlin.math.atan2

data class SapperBombFlightFrame(
    val entityId: String,
    val x: Float,
    val y: Float,
    val rotationDegrees: Float,
    val frameIndex: Int,
)

data class SapperBombImpactFrame(
    val entityId: String,
    val x: Float,
    val y: Float,
    val frameIndex: Int,
)

data class SapperBombPresentationFrame(
    val flights: List<SapperBombFlightFrame> = emptyList(),
    val impacts: List<SapperBombImpactFrame> = emptyList(),
)

class SapperBombFlightClock {
    private data class ActiveFlight(
        val entityId: String,
        val startX: Float,
        val startY: Float,
        val targetX: Float,
        val targetY: Float,
        var elapsedMilliseconds: Long = 0L,
    )

    private data class ActiveImpact(
        val entityId: String,
        val x: Float,
        val y: Float,
        val frameCount: Int,
        var elapsedMilliseconds: Long = 0L,
    )

    private val activeFlights = linkedMapOf<String, ActiveFlight>()
    private val activeImpacts = linkedMapOf<String, ActiveImpact>()
    private var previousState: RenderState? = null
    private var initialized = false
    private var matchId: String? = null
    private var matchGeneration = 0L
    private var matchStartEventId: String? = null
    private var lastUpdateMilliseconds: Long? = null
    private var wasPaused = false

    fun update(
        state: RenderState,
        nowMilliseconds: Long,
        impactFrameCount: Int,
    ): SapperBombPresentationFrame {
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
            activeFlights.values.forEach { it.elapsedMilliseconds += elapsed }
            activeImpacts.values.forEach { it.elapsedMilliseconds += elapsed }
        }
        val completedFlights = activeFlights.values.filter {
            it.elapsedMilliseconds >= SapperBombFlightTiming.DURATION_MILLISECONDS
        }
        completedFlights.forEach { flight ->
            activeFlights.remove(flight.entityId)
            if (impactFrameCount > 0) {
                activeImpacts[flight.entityId] = ActiveImpact(
                    entityId = flight.entityId,
                    x = flight.targetX,
                    y = flight.targetY,
                    frameCount = impactFrameCount,
                    elapsedMilliseconds = (
                        flight.elapsedMilliseconds -
                            SapperBombFlightTiming.DURATION_MILLISECONDS
                        ).coerceAtLeast(0L),
                )
            }
        }
        activeImpacts.entries.removeAll {
            it.value.elapsedMilliseconds >=
                it.value.frameCount * IMPACT_FRAME_DURATION_MILLISECONDS
        }

        previousState?.let { previous ->
            val previousSappers = previous.units
                .asSequence()
                .filter { it.unitType == "SAPPER" }
                .associateBy(UnitRenderState::entityId)
            state.units
                .asSequence()
                .filter { it.unitType == "SAPPER" && !it.sapperHasBomb }
                .forEach { sapper ->
                    val previousSapper = previousSappers[sapper.entityId]
                        ?: return@forEach
                    if (!previousSapper.sapperHasBomb) return@forEach
                    val target = targetPosition(previous, previousSapper)
                        ?: return@forEach
                    val direction = facingDirection(previousSapper)
                    activeFlights[sapper.entityId] = ActiveFlight(
                        entityId = sapper.entityId,
                        startX = previousSapper.x + direction * RELEASE_OFFSET_X,
                        startY = previousSapper.y - RELEASE_OFFSET_Y,
                        targetX = target.first,
                        targetY = target.second,
                    )
                }
        }

        previousState = state
        lastUpdateMilliseconds = nowMilliseconds
        wasPaused = state.isPaused
        return SapperBombPresentationFrame(
            flights = activeFlights.values.map(::toFrame),
            impacts = activeImpacts.values.map(::toImpactFrame),
        )
    }

    fun reset() {
        clearRuntimeState()
        initialized = false
        matchId = null
        matchGeneration = 0L
        matchStartEventId = null
    }

    private fun targetPosition(state: RenderState, sapper: UnitRenderState): Pair<Float, Float>? {
        val targetId = sapper.targetId ?: return null
        val bastion = state.runicBastions.firstOrNull { it.entityId == targetId }
        if (bastion != null) {
            return if (bastion.teamLabel != sapper.teamLabel) {
                bastion.centerX to bastion.centerY
            } else {
                null
            }
        }

        state.boss?.takeIf { it.entityId == targetId }?.let { boss ->
            return boss.x to (boss.groundY - boss.collisionHeight / 2f)
        }

        val direction = when (sapper.teamLabel) {
            "BLUE" -> 1f
            "RED" -> -1f
            else -> return null
        }
        val targetTeam = when {
            targetId.startsWith("blue") -> "BLUE"
            targetId.startsWith("red") -> "RED"
            else -> return null
        }
        if (targetTeam == sapper.teamLabel) return null

        val targetX = if (targetTeam == "BLUE") state.blueFortressX else state.redFortressX
        val foundationY = if (targetTeam == "BLUE") {
            state.blueFortressFoundationY
        } else {
            state.redFortressFoundationY
        }
        val hitbox = FortressHitboxProfile.rectangles.firstOrNull() ?: return null
        val frontFaceOffset = if (direction > 0f) hitbox.left else hitbox.right
        return (targetX + frontFaceOffset) to
            (foundationY - WorldScale.FORTRESS_HEIGHT / 2f)
    }

    private fun facingDirection(sapper: UnitRenderState): Float =
        when {
            sapper.facingDirection > 0f -> 1f
            sapper.facingDirection < 0f -> -1f
            sapper.teamLabel == "BLUE" -> 1f
            else -> -1f
        }

    private fun toFrame(flight: ActiveFlight): SapperBombFlightFrame {
        val progress = (
            flight.elapsedMilliseconds.toFloat() /
                SapperBombFlightTiming.DURATION_MILLISECONDS
            ).coerceIn(0f, 1f)
        val arc = ARC_HEIGHT * 4f * progress * (1f - progress)
        val x = flight.startX + (flight.targetX - flight.startX) * progress
        val y = flight.startY + (flight.targetY - flight.startY) * progress - arc
        val verticalVelocity = flight.targetY - flight.startY -
            ARC_HEIGHT * 4f * (1f - 2f * progress)
        val rotationDegrees = (
            atan2(verticalVelocity, flight.targetX - flight.startX) * 180f / PI
            ).toFloat()
        return SapperBombFlightFrame(
            entityId = flight.entityId,
            x = x,
            y = y,
            rotationDegrees = rotationDegrees,
            frameIndex = (progress * FLIGHT_FRAME_COUNT)
                .toInt()
                .coerceIn(0, FLIGHT_FRAME_COUNT - 1),
        )
    }

    private fun toImpactFrame(impact: ActiveImpact): SapperBombImpactFrame =
        SapperBombImpactFrame(
            entityId = impact.entityId,
            x = impact.x,
            y = impact.y,
            frameIndex = (impact.elapsedMilliseconds / IMPACT_FRAME_DURATION_MILLISECONDS)
                .toInt()
                .coerceIn(0, impact.frameCount - 1),
        )

    private fun clearRuntimeState() {
        activeFlights.clear()
        activeImpacts.clear()
        previousState = null
        lastUpdateMilliseconds = null
        wasPaused = false
        matchStartEventId = null
    }

    private companion object {
        const val FLIGHT_FRAME_COUNT = 32
        const val RELEASE_OFFSET_X = 12f
        const val RELEASE_OFFSET_Y = 70f
        const val ARC_HEIGHT = 24f
        const val IMPACT_FRAME_DURATION_MILLISECONDS = 33L
    }
}
