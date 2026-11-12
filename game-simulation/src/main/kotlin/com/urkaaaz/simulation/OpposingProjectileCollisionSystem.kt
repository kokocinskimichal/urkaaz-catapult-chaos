package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.domain.Projectile

internal data class ProjectileCollisionOperations(
    val timeToApex: (Projectile, Float) -> Float?,
    val fragmentAtApex: (Projectile) -> List<Projectile>,
    val advanceProjectileSet: (
        List<Projectile>,
        Float,
        MutableMap<EntityId, MutableSet<EntityId>>,
    ) -> List<Projectile>,
    val advanceProjectilePhysics: (Projectile, Float) -> Projectile,
    val collisionRadius: (Projectile) -> Float,
    val onCollision: (EntityId) -> Unit,
)

internal class OpposingProjectileCollisionSystem(
    private val maxStepSeconds: Float,
    private val apexTimeEpsilonSeconds: Float,
    private val operations: ProjectileCollisionOperations,
) {
    private data class Candidate(
        val fraction: Float,
        val firstId: EntityId,
        val secondId: EntityId,
    )

    init {
        require(maxStepSeconds > 0f) { "projectile collision step must be positive" }
        require(apexTimeEpsilonSeconds >= 0f) {
            "apex time epsilon must not be negative"
        }
    }

    fun advance(projectiles: List<Projectile>, deltaSeconds: Float): List<Projectile> {
        var active = buildList {
            projectiles.forEach { addAll(operations.fragmentAtApex(it)) }
        }
        val nonStoppingTargetIds = active.associate { it.id to mutableSetOf<EntityId>() }
            .toMutableMap()
        var remainingSeconds = deltaSeconds.coerceAtLeast(0f)
        var processZeroTimeStep = deltaSeconds == 0f

        while (remainingSeconds > 0f || processZeroTimeStep) {
            val maximumStep = if (processZeroTimeStep) {
                0f
            } else {
                minOf(remainingSeconds, maxStepSeconds)
            }
            val apexTimes = active.mapNotNull { projectile ->
                operations.timeToApex(projectile, maximumStep)?.let { projectile.id to it }
            }
            val earliestApexTime = apexTimes.minOfOrNull { it.second }
            val stepSeconds = earliestApexTime
                ?.let { minOf(it, maximumStep) }
                ?.takeIf { it > 0f }
                ?: maximumStep
            val splitAtStepEnd = apexTimes
                .filter { (_, apexTime) ->
                    kotlin.math.abs(apexTime - stepSeconds) <= apexTimeEpsilonSeconds
                }
                .mapTo(mutableSetOf()) { it.first }
            var segmentRemainingSeconds = stepSeconds
            var processZeroTimeSegment = processZeroTimeStep
            while (segmentRemainingSeconds > 0f || processZeroTimeSegment) {
                if (active.isEmpty()) break
                val collision = earliestCollision(active, segmentRemainingSeconds)
                if (collision == null) {
                    active = operations.advanceProjectileSet(
                        active,
                        segmentRemainingSeconds,
                        nonStoppingTargetIds,
                    )
                    segmentRemainingSeconds = 0f
                    processZeroTimeSegment = false
                    break
                }

                val collisionTravelSeconds =
                    segmentRemainingSeconds * collision.fraction
                if (collisionTravelSeconds > 0f) {
                    active = operations.advanceProjectileSet(
                        active,
                        collisionTravelSeconds,
                        nonStoppingTargetIds,
                    )
                    segmentRemainingSeconds =
                        (segmentRemainingSeconds - collisionTravelSeconds).coerceAtLeast(0f)
                }
                val activeIds = active.mapTo(mutableSetOf(), Projectile::id)
                if (collision.firstId in activeIds && collision.secondId in activeIds) {
                    active = active.filterNot {
                        it.id == collision.firstId || it.id == collision.secondId
                    }
                    nonStoppingTargetIds.remove(collision.firstId)
                    nonStoppingTargetIds.remove(collision.secondId)
                    operations.onCollision(collision.firstId)
                    operations.onCollision(collision.secondId)
                }
            }

            if (splitAtStepEnd.isNotEmpty()) {
                val splitProjectiles = mutableListOf<Projectile>()
                active.forEach { projectile ->
                    if (projectile.id !in splitAtStepEnd) {
                        splitProjectiles += projectile
                    } else {
                        nonStoppingTargetIds.remove(projectile.id)
                        val parentAtApex = projectile.copy(
                            velocity = projectile.velocity.copy(y = 0f),
                        )
                        val fragments = operations.fragmentAtApex(parentAtApex)
                        splitProjectiles += fragments
                        fragments.forEach { fragment ->
                            nonStoppingTargetIds[fragment.id] = mutableSetOf()
                        }
                    }
                }
                active = splitProjectiles
            }

            if (processZeroTimeStep) {
                processZeroTimeStep = false
            } else {
                remainingSeconds = (remainingSeconds - stepSeconds).coerceAtLeast(0f)
            }
        }
        return active
    }

    private fun earliestCollision(
        projectiles: List<Projectile>,
        deltaSeconds: Float,
    ): Candidate? {
        val advancedById = projectiles.associate { projectile ->
            projectile.id to operations.advanceProjectilePhysics(projectile, deltaSeconds)
        }
        val candidates = mutableListOf<Candidate>()
        for (firstIndex in projectiles.indices) {
            val first = projectiles[firstIndex]
            val advancedFirst = advancedById.getValue(first.id)
            val firstRadius = operations.collisionRadius(first)
            for (secondIndex in firstIndex + 1 until projectiles.size) {
                val second = projectiles[secondIndex]
                if (first.firedBy == second.firedBy) continue
                val advancedSecond = advancedById.getValue(second.id)
                val secondRadius = operations.collisionRadius(second)
                val fraction = sweptCollisionFraction(
                    firstStartX = first.position.x,
                    firstStartY = first.position.y,
                    firstEndX = advancedFirst.position.x,
                    firstEndY = advancedFirst.position.y,
                    firstRadius = firstRadius,
                    secondStartX = second.position.x,
                    secondStartY = second.position.y,
                    secondEndX = advancedSecond.position.x,
                    secondEndY = advancedSecond.position.y,
                    secondRadius = secondRadius,
                ) ?: continue
                val orderedIds = listOf(first.id, second.id).sortedBy(EntityId::value)
                candidates += Candidate(
                    fraction = fraction,
                    firstId = orderedIds[0],
                    secondId = orderedIds[1],
                )
            }
        }

        return candidates.minWithOrNull(
            compareBy<Candidate>(
                Candidate::fraction,
                { it.firstId.value },
                { it.secondId.value },
            ),
        )
    }

    private fun sweptCollisionFraction(
        firstStartX: Float,
        firstStartY: Float,
        firstEndX: Float,
        firstEndY: Float,
        firstRadius: Float,
        secondStartX: Float,
        secondStartY: Float,
        secondEndX: Float,
        secondEndY: Float,
        secondRadius: Float,
    ): Float? {
        val startX = firstStartX - secondStartX
        val startY = firstStartY - secondStartY
        val deltaX = (firstEndX - firstStartX) - (secondEndX - secondStartX)
        val deltaY = (firstEndY - firstStartY) - (secondEndY - secondStartY)
        val radius = firstRadius + secondRadius
        val c = startX * startX + startY * startY - radius * radius
        if (c <= 0f) return 0f

        val a = deltaX * deltaX + deltaY * deltaY
        if (a <= 0f) return null
        val b = 2f * (startX * deltaX + startY * deltaY)
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null
        val fraction = (-b - kotlin.math.sqrt(discriminant)) / (2f * a)
        return fraction.takeIf { it in 0f..1f }
    }
}
