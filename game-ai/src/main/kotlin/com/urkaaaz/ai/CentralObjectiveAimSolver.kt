package com.urkaaaz.ai

import com.urkaaaz.contracts.BossSnapshot
import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.domain.Aim
import com.urkaaaz.domain.AmmunitionCollisionMode
import com.urkaaaz.domain.AmmunitionHitLayer
import com.urkaaaz.domain.FriendlyFirePolicy
import com.urkaaaz.domain.Projectile
import com.urkaaaz.domain.Velocity
import com.urkaaaz.domain.WorldPosition
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal object CentralObjectiveAimSolver {
    private const val STEP_SECONDS = 1f / 60f

    fun solve(
        context: AiStrategyContext,
        catapult: CatapultSnapshot,
        ammunition: com.urkaaaz.domain.AmmunitionType,
        boss: BossSnapshot,
    ): Aim? {
        val terrain = context.snapshot.terrain
        if (terrain.heightSamples.isEmpty() || terrain.sampleSpacing <= 0f) return null
        val definition = context.balance.ammunition.definition(ammunition)
        if (AmmunitionHitLayer.FORTRESS !in definition.collision.hitLayers) return null

        val worldWidth = terrain.worldWidth
        val physicsScale =
            (worldWidth / WorldScale.PHYSICS_REFERENCE_WORLD_WIDTH).coerceAtLeast(1f)
        val windAccelerationX = context.snapshot.wind.direction *
            context.snapshot.wind.strength *
            context.balance.runtime.physics.windAccelerationScale
        val gravityAccelerationY =
            context.balance.runtime.physics.gravityAccelerationY * physicsScale
        val evaluator = TrajectoryEvaluator(
            context = context,
            catapult = catapult,
            boss = boss,
            ammunition = ammunition,
            definition = definition,
            worldWidth = worldWidth,
            physicsScale = physicsScale,
            windAccelerationX = windAccelerationX,
            gravityAccelerationY = gravityAccelerationY,
        )

        var best: TrajectoryCandidate? = null
        var angle = 10f
        while (angle <= 86f) {
            var power = 5f
            while (power <= 100f) {
                val candidate = evaluator.evaluate(angle, power)
                if (candidate.isBetterThan(best)) best = candidate
                power += 5f
            }
            angle += 2f
        }

        val coarseBest = best
            ?.takeIf { it.closestNormalizedDistance.isFinite() }
            ?: return null
        angle = (coarseBest.angle - 2f).coerceAtLeast(1f)
        val angleLimit = (coarseBest.angle + 2f).coerceAtMost(89f)
        while (angle <= angleLimit) {
            var power = (coarseBest.power - 5f).coerceAtLeast(1f)
            val powerLimit = (coarseBest.power + 5f).coerceAtMost(100f)
            while (power <= powerLimit) {
                val candidate = evaluator.evaluate(angle, power)
                if (candidate.isBetterThan(best)) best = candidate
                power += 1f
            }
            angle += 0.25f
        }
        return best
            ?.takeIf(TrajectoryCandidate::hit)
            ?.let { Aim(it.angle, it.power) }
    }

    private data class TrajectoryCandidate(
        val angle: Float,
        val power: Float,
        val hit: Boolean,
        val closestNormalizedDistance: Float,
        val flightSeconds: Float,
    ) {
        fun isBetterThan(other: TrajectoryCandidate?): Boolean {
            if (other == null) return true
            if (hit != other.hit) return hit
            return if (hit) {
                flightSeconds < other.flightSeconds ||
                    (flightSeconds == other.flightSeconds && power < other.power)
            } else {
                closestNormalizedDistance < other.closestNormalizedDistance ||
                    (closestNormalizedDistance == other.closestNormalizedDistance &&
                        flightSeconds < other.flightSeconds)
            }
        }
    }

    private class TrajectoryEvaluator(
        private val context: AiStrategyContext,
        private val catapult: CatapultSnapshot,
        private val boss: BossSnapshot,
        private val ammunition: com.urkaaaz.domain.AmmunitionType,
        private val definition: com.urkaaaz.domain.AmmunitionDefinition,
        private val worldWidth: Float,
        private val physicsScale: Float,
        private val windAccelerationX: Float,
        private val gravityAccelerationY: Float,
    ) {
        private val bossTopY = boss.groundY - boss.collisionHeight / 2f
        private val bossRadiusX = boss.collisionWidth / 2f + definition.collision.hitRadius
        private val bossRadiusY = boss.collisionHeight / 2f + definition.collision.hitRadius
        private val maximumSteps =
            ceil(definition.flight.maximumLifetimeSeconds / STEP_SECONDS).toInt()
        private val direction = if (context.team == Team.BLUE) 1f else -1f
        private val speedScale = definition.flight.initialSpeedMultiplier * physicsScale

        fun evaluate(angle: Float, power: Float): TrajectoryCandidate {
            val radians = Math.toRadians(angle.toDouble())
            val speed = power * speedScale
            var projectile = Projectile(
                id = EntityId("ai-trajectory-probe"),
                ammunition = ammunition,
                firedBy = context.team,
                position = WorldPosition(catapult.anchorX, catapult.anchorY),
                velocity = Velocity(
                    x = cos(radians).toFloat() * speed * direction,
                    y = -sin(radians).toFloat() * speed,
                ),
                mass = definition.flight.mass,
                windResponse = definition.flight.windResponse,
                gravityResponse = definition.flight.gravityResponse,
                drag = definition.flight.drag,
                apexFragmentation = definition.flight.apexFragmentation,
            )
            var closestDistance = Float.POSITIVE_INFINITY
            var flightSeconds = 0f

            repeat(maximumSteps) {
                val previous = projectile
                projectile = projectile.advance(
                    deltaSeconds = STEP_SECONDS,
                    windAccelerationX = windAccelerationX,
                    gravityAccelerationY = gravityAccelerationY,
                    windResponse = projectile.windResponse,
                    gravityResponse = projectile.gravityResponse,
                    drag = projectile.drag,
                )
                flightSeconds += STEP_SECONDS
                val terrainImpact = terrainImpactPosition(previous, STEP_SECONDS)
                val impactPosition = terrainImpact ?: projectile.position
                if (hitsStoppingUnit(impactPosition)) {
                    return TrajectoryCandidate(
                        angle,
                        power,
                        hit = false,
                        closestNormalizedDistance = closestDistance,
                        flightSeconds = flightSeconds,
                    )
                }

                val normalizedDistance = bossDistance(impactPosition)
                closestDistance = minOf(closestDistance, normalizedDistance)
                if (normalizedDistance <= 1f) {
                    return TrajectoryCandidate(
                        angle,
                        power,
                        hit = true,
                        closestNormalizedDistance = normalizedDistance,
                        flightSeconds = flightSeconds,
                    )
                }
                if (terrainImpact != null) {
                    return TrajectoryCandidate(
                        angle,
                        power,
                        hit = false,
                        closestNormalizedDistance = closestDistance,
                        flightSeconds = flightSeconds,
                    )
                }
                if (
                    projectile.position.x < 0f ||
                    projectile.position.x > worldWidth ||
                    projectile.position.y < -WorldDimensions.HEIGHT ||
                    (boss.x - projectile.position.x) * direction <
                    -bossRadiusX
                ) {
                    return TrajectoryCandidate(
                        angle,
                        power,
                        hit = false,
                        closestNormalizedDistance = closestDistance,
                        flightSeconds = flightSeconds,
                    )
                }
            }
            return TrajectoryCandidate(
                angle,
                power,
                hit = false,
                closestNormalizedDistance = closestDistance,
                flightSeconds = flightSeconds,
            )
        }

        private fun hitsStoppingUnit(position: WorldPosition): Boolean {
            val collision = definition.collision
            if (
                AmmunitionHitLayer.UNIT !in collision.hitLayers ||
                collision.mode == AmmunitionCollisionMode.IGNORE_UNITS ||
                collision.mode == AmmunitionCollisionMode.PIERCE_UNITS
            ) {
                return false
            }
            return context.snapshot.units.any { unit ->
                unit.health > 0 &&
                    (collision.friendlyFire != FriendlyFirePolicy.ENEMY_ONLY ||
                        unit.team != context.team) &&
                    hypot(unit.x - position.x, unit.y - position.y) <= collision.hitRadius
            }
        }

        private fun bossDistance(position: WorldPosition): Float =
            hypot(
                (boss.x - position.x) / bossRadiusX,
                (bossTopY - position.y) / bossRadiusY,
            )

        private fun terrainImpactPosition(
            previous: Projectile,
            deltaSeconds: Float,
        ): WorldPosition? {
            val previousPosition = previous.position
            if (previousPosition.x !in 0f..worldWidth) return null

            fun clearance(position: WorldPosition): Float =
                position.y - terrainHeightAt(position.x)

            fun positionAt(timeSeconds: Float): WorldPosition = WorldPosition(
                x = previousPosition.x +
                    (previous.velocity.x +
                        windAccelerationX * previous.windResponse * timeSeconds) * timeSeconds,
                y = previousPosition.y +
                    (previous.velocity.y +
                        gravityAccelerationY * previous.gravityResponse * timeSeconds) *
                    timeSeconds,
            )

            var intervalStart = previousPosition
            var intervalStartClearance = clearance(intervalStart)
            var hasBeenAboveTerrain = intervalStartClearance < 0f
            if (!hasBeenAboveTerrain && previous.ageSeconds > 0f && previous.velocity.y >= 0f) {
                return previousPosition
            }

            val sampleCount = ceil(deltaSeconds / STEP_SECONDS).toInt().coerceAtLeast(1)
            for (sampleIndex in 1..sampleCount) {
                val intervalEnd = positionAt(deltaSeconds * sampleIndex / sampleCount)
                if (intervalEnd.x !in 0f..worldWidth) break
                val intervalEndClearance = clearance(intervalEnd)
                if (!hasBeenAboveTerrain) {
                    hasBeenAboveTerrain = intervalEndClearance < 0f
                    intervalStart = intervalEnd
                    intervalStartClearance = intervalEndClearance
                    continue
                }
                if (intervalStartClearance < 0f && intervalEndClearance >= 0f) {
                    var above = intervalStart
                    var below = intervalEnd
                    repeat(12) {
                        val midpoint = WorldPosition(
                            x = (above.x + below.x) * 0.5f,
                            y = (above.y + below.y) * 0.5f,
                        )
                        if (clearance(midpoint) < 0f) {
                            above = midpoint
                        } else {
                            below = midpoint
                        }
                    }
                    return below
                }
                intervalStart = intervalEnd
                intervalStartClearance = intervalEndClearance
            }
            return null
        }

        private fun terrainHeightAt(x: Float): Float {
            val samples = context.snapshot.terrain.heightSamples
            val position = x.coerceIn(0f, worldWidth) / context.snapshot.terrain.sampleSpacing
            val left = position.toInt().coerceIn(0, samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = position - left
            return samples[left] + (samples[right] - samples[left]) * fraction
        }
    }
}
