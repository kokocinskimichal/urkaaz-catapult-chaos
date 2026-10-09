package com.urkaaaz.domain

/** Gameplay effect applied when a projectile of this type resolves. */
enum class AmmunitionEffect {
    DIRECT,
    EXPLOSIVE,
    CLUSTER,
    INCENDIARY,
    PLAGUE,
}

/**
 * Domain definition of a projectile ammunition type.
 *
 * Rendering names, Android resource identifiers and animation assets are
 * intentionally not part of this model.
 */
enum class AmmunitionType(
    val effect: AmmunitionEffect,
    val reloadSeconds: Float,
    val damageRadius: Float,
    val damage: Int,
    val craterRadius: Float,
    val craterDepth: Float,
    val unlimited: Boolean,
    val explosionLifetimeSeconds: Float,
) {
    ROCK(
        effect = AmmunitionEffect.DIRECT,
        reloadSeconds = 0f,
        damageRadius = 28f,
        damage = 28,
        craterRadius = 34f,
        craterDepth = 22f,
        unlimited = true,
        explosionLifetimeSeconds = 0.8f,
    ),
    SIEGE_BOMB(
        effect = AmmunitionEffect.EXPLOSIVE,
        reloadSeconds = 5.5f,
        damageRadius = 115f,
        damage = 72,
        craterRadius = 125f,
        craterDepth = 82f,
        unlimited = true,
        explosionLifetimeSeconds = 0.8f,
    ),
    POWDER_BARREL(
        effect = AmmunitionEffect.EXPLOSIVE,
        reloadSeconds = 6.5f,
        damageRadius = 155f,
        damage = 110,
        craterRadius = 165f,
        craterDepth = 112f,
        unlimited = false,
        explosionLifetimeSeconds = 0.8f,
    ),
    CLUSTER_BOMB(
        effect = AmmunitionEffect.CLUSTER,
        reloadSeconds = 4.5f,
        damageRadius = 36f,
        damage = 22,
        craterRadius = 42f,
        craterDepth = 26f,
        unlimited = true,
        explosionLifetimeSeconds = 0.8f,
    ),
    FIRE_RAIN(
        effect = AmmunitionEffect.INCENDIARY,
        reloadSeconds = 8f,
        damageRadius = 68f,
        damage = 12,
        craterRadius = 54f,
        craterDepth = 16f,
        unlimited = false,
        explosionLifetimeSeconds = 0.8f,
    ),
    PLAGUE_CAULDRON(
        effect = AmmunitionEffect.PLAGUE,
        reloadSeconds = 15f,
        damageRadius = 260f,
        damage = 75,
        craterRadius = 0f,
        craterDepth = 0f,
        unlimited = false,
        explosionLifetimeSeconds = 1.3f,
    ),
}
