package com.urkaaaz.domain

/** Gameplay effect applied when a projectile of this type resolves. */
enum class AmmunitionEffect {
    DIRECT,
    EXPLOSIVE,
    INCENDIARY,
    PLAGUE,
}

/** Stable serialized ammunition identifiers. Balance lives in GameBalanceConfig. */
enum class AmmunitionType {
    ROCK,
    SIEGE_BOMB,
    POWDER_BARREL,
    FIRE_RAIN,
    PLAGUE_CAULDRON,
}
