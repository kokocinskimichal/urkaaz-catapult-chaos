package com.urkaaaz.domain

import com.urkaaaz.contracts.Team
import kotlin.math.abs

/** A position in the simulated battlefield coordinate system. */
data class WorldPosition(
    val x: Float,
    val y: Float,
)

/** Linear movement vector expressed in world units per second. */
data class Velocity(
    val x: Float,
    val y: Float,
)

/**
 * Bounded hit-point value object.
 *
 * Damage and healing return new values instead of mutating the existing value.
 */
data class Health(
    val maximum: Int,
    val current: Int = maximum,
) {
    init {
        require(maximum > 0) { "maximum health must be positive" }
        require(current in 0..maximum) { "current health must be within health bounds" }
    }

    val isAlive: Boolean
        get() = current > 0

    fun damage(amount: Int): Health {
        require(amount >= 0) { "damage must not be negative" }
        return copy(current = (current - amount).coerceAtLeast(0))
    }

    fun heal(amount: Int): Health {
        require(amount >= 0) { "healing must not be negative" }
        return copy(current = (current + amount).coerceAtMost(maximum))
    }
}

/**
 * Runtime resources owned by a player or team.
 *
 * Limited ammunition is tracked by count; unlimited ammunition is represented
 * by the ammunition definition and does not consume inventory.
 */
data class ResourceWallet(
    val gold: Int = 0,
    val supply: Int = 0,
    val mana: Int = 0,
    val ammunition: Map<AmmunitionType, Int> = emptyMap(),
) {
    init {
        require(gold >= 0) { "gold must not be negative" }
        require(supply >= 0) { "supply must not be negative" }
        require(mana >= 0) { "mana must not be negative" }
        require(ammunition.values.all { it >= 0 }) { "ammunition counts must not be negative" }
    }

    fun canSpendGold(amount: Int): Boolean = amount >= 0 && gold >= amount

    fun spendGold(amount: Int): ResourceWallet {
        require(canSpendGold(amount)) { "insufficient gold" }
        return copy(gold = gold - amount)
    }

    fun canSpendSupply(amount: Int): Boolean = amount >= 0 && supply >= amount

    fun spendSupply(amount: Int): ResourceWallet {
        require(canSpendSupply(amount)) { "insufficient supply" }
        return copy(supply = supply - amount)
    }

    fun ammunitionCount(type: AmmunitionType): Int =
        if (type.unlimited) Int.MAX_VALUE else ammunition[type] ?: 0

    fun canUse(type: AmmunitionType): Boolean =
        type.unlimited || ammunitionCount(type) > 0

    fun consume(type: AmmunitionType): ResourceWallet {
        require(canUse(type)) { "ammunition is unavailable" }
        if (type.unlimited) return this
        return copy(ammunition = ammunition + (type to ammunitionCount(type) - 1))
    }
}

/** Validated catapult aiming intent used by the domain and simulation. */
data class Aim(
    val directionDegrees: Float,
    val power: Float,
) {
    init {
        require(directionDegrees in 0f..180f) { "direction must be between 0 and 180 degrees" }
        require(power in 0f..100f) { "power must be between 0 and 100" }
    }
}

/** Dimensions of the simulated battlefield. */
data class WorldBounds(
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f) { "world width must be positive" }
        require(height > 0f) { "world height must be positive" }
    }
}

fun WorldPosition.distanceTo(other: WorldPosition): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

fun Float.isApproximatelyEqual(other: Float, tolerance: Float = 0.5f): Boolean =
    abs(this - other) <= tolerance

/** Converts a domain team to the team value exposed by the public contracts. */
enum class DomainTeam {
    BLUE,
    RED;

    fun toContractTeam(): Team = when (this) {
        BLUE -> Team.BLUE
        RED -> Team.RED
    }
}
