package com.urkaaaz.domain

/** Data-driven behavior currently implemented by the deterministic simulation. */
sealed interface SpellEffectDefinition {
    data class FortressShield(
        val hitPoints: Int,
    ) : SpellEffectDefinition {
        init {
            require(hitPoints > 0) { "shield hit points must be positive" }
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
) {
    init {
        require(goldCost >= 0) { "spell gold cost must not be negative" }
        require(manaCost > 0) { "spell mana cost must be positive" }
    }

    val isImplemented: Boolean
        get() = effect != null
}

/** Canonical spell balance and effect catalog, independent of UI and persistence. */
object SpellCatalog {
    val all: List<SpellDefinition> = listOf(
        SpellDefinition(
            type = SpellType.ICE_TRAP,
            goldCost = 500,
            category = SpellCategory.BATTLEFIELD_CONTROL,
            targetCategory = SpellTargetCategory.ENEMY_FORCE,
            special = false,
            manaCost = 50,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.RUNIC_BASTION,
            goldCost = 1_200,
            category = SpellCategory.DEFENSIVE,
            targetCategory = SpellTargetCategory.OWN_GATE,
            special = false,
            manaCost = 70,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.HASTE,
            goldCost = 900,
            category = SpellCategory.SUPPORT,
            targetCategory = SpellTargetCategory.ALLIED_FORCE,
            special = false,
            manaCost = 50,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.HEAL,
            goldCost = 900,
            category = SpellCategory.SUPPORT,
            targetCategory = SpellTargetCategory.ALLIED_FORCE,
            special = false,
            manaCost = 50,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.FORTRESS_SHIELD,
            goldCost = 0,
            category = SpellCategory.DEFENSIVE,
            targetCategory = SpellTargetCategory.OWN_FORTRESS,
            special = false,
            manaCost = 80,
            effect = SpellEffectDefinition.FortressShield(hitPoints = 80),
        ),
        SpellDefinition(
            type = SpellType.CATAPULT_LOCK,
            goldCost = 1_000,
            category = SpellCategory.SIEGE,
            targetCategory = SpellTargetCategory.ENEMY_SIEGE_ENGINE,
            special = true,
            manaCost = 60,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.SLOWING_FIELD,
            goldCost = 1_000,
            category = SpellCategory.BATTLEFIELD_CONTROL,
            targetCategory = SpellTargetCategory.ENEMY_FORCE,
            special = true,
            manaCost = 50,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.VAMPIRIC_BANNER,
            goldCost = 1_100,
            category = SpellCategory.SUPPORT,
            targetCategory = SpellTargetCategory.ALLIED_FORCE,
            special = true,
            manaCost = 60,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.STONE_SKIN,
            goldCost = 1_100,
            category = SpellCategory.DEFENSIVE,
            targetCategory = SpellTargetCategory.ALLIED_FORCE,
            special = true,
            manaCost = 60,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.SIEGE_FRENZY,
            goldCost = 1_200,
            category = SpellCategory.OFFENSIVE,
            targetCategory = SpellTargetCategory.ALLIED_FORCE,
            special = true,
            manaCost = 60,
            effect = null,
        ),
        SpellDefinition(
            type = SpellType.BERSERKER,
            goldCost = 2_000,
            category = SpellCategory.OFFENSIVE,
            targetCategory = SpellTargetCategory.ALLIED_UNIT,
            special = true,
            manaCost = 100,
            effect = null,
        ),
    )

    private val definitionsByType = all.associateBy { it.type }

    init {
        require(definitionsByType.size == all.size) { "spell definitions must be unique" }
        require(definitionsByType.keys == SpellType.values().toSet()) {
            "every stable spell identifier must have a catalog definition"
        }
    }

    fun definition(type: SpellType): SpellDefinition = definitionsByType.getValue(type)

    fun find(id: String): SpellDefinition? =
        SpellType.values().firstOrNull { it.name == id }?.let(::definition)
}

/** Match-runtime mana defaults, based on the legacy match rules. */
object ManaConfiguration {
    const val STARTING_MANA = 100
    const val MAXIMUM_MANA = 100
    const val REGENERATION_AMOUNT = 1
    const val REGENERATION_INTERVAL_MILLISECONDS = 800L
}
