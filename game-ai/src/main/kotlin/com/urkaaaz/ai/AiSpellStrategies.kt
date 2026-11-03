package com.urkaaaz.ai

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.domain.SpellEffectDefinition
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitStatus

data class AiSpellCandidate(
    val spellType: SpellType,
    val target: EntityId? = null,
    val utilityScore: Float,
) {
    init {
        require(utilityScore.isFinite() && utilityScore > 0f) {
            "AI spell utility score must be finite and positive"
        }
    }
}

interface AiSpellUseStrategy {
    val spellType: SpellType

    fun decide(context: AiStrategyContext): AiSpellCandidate?
}

fun standardAiSpellUseStrategies(): List<AiSpellUseStrategy> = listOf(
    FortressShieldAiSpellStrategy,
    RunicBastionAiSpellStrategy,
    HasteAiSpellStrategy,
)

private object FortressShieldAiSpellStrategy : AiSpellUseStrategy {
    override val spellType = SpellType.FORTRESS_SHIELD

    override fun decide(context: AiStrategyContext): AiSpellCandidate? {
        val fortress = context.snapshot.fortresses.firstOrNull { it.team == context.team }
            ?: return null
        if (fortress.maxHealth <= 0) return null
        val effect = context.balance.spells.definition(spellType).effect
            as? SpellEffectDefinition.FortressShield
            ?: return null
        if (fortress.health <= 0 || fortress.shieldHealth != null) return null
        val healthFraction = fortress.health.toFloat() / fortress.maxHealth
        if (
            healthFraction >
            context.balance.runtime.aiStrategy.waveDispatch.lowFortressHealthFraction
        ) {
            return null
        }
        val manaCost = context.balance.spells.definition(spellType).manaCost
        val protectedHealth = minOf(effect.hitPoints, fortress.maxHealth - fortress.health)
            .coerceAtLeast(0)
        return AiSpellCandidate(
            spellType = spellType,
            utilityScore = protectedHealth.toFloat() / fortress.maxHealth / manaCost,
        )
    }
}

private object RunicBastionAiSpellStrategy : AiSpellUseStrategy {
    override val spellType = SpellType.RUNIC_BASTION

    override fun decide(context: AiStrategyContext): AiSpellCandidate? {
        if (context.snapshot.runicBastions.any { it.team == context.team }) return null
        val fortress = context.snapshot.fortresses.firstOrNull { it.team == context.team }
            ?: return null
        val activeEnemies = context.snapshot.units.filter {
            it.team != context.team && it.health > 0
        }
        if (activeEnemies.isEmpty()) return null
        val attackers = activeEnemies.count { it.targetId == fortress.entityId }
        if (attackers == 0) return null
        val effect = context.balance.spells.definition(spellType).effect
            as? SpellEffectDefinition.RunicBastion
            ?: return null
        return AiSpellCandidate(
            spellType = spellType,
            utilityScore = attackers.toFloat() / activeEnemies.size /
                context.balance.spells.definition(spellType).manaCost *
                effect.hitPoints,
        )
    }
}

private object HasteAiSpellStrategy : AiSpellUseStrategy {
    override val spellType = SpellType.HASTE

    override fun decide(context: AiStrategyContext): AiSpellCandidate? {
        if (context.snapshot.activeSpells.any {
                it.team == context.team && it.spellType == spellType.name
            }
        ) {
            return null
        }
        val alliedUnits = context.snapshot.units.filter {
            it.team == context.team && it.health > 0
        }
        if (alliedUnits.isEmpty()) return null
        val advancingUnits = alliedUnits.filter { unit ->
            unit.moving && UnitStatus.HASTED.name !in unit.statuses
        }
        if (advancingUnits.isEmpty()) return null
        val effect = context.balance.spells.definition(spellType).effect
            as? SpellEffectDefinition.Haste
            ?: return null
        val utility = advancingUnits.size.toFloat() / alliedUnits.size *
            (effect.movementSpeedMultiplier - 1f) *
            effect.durationMilliseconds.toFloat() / 1_000f
        return AiSpellCandidate(
            spellType = spellType,
            utilityScore = utility / context.balance.spells.definition(spellType).manaCost,
        )
    }
}
