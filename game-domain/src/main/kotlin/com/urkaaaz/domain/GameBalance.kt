package com.urkaaaz.domain

import kotlin.math.roundToInt

/** Multipliers applied to one unit's shipped [UnitConfiguration]. */
data class UnitTuning(
    val healthMultiplier: Float = 1f,
    val damageMultiplier: Float = 1f,
    val speedMultiplier: Float = 1f,
    val supplyCostMultiplier: Float = 1f,
) {
    init {
        require(healthMultiplier > 0f) { "health multiplier must be positive" }
        require(damageMultiplier >= 0f) { "damage multiplier must not be negative" }
        require(speedMultiplier >= 0f) { "speed multiplier must not be negative" }
        require(supplyCostMultiplier >= 0f) { "supply cost multiplier must not be negative" }
    }
}

/** Economy parameters of a match. */
data class SupplyBalance(
    val startingSupply: Int = SupplyConfiguration.MAXIMUM_SUPPLY,
    val regenIntervalSeconds: Float = DEFAULT_REGEN_INTERVAL_SECONDS,
) {
    init {
        require(startingSupply in 0..SupplyConfiguration.MAXIMUM_SUPPLY) {
            "starting supply must be within 0..${SupplyConfiguration.MAXIMUM_SUPPLY}"
        }
        require(regenIntervalSeconds > 0f) { "supply regeneration interval must be positive" }
    }

    companion object {
        const val DEFAULT_REGEN_INTERVAL_SECONDS = 3f
    }
}

/**
 * Injectable balance parameters: unit and ammunition catalogs plus supply economy.
 * [default] reproduces the shipped game; [tuned] derives a variant without touching global state.
 */
data class GameBalance(
    val units: UnitCatalog = UnitCatalog.default,
    val ammunition: AmmunitionCatalog = AmmunitionCatalog.default,
    val supply: SupplyBalance = SupplyBalance(),
) {
    companion object {
        val default = GameBalance()

        fun tuned(
            unitTunings: Map<String, UnitTuning> = emptyMap(),
            ammunitionTunings: Map<AmmunitionType, AmmunitionTuning> = emptyMap(),
            supply: SupplyBalance = SupplyBalance(),
        ): GameBalance {
            val unitIds = UnitConfigurations.all.map { it.id }.toSet()
            require(unitTunings.keys.all { it in unitIds }) { "unknown unit in tuning" }
            return GameBalance(
                units = UnitCatalog(
                    UnitConfigurations.all.map { configuration ->
                        unitTunings[configuration.id]?.let(configuration::tuned) ?: configuration
                    },
                ),
                ammunition = AmmunitionCatalog(
                    AmmunitionCatalog.default.all.map { definition ->
                        ammunitionTunings[definition.type]
                            ?.let { TunedAmmunitionDefinition(definition, it) }
                            ?: definition
                    },
                ),
                supply = supply,
            )
        }
    }
}

private fun UnitConfiguration.tuned(tuning: UnitTuning) = copy(
    maxHealth = (maxHealth * tuning.healthMultiplier).roundToInt().coerceAtLeast(1),
    attackDamage = (attackDamage * tuning.damageMultiplier).roundToInt(),
    fortressDamage = (fortressDamage * tuning.damageMultiplier).roundToInt(),
    speed = speed * tuning.speedMultiplier,
    supplyCost = (supplyCost * tuning.supplyCostMultiplier).roundToInt(),
)
