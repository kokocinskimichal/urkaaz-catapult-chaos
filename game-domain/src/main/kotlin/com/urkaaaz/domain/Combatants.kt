package com.urkaaaz.domain

import com.urkaaaz.contracts.EntityId
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
enum class UnitType(
    val goldCost: Int,
    val maxHealth: Int,
    val speed: Float,
    val attackDamage: Int,
    val attackCooldownSeconds: Float,
) {
    SAPPER(10, 60, 260f, 180, 1.75f),
    DEFENDER(5, 260, 150f, 30, 0.8f),
    DEMOLISHER(10, 70, 110f, 0, 0.8f),
    RAGING_BOAR(10, 420, 240f, 52, 0.8f),
    SLINGMASTER(5, 75, 120f, 20, 1.2f),
}

/** A unit deployed on the battlefield. */
data class Unit(
    val id: EntityId,
    val type: UnitType,
    val team: Team,
    val position: WorldPosition,
    val health: Health = Health(maximum = type.maxHealth),
) {
    init {
        require(health.maximum == type.maxHealth) { "unit health maximum must match unit type" }
    }

    val isAlive: Boolean
        get() = health.isAlive

    fun withDamage(amount: Int): Unit = copy(health = health.damage(amount))
}
