package com.urkaaaz.contracts

enum class BossArchetype {
    CENTRAL_OBJECTIVE,
    MOBILE_SIEGE_BOSS,
}

enum class BossPhase {
    IDLE,
    MARCHING,
    ENGAGED,
    ENRAGED,
    AT_GATE,
    DEFEATED,
}

enum class BossVictoryRule {
    CENTRAL_DAMAGE_RACE,
    DEFEAT_BOSS,
}

data class BossEncounterConfig(
    val profileId: String,
    val spawnXRatio: Float,
    val victoryRule: BossVictoryRule = BossVictoryRule.DEFEAT_BOSS,
) {
    init {
        require(profileId.isNotBlank()) { "boss profile ID must not be blank" }
        require(spawnXRatio in 0f..1f) { "boss spawn X ratio must be between 0 and 1" }
    }
}

data class BossSnapshot(
    val entityId: EntityId,
    val profileId: String,
    val presentationKey: String,
    val archetype: BossArchetype,
    val phase: BossPhase,
    val x: Float,
    val groundY: Float,
    val collisionWidth: Float,
    val collisionHeight: Float,
    val health: Int,
    val maxHealth: Int,
    val blueDamage: Int = 0,
    val redDamage: Int = 0,
) {
    init {
        require(profileId.isNotBlank()) { "boss profile ID must not be blank" }
        require(presentationKey.isNotBlank()) { "boss presentation key must not be blank" }
        require(collisionWidth > 0f && collisionHeight > 0f) {
            "boss collision dimensions must be positive"
        }
        require(maxHealth > 0) { "boss max health must be positive" }
        require(health in 0..maxHealth) { "boss health must be within max health" }
        require(blueDamage >= 0 && redDamage >= 0) { "boss damage totals must not be negative" }
    }
}
