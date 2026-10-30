package com.urkaaaz.domain

import com.urkaaaz.contracts.BossArchetype

enum class BossMovementKind {
    STATIC,
    ADVANCE,
}

enum class BossAttackTarget {
    UNIT,
    FORTRESS_GATE,
}

data class BossCollisionBalance(
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f && height > 0f) { "boss collision dimensions must be positive" }
    }
}

data class BossMovementBalance(
    val kind: BossMovementKind,
    val speedWorldUnitsPerSecond: Float,
) {
    init {
        require(speedWorldUnitsPerSecond >= 0f) { "boss movement speed must not be negative" }
        require(
            (kind == BossMovementKind.STATIC && speedWorldUnitsPerSecond == 0f) ||
                (kind == BossMovementKind.ADVANCE && speedWorldUnitsPerSecond > 0f),
        ) { "boss movement kind and speed do not match" }
    }
}

data class BossAttackBalance(
    val target: BossAttackTarget,
    val damage: Int,
    val minimumDamage: Int,
    val rangeWorldUnits: Float,
    val cooldownSeconds: Float,
    val initialCooldownSeconds: Float,
) {
    init {
        require(damage > 0) { "boss attack damage must be positive" }
        require(minimumDamage in 1..damage) { "boss minimum damage must be within attack damage" }
        require(rangeWorldUnits >= 0f) { "boss attack range must not be negative" }
        require(cooldownSeconds > 0f) { "boss attack cooldown must be positive" }
        require(initialCooldownSeconds >= 0f) { "boss initial cooldown must not be negative" }
    }
}

sealed interface BossSpecialAbility {
    data class Enrage(
        val healthFractionThreshold: Float,
        val speedMultiplier: Float,
        val attackCooldownMultiplier: Float,
    ) : BossSpecialAbility {
        init {
            require(healthFractionThreshold in 0f..1f) {
                "enrage health threshold must be between 0 and 1"
            }
            require(speedMultiplier >= 1f) { "enrage speed multiplier must be at least 1" }
            require(attackCooldownMultiplier in 0f..1f) {
                "enrage cooldown multiplier must be between 0 and 1"
            }
        }
    }
}

data class BossBalanceProfile(
    val id: String,
    val presentationKey: String,
    val archetype: BossArchetype,
    val maxHealth: Int,
    val collision: BossCollisionBalance,
    val movement: BossMovementBalance,
    val attacks: List<BossAttackBalance>,
    val specialAbilities: List<BossSpecialAbility>,
    val immuneAmmunitionTypes: Set<String> = emptySet(),
) {
    init {
        require(id.matches(PROFILE_ID)) { "invalid boss profile ID: '$id'" }
        require(presentationKey.matches(PRESENTATION_KEY)) {
            "invalid boss presentation key: '$presentationKey'"
        }
        require(maxHealth > 0) { "boss max health must be positive" }
        require(attacks.isNotEmpty()) { "boss profile must define at least one attack" }
        require(attacks.map(BossAttackBalance::target).distinct().size == attacks.size) {
            "boss attack targets must be unique"
        }
        require(specialAbilities.filterIsInstance<BossSpecialAbility.Enrage>().size <= 1) {
            "boss profile may define at most one enrage ability"
        }
        require(immuneAmmunitionTypes.all { it.matches(AMMUNITION_ID) }) {
            "boss immunity contains an invalid ammunition identifier"
        }
        when (archetype) {
            BossArchetype.CENTRAL_OBJECTIVE -> {
                require(movement.kind == BossMovementKind.STATIC) {
                    "central objective must be static"
                }
                require(attacks.all { it.target == BossAttackTarget.UNIT }) {
                    "central objective may only target units"
                }
            }
            BossArchetype.MOBILE_SIEGE_BOSS -> {
                require(movement.kind == BossMovementKind.ADVANCE) {
                    "mobile boss must advance"
                }
                require(attacks.any { it.target == BossAttackTarget.FORTRESS_GATE }) {
                    "mobile boss must define a fortress gate attack"
                }
            }
        }
    }

    fun enrage(): BossSpecialAbility.Enrage? =
        specialAbilities.filterIsInstance<BossSpecialAbility.Enrage>().singleOrNull()

    companion object {
        private val PROFILE_ID = Regex("boss\\.[a-z0-9-]+\\.level-[0-9]{2}")
        private val PRESENTATION_KEY = Regex("[a-z][a-z0-9_]*")
        private val AMMUNITION_ID = Regex("[A-Z][A-Z0-9_]*")
    }
}

data class GameBalanceConfig(
    val schemaVersion: Int,
    val balanceRevision: String,
    val bosses: List<BossBalanceProfile>,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "unsupported game balance schema version: $schemaVersion"
        }
        require(balanceRevision.isNotBlank()) { "balance revision must not be blank" }
        require(bosses.isNotEmpty()) { "game balance must define boss profiles" }
        require(bosses.map(BossBalanceProfile::id).distinct().size == bosses.size) {
            "boss profile IDs must be unique"
        }
    }

    fun bossProfile(id: String): BossBalanceProfile =
        bosses.firstOrNull { it.id == id } ?: error("unknown boss profile: $id")

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
