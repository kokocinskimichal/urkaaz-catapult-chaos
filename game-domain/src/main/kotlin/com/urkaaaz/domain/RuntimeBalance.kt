package com.urkaaaz.domain

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.FortressHealthOverrides

data class RuntimeBalance(
    val economy: EconomyBalance,
    val fortresses: FortressBalance,
    val catapult: CatapultBalance,
    val match: MatchBalance,
    val physics: PhysicsBalance,
    val wind: WindBalance,
    val combat: CombatBalance,
    val bossCombat: BossCombatBalance,
    val aiProfiles: List<AiProfileBalance>,
    val aiStrategy: AiStrategyBalance,
    val rewards: RewardBalance,
    val campaign: CampaignBalance,
) {
    init {
        require(aiProfiles.map(AiProfileBalance::profileId).distinct().size == aiProfiles.size) {
            "AI balance profile IDs must be unique"
        }
        require(
            aiProfiles.map(AiProfileBalance::profileId).toSet()
                .containsAll(setOf(AiProfileId.CADET, AiProfileId.VETERAN, AiProfileId.ELITE)),
        ) {
            "runtime balance must define the standard AI profiles"
        }
    }

    fun aiProfile(profileId: AiProfileId): AiProfileBalance =
        aiProfiles.firstOrNull { it.profileId == profileId }
            ?: error("unknown AI balance profile: $profileId")
}

data class EconomyBalance(
    val startingGold: Int,
    val startingSupply: Int,
    val maximumSupply: Int,
    val supplyRegenerationIntervalSeconds: Float,
) {
    init {
        require(startingGold >= 0) { "starting gold must not be negative" }
        require(maximumSupply > 0) { "maximum supply must be positive" }
        require(startingSupply in 0..maximumSupply) {
            "starting supply must be within supply bounds"
        }
        require(supplyRegenerationIntervalSeconds.isFinite() &&
            supplyRegenerationIntervalSeconds > 0f
        ) { "supply regeneration interval must be finite and positive" }
    }
}

data class FortressBalance(
    val standardMaximumHealth: Int,
    val demoBlueMaximumHealth: Int,
) {
    init {
        require(standardMaximumHealth > 0) { "standard fortress health must be positive" }
        require(demoBlueMaximumHealth > 0) { "demo fortress health must be positive" }
    }
}

data class CatapultBalance(
    val initialAimDirectionDegrees: Float,
    val initialAimPower: Float,
) {
    init {
        require(initialAimDirectionDegrees.isFinite() &&
            initialAimDirectionDegrees in 0f..180f
        ) { "initial catapult aim direction must be between 0 and 180 degrees" }
        require(initialAimPower.isFinite() && initialAimPower in 0f..100f) {
            "initial catapult aim power must be between 0 and 100"
        }
    }
}

data class MatchBalance(
    val durationMilliseconds: Long,
    val bossEncounterDurationMilliseconds: Long,
) {
    init {
        require(durationMilliseconds > 0L) { "match duration must be positive" }
        require(bossEncounterDurationMilliseconds > 0L) {
            "boss encounter duration must be positive"
        }
    }
}

data class PhysicsBalance(
    val gravityAccelerationY: Float,
    val windAccelerationScale: Float,
) {
    init {
        require(gravityAccelerationY.isFinite() && gravityAccelerationY >= 0f) {
            "gravity acceleration must be finite and non-negative"
        }
        require(windAccelerationScale.isFinite() && windAccelerationScale > 0f) {
            "wind acceleration scale must be finite and positive"
        }
    }
}

data class WindBalance(
    val initialDelaySeconds: Float,
    val transitionSeconds: Float,
    val minimumChangeIntervalSeconds: Float,
    val maximumChangeIntervalSeconds: Float,
    val minimumStrength: Float,
    val maximumStrength: Float,
) {
    init {
        require(initialDelaySeconds.isFinite() && initialDelaySeconds >= 0f) {
            "wind initial delay must be finite and non-negative"
        }
        require(transitionSeconds.isFinite() && transitionSeconds > 0f) {
            "wind transition duration must be finite and positive"
        }
        require(
            minimumChangeIntervalSeconds.isFinite() &&
                minimumChangeIntervalSeconds > 0f &&
                maximumChangeIntervalSeconds.isFinite() &&
                maximumChangeIntervalSeconds >= minimumChangeIntervalSeconds,
        ) { "wind change interval bounds are invalid" }
        require(minimumStrength.isFinite() && minimumStrength >= 0f) {
            "minimum wind strength must be finite and non-negative"
        }
        require(maximumStrength.isFinite() && maximumStrength >= minimumStrength) {
            "maximum wind strength must be finite and no lower than minimum strength"
        }
    }
}

data class DemolisherBalance(
    val explosionDamage: Int,
    val blastRadius: Float,
    val throwDurationSeconds: Float,
    val throwForwardDistance: Float,
) {
    init {
        require(explosionDamage > 0) { "Demolisher explosion damage must be positive" }
        require(blastRadius.isFinite() && blastRadius > 0f) {
            "Demolisher blast radius must be finite and positive"
        }
        require(throwDurationSeconds.isFinite() && throwDurationSeconds > 0f) {
            "Demolisher throw duration must be finite and positive"
        }
        require(throwForwardDistance.isFinite() && throwForwardDistance >= 0f) {
            "Demolisher throw distance must be finite and non-negative"
        }
    }
}

data class CombatBalance(
    val minimumDamageVarianceFactor: Float,
    val maximumDamageVarianceFactor: Float,
    val unitLuckBiasMaximumMagnitude: Float,
    val maximumInitialAttackDelayRatio: Float,
    val gameplaySpeedMultiplier: Float,
    val unitMovementSpeedScale: Float,
    val garrisonMovementSpeedScale: Float,
    val unitInteractionRange: Float,
    val fortressAttackRange: Float,
    val sapperFortressStandoffDistance: Float,
    val sapperContactSpeedMultiplier: Float,
    val meleeAttackStandoffDistance: Float,
    val attackFormationSpacing: Float,
    val meleeSynergyDamagePerAdditionalUnit: Float,
    val maximumMeleeSynergyMultiplier: Float,
    val demolisher: DemolisherBalance,
) {
    init {
        require(minimumDamageVarianceFactor.isFinite() && minimumDamageVarianceFactor > 0f) {
            "minimum damage variance factor must be finite and positive"
        }
        require(
            maximumDamageVarianceFactor.isFinite() &&
                maximumDamageVarianceFactor >= minimumDamageVarianceFactor,
        ) { "maximum damage variance factor must be no lower than the minimum" }
        require(unitLuckBiasMaximumMagnitude.isFinite() && unitLuckBiasMaximumMagnitude >= 0f) {
            "unit luck bias magnitude must be finite and non-negative"
        }
        require(maximumInitialAttackDelayRatio in 0f..1f) {
            "initial attack delay ratio must be between 0 and 1"
        }
        require(gameplaySpeedMultiplier.isFinite() && gameplaySpeedMultiplier in 1f..1.5f) {
            "gameplay speed multiplier must be finite and between 1 and 1.5"
        }
        require(unitMovementSpeedScale.isFinite() && unitMovementSpeedScale >= 0f) {
            "unit movement speed scale must be finite and non-negative"
        }
        require(garrisonMovementSpeedScale.isFinite() && garrisonMovementSpeedScale >= 0f) {
            "garrison movement speed scale must be finite and non-negative"
        }
        require(unitInteractionRange.isFinite() && unitInteractionRange >= 0f) {
            "unit interaction range must be finite and non-negative"
        }
        require(fortressAttackRange.isFinite() && fortressAttackRange >= 0f) {
            "fortress attack range must be finite and non-negative"
        }
        require(sapperFortressStandoffDistance.isFinite() && sapperFortressStandoffDistance >= 0f) {
            "Sapper fortress standoff distance must be finite and non-negative"
        }
        require(sapperContactSpeedMultiplier.isFinite() && sapperContactSpeedMultiplier in 0f..1f) {
            "Sapper contact speed multiplier must be between 0 and 1"
        }
        require(meleeAttackStandoffDistance.isFinite() && meleeAttackStandoffDistance >= 0f) {
            "melee attack standoff distance must be finite and non-negative"
        }
        require(attackFormationSpacing.isFinite() && attackFormationSpacing >= 0f) {
            "attack formation spacing must be finite and non-negative"
        }
        require(
            meleeSynergyDamagePerAdditionalUnit.isFinite() &&
                meleeSynergyDamagePerAdditionalUnit >= 0f,
        ) { "combat synergy increment must be finite and non-negative" }
        require(maximumMeleeSynergyMultiplier.isFinite() && maximumMeleeSynergyMultiplier >= 1f) {
            "maximum combat synergy multiplier must be finite and at least 1"
        }
    }
}

data class BossCombatBalance(
    val largerGroupTargetChance: Float,
    val maximumDistanceFalloff: Float,
) {
    init {
        require(largerGroupTargetChance in 0f..1f) {
            "boss larger-group target chance must be between 0 and 1"
        }
        require(maximumDistanceFalloff in 0f..1f) {
            "boss maximum distance falloff must be between 0 and 1"
        }
    }
}

data class AiProfileBalance(
    val profileId: AiProfileId,
    val angleErrorDegrees: Float,
    val powerError: Float,
    val reactionIntervalMilliseconds: Long,
    val unitRecruitmentIntervalMilliseconds: Long,
    val progressionErrorReduction: Float,
    val progressionReactionReduction: Float,
) {
    init {
        require(angleErrorDegrees.isFinite() && angleErrorDegrees >= 0f) {
            "AI angle error must be finite and non-negative"
        }
        require(powerError.isFinite() && powerError >= 0f) {
            "AI power error must be finite and non-negative"
        }
        require(reactionIntervalMilliseconds > 0L) {
            "AI reaction interval must be positive"
        }
        require(unitRecruitmentIntervalMilliseconds >= 0L) {
            "AI unit recruitment interval must be non-negative"
        }
        require(progressionErrorReduction in 0f..1f) {
            "AI progression error reduction must be between 0 and 1"
        }
        require(progressionReactionReduction in 0f..1f) {
            "AI progression reaction reduction must be between 0 and 1"
        }
    }
}

data class AiStrategyBalance(
    val aimAngleOffsetDegrees: Float,
    val horizontalDistancePerPowerPercent: Float,
    val specialSpellStockPerMatch: Int,
    val ammunitionScoring: AiAmmunitionScoringBalance,
    val unitScoring: AiUnitScoringBalance,
    val waveDispatch: AiWaveDispatchBalance,
) {
    init {
        require(aimAngleOffsetDegrees.isFinite()) { "AI aim angle offset must be finite" }
        require(specialSpellStockPerMatch >= 0) {
            "AI special spell stock must not be negative"
        }
        require(
            horizontalDistancePerPowerPercent.isFinite() &&
                horizontalDistancePerPowerPercent > 0f,
        ) { "AI horizontal distance per power percent must be finite and positive" }
    }
}

data class AiAmmunitionScoringBalance(
    val situationalFortressDamageWeight: Float,
    val situationalUnitDamageWeight: Float,
    val situationalFortressRadiusWeight: Float,
    val situationalUnitRadiusWeight: Float,
    val counterplayUnitDamageWeight: Float,
    val counterplayUnitRadiusWeight: Float,
    val counterplayFortressDamageWeight: Float,
    val counterplayFortressRadiusWeight: Float,
) {
    init {
        require(
            listOf(
                situationalFortressDamageWeight,
                situationalUnitDamageWeight,
                situationalFortressRadiusWeight,
                situationalUnitRadiusWeight,
                counterplayUnitDamageWeight,
                counterplayUnitRadiusWeight,
                counterplayFortressDamageWeight,
                counterplayFortressRadiusWeight,
            ).all { it.isFinite() && it >= 0f },
        ) { "AI ammunition scoring weights must be finite and non-negative" }
    }
}

data class AiUnitScoringBalance(
    val sapperPriorityScore: Float,
    val demolisherPriorityScore: Float,
    val attackDamageWeight: Float,
    val speedWeight: Float,
    val defenderVsSapperRoleBonus: Float,
    val rangedVsRangedRoleBonus: Float,
    val rangedVsMeleeRoleBonus: Float,
    val supplyCostPenaltyWeight: Float,
) {
    init {
        require(
            listOf(
                sapperPriorityScore,
                demolisherPriorityScore,
                attackDamageWeight,
                speedWeight,
                defenderVsSapperRoleBonus,
                rangedVsRangedRoleBonus,
                rangedVsMeleeRoleBonus,
                supplyCostPenaltyWeight,
            ).all { it.isFinite() && it >= 0f },
        ) { "AI unit scoring weights must be finite and non-negative" }
    }
}

data class AiWaveDispatchBalance(
    val situationalMinimumReadyUnits: Int,
    val counterplayMinimumReadyUnits: Int,
    val counterplayMinimumReadyUnitsAgainstMovingEnemies: Int,
    val lowFortressHealthFraction: Float,
) {
    init {
        require(situationalMinimumReadyUnits > 0) {
            "situational wave minimum must be positive"
        }
        require(counterplayMinimumReadyUnits > 0) {
            "counterplay wave minimum must be positive"
        }
        require(counterplayMinimumReadyUnitsAgainstMovingEnemies > 0) {
            "moving-enemy wave minimum must be positive"
        }
        require(lowFortressHealthFraction in 0f..1f) {
            "low-fortress-health fraction must be between 0 and 1"
        }
    }
}

data class RewardBalance(
    val winGold: Long,
    val directEnemyUnitHitGold: Long,
    val fastWinGold: Long,
    val fastWinThresholdMilliseconds: Long,
    val untouchedFortressGold: Long,
    val fortressHitStreakGoldPerHitBeyondFirst: Long,
    val fortressHitStreakStartHits: Int,
) {
    init {
        require(winGold >= 0L) { "win gold must not be negative" }
        require(directEnemyUnitHitGold >= 0L) { "direct-hit gold must not be negative" }
        require(fastWinGold >= 0L) { "fast-win gold must not be negative" }
        require(fastWinThresholdMilliseconds > 0L) { "fast-win threshold must be positive" }
        require(untouchedFortressGold >= 0L) { "untouched-fortress gold must not be negative" }
        require(fortressHitStreakGoldPerHitBeyondFirst >= 0L) {
            "fortress streak reward increment must not be negative"
        }
        require(fortressHitStreakStartHits >= 2) {
            "fortress streak reward must start at hit two or later"
        }
    }
}

data class CampaignUnitAvailability(
    val unitId: String,
    val availableFromLevel: Int?,
) {
    init {
        require(unitId in UnitIds.all) { "unknown campaign unit ID: $unitId" }
        require(availableFromLevel == null || availableFromLevel in 1..100) {
            "campaign unit availability level must be null or in 1..100"
        }
    }
}

data class CampaignAmmunitionAvailability(
    val ammunitionType: AmmunitionType,
    val availableFromLevel: Int?,
) {
    init {
        require(availableFromLevel == null || availableFromLevel in 1..100) {
            "campaign ammunition availability level must be null or in 1..100"
        }
    }
}

data class CampaignSpellAvailability(
    val spellType: SpellType,
    val availableFromLevel: Int?,
) {
    init {
        require(availableFromLevel == null || availableFromLevel in 1..100) {
            "campaign spell availability level must be null or in 1..100"
        }
    }
}

data class CampaignContentAvailability(
    val units: List<CampaignUnitAvailability>,
    val ammunition: List<CampaignAmmunitionAvailability>,
    val spells: List<CampaignSpellAvailability>,
) {
    init {
        val unitIds = units.map(CampaignUnitAvailability::unitId)
        require(unitIds.size == unitIds.toSet().size && unitIds.toSet() == UnitIds.all) {
            "campaign content availability must define every unit ID exactly once"
        }
        val ammunitionTypes = ammunition.map(CampaignAmmunitionAvailability::ammunitionType)
        require(
            ammunitionTypes.size == ammunitionTypes.toSet().size &&
                ammunitionTypes.toSet() == AmmunitionType.entries.toSet(),
        ) {
            "campaign content availability must define every ammunition type exactly once"
        }
        val spellTypes = spells.map(CampaignSpellAvailability::spellType)
        require(
            spellTypes.size == spellTypes.toSet().size &&
                spellTypes.toSet() == SpellType.entries.toSet(),
        ) {
            "campaign content availability must define every spell type exactly once"
        }
    }

    fun availableUnitsAt(level: Int): Set<String> {
        require(level in 1..100) { "campaign availability level must be in 1..100" }
        return units.asSequence()
            .filter { it.availableFromLevel?.let { availableFrom -> level >= availableFrom } == true }
            .mapTo(linkedSetOf(), CampaignUnitAvailability::unitId)
    }

    fun availableAmmunitionAt(level: Int): Set<AmmunitionType> {
        require(level in 1..100) { "campaign availability level must be in 1..100" }
        return ammunition.asSequence()
            .filter { it.availableFromLevel?.let { availableFrom -> level >= availableFrom } == true }
            .mapTo(linkedSetOf(), CampaignAmmunitionAvailability::ammunitionType)
    }

    fun availableSpellsAt(level: Int): Set<SpellType> {
        require(level in 1..100) { "campaign availability level must be in 1..100" }
        return spells.asSequence()
            .filter { it.availableFromLevel?.let { availableFrom -> level >= availableFrom } == true }
            .mapTo(linkedSetOf(), CampaignSpellAvailability::spellType)
    }

    fun availableFromLevel(spellType: SpellType): Int? =
        spells.single { it.spellType == spellType }.availableFromLevel
}

data class CampaignBalance(
    val initialWindStrength: Float,
    val initialWindStrengthStep: Float,
    val initialWindStrengthBands: Int,
    val demoInitialWindStrength: Float,
    val lives: LifeBalance,
    val aiDifficultyBands: List<CampaignAiDifficultyBand>,
    val contentAvailability: CampaignContentAvailability,
    val fortressHealthOverrides: List<CampaignFortressHealthOverride> = emptyList(),
) {
    init {
        require(initialWindStrength.isFinite() && initialWindStrength >= 0f) {
            "initial wind strength must be finite and non-negative"
        }
        require(initialWindStrengthStep.isFinite() && initialWindStrengthStep >= 0f) {
            "initial wind strength step must be finite and non-negative"
        }
        require(initialWindStrengthBands > 0) { "initial wind strength bands must be positive" }
        require(demoInitialWindStrength.isFinite() && demoInitialWindStrength >= 0f) {
            "demo wind strength must be finite and non-negative"
        }
        require(aiDifficultyBands.isNotEmpty()) { "campaign AI difficulty bands must not be empty" }
        val orderedBands = aiDifficultyBands.sortedBy(CampaignAiDifficultyBand::firstLevel)
        require(orderedBands.first().firstLevel == 1) {
            "campaign AI difficulty bands must start at level 1"
        }
        require(orderedBands.zipWithNext().all { (first, second) ->
            first.lastLevel + 1 == second.firstLevel
        }) {
            "campaign AI difficulty bands must be contiguous and non-overlapping"
        }
        require(
            fortressHealthOverrides.map(CampaignFortressHealthOverride::level).distinct().size ==
                fortressHealthOverrides.size,
        ) {
            "campaign fortress health override levels must be unique"
        }
    }

    fun initialWindStrength(level: Int): Float =
        initialWindStrength +
            ((level - 1).coerceAtLeast(0) % initialWindStrengthBands) * initialWindStrengthStep

    fun aiDifficultyFor(level: Int): CampaignAiDifficultyBand =
        aiDifficultyBands.firstOrNull { level in it.firstLevel..it.lastLevel }
            ?: error("campaign AI difficulty is not configured for level $level")

    fun fortressHealthOverrideFor(level: Int): CampaignFortressHealthOverride? =
        fortressHealthOverrides.firstOrNull { it.level == level }
}

data class CampaignFortressHealthOverride(
    val level: Int,
    val blueMaximumHealth: Int? = null,
    val redMaximumHealth: Int? = null,
) {
    init {
        require(level in 1..100) { "campaign fortress health override level must be in 1..100" }
        require(blueMaximumHealth != null || redMaximumHealth != null) {
            "campaign fortress health override must set BLUE or RED"
        }
        require(blueMaximumHealth == null || blueMaximumHealth > 0) {
            "BLUE fortress maximum health override must be positive"
        }
        require(redMaximumHealth == null || redMaximumHealth > 0) {
            "RED fortress maximum health override must be positive"
        }
    }

    fun toMatchOverride() = FortressHealthOverrides(
        blueMaximumHealth = blueMaximumHealth,
        redMaximumHealth = redMaximumHealth,
    )
}

data class LifeBalance(
    val initialLives: Int,
    val maximumLives: Int,
    val regenerationIntervalMilliseconds: Long,
) {
    init {
        require(maximumLives > 0) { "maximum lives must be positive" }
        require(initialLives in 0..maximumLives) {
            "initial lives must be within life bounds"
        }
        require(regenerationIntervalMilliseconds > 0L) {
            "life regeneration interval must be positive"
        }
    }
}

data class CampaignAiDifficultyBand(
    val firstLevel: Int,
    val lastLevel: Int,
    val profileId: AiProfileId,
    val progressionStartPercent: Int,
    val progressionEndPercent: Int,
) {
    init {
        require(firstLevel > 0 && lastLevel >= firstLevel) {
            "campaign AI difficulty level range is invalid"
        }
        require(progressionStartPercent in 0..100 && progressionEndPercent in 0..100) {
            "campaign AI progression percentages must be between 0 and 100"
        }
    }

    fun progressionPercent(level: Int): Int {
        require(level in firstLevel..lastLevel) {
            "level must be within the AI difficulty band"
        }
        if (firstLevel == lastLevel) return progressionEndPercent
        return progressionStartPercent +
            ((level - firstLevel) * (progressionEndPercent - progressionStartPercent)) /
            (lastLevel - firstLevel)
    }
}
