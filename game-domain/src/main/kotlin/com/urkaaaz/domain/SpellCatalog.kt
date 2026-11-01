package com.urkaaaz.domain

/** Data-driven behavior currently implemented by the deterministic simulation. */
sealed interface SpellEffectDefinition {
    data class Haste(
        val durationMilliseconds: Long,
        val movementSpeedMultiplier: Float,
    ) : SpellEffectDefinition {
        init {
            require(durationMilliseconds > 0L) { "haste duration must be positive" }
            require(movementSpeedMultiplier > 1f) {
                "haste movement speed multiplier must be greater than one"
            }
        }
    }

    data class FortressShield(
        val hitPoints: Int,
    ) : SpellEffectDefinition {
        init {
            require(hitPoints > 0) { "shield hit points must be positive" }
        }
    }

    data class RunicBastion(
        val hitPoints: Int,
    ) : SpellEffectDefinition {
        init {
            require(hitPoints > 0) { "bastion hit points must be positive" }
        }
    }
}

enum class SpellTargetCategory {
    ALLIED_FORCE,
    ALLIED_UNIT,
    ENEMY_FORCE,
    ENEMY_SIEGE_ENGINE,
    OWN_FORTRESS,
    OWN_GATE,
}

data class SpellDefinition(
    val type: SpellType,
    val goldCost: Int,
    val category: SpellCategory,
    val targetCategory: SpellTargetCategory,
    val special: Boolean,
    val manaCost: Int,
    val effect: SpellEffectDefinition?,
    val recastableWhileActive: Boolean,
) {
    init {
        require(goldCost >= 0) { "spell gold cost must not be negative" }
        require(manaCost > 0) { "spell mana cost must be positive" }
    }

    val isImplemented: Boolean
        get() = effect != null
}

/** Canonical spell balance and effect catalog, sourced from GameBalanceConfig. */
class SpellCatalog(definitions: List<SpellDefinition>) {
    val all: List<SpellDefinition> = definitions.toList()
    private val definitionsByType = all.associateBy(SpellDefinition::type)

    init {
        require(definitionsByType.size == all.size) { "spell definitions must be unique" }
        require(definitionsByType.keys == SpellType.values().toSet()) {
            "every stable spell identifier must have a catalog definition"
        }
        require(all.all { spell ->
            when (spell.type) {
                SpellType.HASTE -> spell.effect is SpellEffectDefinition.Haste
                SpellType.FORTRESS_SHIELD -> spell.effect is SpellEffectDefinition.FortressShield
                SpellType.RUNIC_BASTION -> spell.effect is SpellEffectDefinition.RunicBastion
                else -> spell.effect == null
            }
        }) {
            "spell effect data must match the effects currently implemented by the simulation"
        }
    }

    fun definition(type: SpellType): SpellDefinition = definitionsByType.getValue(type)

    fun find(id: String): SpellDefinition? =
        SpellType.values().firstOrNull { it.name == id }?.let(::definition)
}

data class ManaConfiguration(
    val startingMana: Int,
    val maximumMana: Int,
    val regenerationAmount: Int,
    val regenerationIntervalMilliseconds: Long,
) {
    init {
        require(maximumMana > 0) { "maximum mana must be positive" }
        require(startingMana in 0..maximumMana) { "starting mana must be within mana bounds" }
        require(regenerationAmount > 0) { "mana regeneration amount must be positive" }
        require(regenerationIntervalMilliseconds > 0L) {
            "mana regeneration interval must be positive"
        }
    }
}
