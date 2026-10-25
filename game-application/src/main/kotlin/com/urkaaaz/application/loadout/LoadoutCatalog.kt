package com.urkaaaz.application.loadout

import com.urkaaaz.domain.AmmunitionCatalog as DomainAmmunitionCatalog
import com.urkaaaz.domain.AmmunitionEffect
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellCategory
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.SpellEffectDefinition
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.ManaConfiguration
import com.urkaaaz.domain.SupplyConfiguration
import com.urkaaaz.domain.UnitConfigurations

data class LoadoutUnitDefinition(
    val id: String,
    val supplyCost: Int,
    val maxHealth: Int,
    val speed: Float,
    val attackDamage: Int,
    val attackRange: Float,
    val fortressDamage: Int,
)

enum class LoadoutAmmunitionEffect {
    DIRECT,
    EXPLOSIVE,
    INCENDIARY,
    PLAGUE,
}

data class LoadoutAmmunitionDefinition(
    val id: String,
    val effect: LoadoutAmmunitionEffect,
    val unlimited: Boolean,
    val reloadSeconds: Float,
    val unitDamage: Int,
    val fortressDamage: Int,
    val damageRadius: Float,
)

enum class LoadoutSpellCategory {
    OFFENSIVE,
    DEFENSIVE,
    BATTLEFIELD_CONTROL,
    SUPPORT,
    SIEGE,
}

data class LoadoutSpellDefinition(
    val id: String,
    val goldCost: Int,
    val manaCost: Int,
    val category: LoadoutSpellCategory,
    val special: Boolean,
    val implemented: Boolean,
    val recastableWhileActive: Boolean,
    val activeDurationMilliseconds: Long?,
)

/**
 * Read-only projections for loadout presentation, sourced from the domain's
 * canonical balance definitions.
 */
object LoadoutCatalog {
    val maximumMana: Int = ManaConfiguration.MAXIMUM_MANA
    val maximumSupply: Int = SupplyConfiguration.MAXIMUM_SUPPLY

    val units: List<LoadoutUnitDefinition> = UnitConfigurations.all.map { unit ->
        LoadoutUnitDefinition(
            id = unit.id,
            supplyCost = unit.supplyCost,
            maxHealth = unit.maxHealth,
            speed = unit.speed,
            attackDamage = unit.attackDamage,
            attackRange = unit.attackRange,
            fortressDamage = unit.fortressDamage,
        )
    }

    val ammunition: List<LoadoutAmmunitionDefinition> =
        AmmunitionType.values().map { type ->
            val definition = DomainAmmunitionCatalog.definition(type)
            LoadoutAmmunitionDefinition(
                id = type.name,
                effect = type.effect.toLoadoutEffect(),
                unlimited = type.unlimited,
                reloadSeconds = definition.reload.reloadSeconds,
                unitDamage = definition.damage.unitDamage,
                fortressDamage = definition.damage.fortressDamage,
                damageRadius = definition.damage.unitRadius,
            )
        }

    val spells: List<LoadoutSpellDefinition> = SpellCatalog.all.map { spell ->
        LoadoutSpellDefinition(
            id = spell.type.name,
            goldCost = spell.goldCost,
            manaCost = spell.manaCost,
            category = spell.category.toLoadoutCategory(),
            special = spell.special,
            implemented = spell.isImplemented,
            recastableWhileActive = spell.recastableWhileActive,
            activeDurationMilliseconds = when (val effect = spell.effect) {
                is SpellEffectDefinition.Haste -> effect.durationMilliseconds
                is SpellEffectDefinition.FortressShield, null -> null
            },
        )
    }

    fun findUnit(id: String): LoadoutUnitDefinition? = units.firstOrNull { it.id == id }

    fun findAmmunition(id: String): LoadoutAmmunitionDefinition? =
        ammunition.firstOrNull { it.id == id }

    fun findSpell(id: String): LoadoutSpellDefinition? = spells.firstOrNull { it.id == id }
}

private fun AmmunitionEffect.toLoadoutEffect(): LoadoutAmmunitionEffect = when (this) {
    AmmunitionEffect.DIRECT -> LoadoutAmmunitionEffect.DIRECT
    AmmunitionEffect.EXPLOSIVE -> LoadoutAmmunitionEffect.EXPLOSIVE
    AmmunitionEffect.INCENDIARY -> LoadoutAmmunitionEffect.INCENDIARY
    AmmunitionEffect.PLAGUE -> LoadoutAmmunitionEffect.PLAGUE
}

private fun SpellCategory.toLoadoutCategory(): LoadoutSpellCategory = when (this) {
    SpellCategory.OFFENSIVE -> LoadoutSpellCategory.OFFENSIVE
    SpellCategory.DEFENSIVE -> LoadoutSpellCategory.DEFENSIVE
    SpellCategory.BATTLEFIELD_CONTROL -> LoadoutSpellCategory.BATTLEFIELD_CONTROL
    SpellCategory.SUPPORT -> LoadoutSpellCategory.SUPPORT
    SpellCategory.SIEGE -> LoadoutSpellCategory.SIEGE
}
