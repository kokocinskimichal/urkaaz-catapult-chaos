package com.urkaaaz.domain

/** Central balance configuration for all recruitable units. */
data class UnitConfiguration(
    val id: String,
    val supplyCost: Int,
    val maxHealth: Int,
    val speed: Float,
    val attackDamage: Int,
    val attackCooldownSeconds: Float,
    val attackRange: Float,
    val fortressDamage: Int,
) {
    init {
        require(id.isNotBlank()) { "unit id must not be blank" }
        require(supplyCost >= 0) { "unit supply cost must not be negative" }
        require(maxHealth > 0) { "unit max health must be positive" }
        require(speed >= 0f) { "unit speed must not be negative" }
        require(attackDamage >= 0) { "unit attack damage must not be negative" }
        require(attackCooldownSeconds > 0f) {
            "unit attack cooldown must be positive"
        }
        require(attackRange >= 0f) { "unit attack range must not be negative" }
        require(fortressDamage >= 0) { "unit fortress damage must not be negative" }
    }
}

object UnitConfigurations {
    val SAPPER = UnitConfiguration(
        id = "SAPPER",
        supplyCost = 10,
        maxHealth = 60,
        speed = 260f,
        attackDamage = 180,
        attackCooldownSeconds = 1.75f,
        attackRange = 0f,
        fortressDamage = 180,
    )
    val DEFENDER = UnitConfiguration(
        id = "DEFENDER",
        supplyCost = 5,
        maxHealth = 260,
        speed = 150f,
        attackDamage = 30,
        attackCooldownSeconds = 0.8f,
        attackRange = 0f,
        fortressDamage = 30,
    )
    val DEMOLISHER = UnitConfiguration(
        id = "DEMOLISHER",
        supplyCost = 10,
        maxHealth = 70,
        speed = 110f,
        attackDamage = 0,
        attackCooldownSeconds = 0.8f,
        attackRange = 0f,
        fortressDamage = 300,
    )
    val RAGING_BOAR = UnitConfiguration(
        id = "RAGING_BOAR",
        supplyCost = 10,
        maxHealth = 420,
        speed = 240f,
        attackDamage = 52,
        attackCooldownSeconds = 0.8f,
        attackRange = 0f,
        fortressDamage = 52,
    )
    val SLINGMASTER = UnitConfiguration(
        id = "SLINGMASTER",
        supplyCost = 5,
        maxHealth = 75,
        speed = 120f,
        attackDamage = 20,
        attackCooldownSeconds = 1.2f,
        attackRange = 260f,
        fortressDamage = 20,
    )

    val all: List<UnitConfiguration> = listOf(
        SAPPER,
        DEFENDER,
        DEMOLISHER,
        RAGING_BOAR,
        SLINGMASTER,
    )
}
