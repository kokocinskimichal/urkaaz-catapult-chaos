package com.urkaaaz.application.loadout

import com.urkaaaz.domain.AmmunitionEffect
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.SpellCategory
import com.urkaaaz.domain.SpellEffectDefinition
import com.urkaaaz.application.balance.GameBalanceRepository

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
    val shopCostGold: Int?,
    val initialSpeedMultiplier: Float,
    val windResponse: Float,
    val gravityResponse: Float,
    val drag: Float,
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
class LoadoutCatalog(private val balance: GameBalanceConfig) {
    val maximumMana: Int = balance.mana.maximumMana
    val maximumSupply: Int = balance.runtime.economy.maximumSupply
    val demoBlueFortressMaximumHealth: Int =
        balance.runtime.fortresses.demoBlueMaximumHealth
    val initialAimDirectionDegrees: Float =
        balance.runtime.catapult.initialAimDirectionDegrees
    val initialAimPower: Float = balance.runtime.catapult.initialAimPower
    val gravityAccelerationY: Float = balance.runtime.physics.gravityAccelerationY
    val windAccelerationScale: Float = balance.runtime.physics.windAccelerationScale

    val units: List<LoadoutUnitDefinition> = balance.units.all.map { unit ->
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

    val ammunition: List<LoadoutAmmunitionDefinition> = balance.ammunition.all.map { definition ->
            LoadoutAmmunitionDefinition(
                id = definition.type.name,
                effect = definition.effect.toLoadoutEffect(),
                unlimited = definition.unlimited,
                shopCostGold = definition.shopCostGold,
                initialSpeedMultiplier = definition.flight.initialSpeedMultiplier,
                windResponse = definition.flight.windResponse,
                gravityResponse = definition.flight.gravityResponse,
                drag = definition.flight.drag,
                reloadSeconds = definition.reload.reloadSeconds,
                unitDamage = definition.damage.unitDamage,
                fortressDamage = definition.damage.fortressDamage,
                damageRadius = definition.damage.unitRadius,
            )
        }

    val spells: List<LoadoutSpellDefinition> = balance.spells.all.map { spell ->
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
                is SpellEffectDefinition.FortressShield,
                is SpellEffectDefinition.RunicBastion,
                null -> null
            },
        )
    }

    fun findUnit(id: String): LoadoutUnitDefinition? = units.firstOrNull { it.id == id }

    fun findAmmunition(id: String): LoadoutAmmunitionDefinition? =
        ammunition.firstOrNull { it.id == id }

    fun findSpell(id: String): LoadoutSpellDefinition? = spells.firstOrNull { it.id == id }

    companion object {
        fun bundled(): LoadoutCatalog =
            LoadoutCatalog(GameBalanceRepository.bundled.requireConfig())
    }
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
