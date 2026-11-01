package com.urkaaaz.domain

/** Stable serialized identifiers for recruitable unit types. */
object UnitIds {
    const val SAPPER = "SAPPER"
    const val DEFENDER = "DEFENDER"
    const val DEMOLISHER = "DEMOLISHER"
    const val RAGING_BOAR = "RAGING_BOAR"
    const val SLINGMASTER = "SLINGMASTER"

    val all: Set<String> = setOf(SAPPER, DEFENDER, DEMOLISHER, RAGING_BOAR, SLINGMASTER)
}

/** Balance configuration for a recruitable unit. */
data class UnitConfiguration(
    val id: String,
    val supplyCost: Int,
    val maxHealth: Int,
    val speed: Float,
    val attackDamage: Int,
    val attackCooldownSeconds: Float,
    val attackRange: Float,
    val fortressDamage: Int,
    val incomingDamageMultiplier: Float,
    val contactMovementSpeedMultiplier: Float,
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
        require(incomingDamageMultiplier.isFinite() && incomingDamageMultiplier > 0f) {
            "unit incoming damage multiplier must be finite and positive"
        }
        require(contactMovementSpeedMultiplier.isFinite() &&
            contactMovementSpeedMultiplier in 0f..1f
        ) { "unit contact movement speed multiplier must be between 0 and 1" }
    }

}

class UnitCatalog(configurations: List<UnitConfiguration>) {
    val all: List<UnitConfiguration> = configurations.toList()
    private val byId = all.associateBy(UnitConfiguration::id)

    init {
        require(byId.size == all.size) { "unit configuration IDs must be unique" }
        require(byId.keys == UnitIds.all) {
            "unit catalog must define every stable unit ID exactly once"
        }
    }

    fun configuration(id: String): UnitConfiguration =
        byId[id] ?: error("unknown unit configuration: $id")

    fun find(id: String): UnitConfiguration? = byId[id]
}
