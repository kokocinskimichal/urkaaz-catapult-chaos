package com.urkaaaz.ai

import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.domain.AmmunitionCatalog
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.Projectile
import com.urkaaaz.domain.Velocity
import com.urkaaaz.domain.WorldPosition
import com.urkaaaz.contracts.EntityId
import kotlin.math.cos
import kotlin.math.sin

/**
 * Terrain-free trajectory estimate used by wind-aware AI profiles.
 *
 * It integrates with [Projectile.advance], the same step the simulation uses. The
 * constants mirror the simulation defaults and are pinned by a test in `game-application`.
 */
object AiBallistics {
    const val GRAVITY_ACCELERATION = 420f
    const val WIND_ACCELERATION_SCALE = 390f
    const val BASE_PHYSICS_WORLD_WIDTH = 1_600f
    private const val STEP_SECONDS = 1f / 30f
    private const val MAX_FLIGHT_SECONDS = 20f
    private const val SEARCH_ITERATIONS = 24

    private val physicsScale = (WorldDimensions.WIDTH / BASE_PHYSICS_WORLD_WIDTH).coerceAtLeast(1f)

    /** Horizontal world position where a shot crosses [targetY] while falling, or null if it never does. */
    fun landingX(
        team: Team,
        originX: Float,
        originY: Float,
        targetY: Float,
        angleDegrees: Float,
        power: Float,
        ammunition: AmmunitionType,
        wind: WindSnapshot,
    ): Float? {
        val definition = AmmunitionCatalog.definition(ammunition)
        val flight = definition.flight
        val radians = Math.toRadians(angleDegrees.toDouble())
        val speed = power * flight.initialSpeedMultiplier * physicsScale
        val direction = if (team == Team.BLUE) 1f else -1f
        var projectile = Projectile(
            id = EntityId("ballistics"),
            ammunition = ammunition,
            firedBy = team,
            position = WorldPosition(originX, originY),
            velocity = Velocity(
                x = (cos(radians) * speed).toFloat() * direction,
                y = -(sin(radians) * speed).toFloat(),
            ),
            windResponse = flight.windResponse,
            gravityResponse = flight.gravityResponse,
            drag = flight.drag,
        )
        val windAcceleration = wind.direction * wind.strength * WIND_ACCELERATION_SCALE
        var seconds = 0f
        while (seconds < MAX_FLIGHT_SECONDS) {
            val next = projectile.advance(
                deltaSeconds = STEP_SECONDS,
                windAccelerationX = windAcceleration,
                gravityAccelerationY = GRAVITY_ACCELERATION * physicsScale,
                windResponse = flight.windResponse,
                gravityResponse = flight.gravityResponse,
                drag = flight.drag,
            )
            if (next.velocity.y > 0f && next.position.y >= targetY) {
                val span = next.position.y - projectile.position.y
                val t = if (span == 0f) 1f else ((targetY - projectile.position.y) / span).coerceIn(0f, 1f)
                return projectile.position.x + (next.position.x - projectile.position.x) * t
            }
            projectile = next
            seconds += STEP_SECONDS
        }
        return null
    }

    /** Power in 0..100 that lands the shot closest to [targetX], or null when the target is out of reach. */
    fun powerFor(
        team: Team,
        originX: Float,
        originY: Float,
        targetX: Float,
        targetY: Float,
        angleDegrees: Float,
        ammunition: AmmunitionType,
        wind: WindSnapshot,
    ): Float? {
        val direction = if (team == Team.BLUE) 1f else -1f
        fun signedError(power: Float): Float? =
            landingX(team, originX, originY, targetY, angleDegrees, power, ammunition, wind)
                ?.let { (it - targetX) * direction }

        var low = 0f
        var high = 100f
        val highError = signedError(high) ?: return null
        if (highError < 0f) return null
        repeat(SEARCH_ITERATIONS) {
            val middle = (low + high) / 2f
            val error = signedError(middle)
            if (error == null || error < 0f) low = middle else high = middle
        }
        return high
    }
}
