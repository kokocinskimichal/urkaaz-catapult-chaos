package com.urkaaaz.domain

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.Team

/**
 * A catapult participating in a match.
 *
 * The model owns position, aim and health, but does not calculate projectile
 * flight or perform rendering.
 */
data class Catapult(
    val id: EntityId,
    val team: Team,
    val position: WorldPosition,
    val aim: Aim = Aim(directionDegrees = 45f, power = 50f),
    val health: Health = Health(maximum = 100),
) {
    val isAlive: Boolean
        get() = health.isAlive

    fun withAim(newAim: Aim): Catapult = copy(aim = newAim)

    fun withDamage(amount: Int): Catapult = copy(health = health.damage(amount))

    fun muzzlePosition(): WorldPosition = position.copy(y = position.y - 34f)
}

/** A fortress that can receive damage and be eliminated. */
data class Fortress(
    val id: EntityId,
    val team: Team,
    val center: WorldPosition,
    val maxHealth: Int = 500,
    val health: Health = Health(maximum = maxHealth),
) {
    init {
        require(health.maximum == maxHealth) { "fortress health maximum must match maxHealth" }
    }

    val isAlive: Boolean
        get() = health.isAlive

    fun withDamage(amount: Int): Fortress = copy(health = health.damage(amount))

    fun contains(position: WorldPosition): Boolean =
        FortressHitboxProfile.rectangles.any {
            it.contains(position.x - center.x, position.y - center.y)
        }

    fun intersects(position: WorldPosition, radius: Float): Boolean =
        FortressHitboxProfile.rectangles.any {
            it.distanceTo(position.x - center.x, position.y - center.y) <= radius
        }
}

/**
 * A projectile state used by the simulation.
 *
 * Advancing a projectile is deterministic for a given time step and wind
 * acceleration.
 */
data class Projectile(
    val id: EntityId,
    val ammunition: AmmunitionType,
    val firedBy: Team,
    val position: WorldPosition,
    val velocity: Velocity,
    val active: Boolean = true,
) {
    fun advance(
        deltaSeconds: Float,
        windAccelerationX: Float = 0f,
        gravityAccelerationY: Float = 0f,
    ): Projectile {
        require(deltaSeconds >= 0f) { "deltaSeconds must not be negative" }
        val nextVelocity = velocity.copy(
            x = velocity.x + windAccelerationX * deltaSeconds,
            y = velocity.y + gravityAccelerationY * deltaSeconds,
        )
        return copy(
            position = WorldPosition(
                x = position.x + nextVelocity.x * deltaSeconds,
                y = position.y + nextVelocity.y * deltaSeconds,
            ),
            velocity = nextVelocity,
        )
    }
}

/** Domain balance definition for a siege unit type. */
/** A unit deployed on the battlefield. */
data class Unit(
    val id: EntityId,
    val type: UnitDefinition,
    val team: Team,
    val position: WorldPosition,
    val health: Health = Health(maximum = type.maxHealth),
    val moving: Boolean = false,
    val garrisonTargetX: Float? = null,
    val combatState: UnitCombatState = UnitCombatState(),
    val sapperHasBomb: Boolean = true,
    val returningToGarrison: Boolean = false,
) {
    init {
        require(health.maximum == type.maxHealth) { "unit health maximum must match unit type" }
    }

    val isAlive: Boolean
        get() = health.isAlive

    fun withDamage(amount: Int): Unit = copy(health = health.damage(amount))
}
