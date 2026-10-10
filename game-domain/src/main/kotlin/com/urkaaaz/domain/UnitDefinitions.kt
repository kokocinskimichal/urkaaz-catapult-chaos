package com.urkaaaz.domain

/**
 * Unit-specific behavior backed by central balance configuration.
 */
sealed interface UnitDefinition {
    val configuration: UnitConfiguration
    val id: String get() = configuration.id
    val supplyCost: Int get() = configuration.supplyCost
    val maxHealth: Int get() = configuration.maxHealth
    val speed: Float get() = configuration.speed
    val attackDamage: Int get() = configuration.attackDamage
    val attackCooldownSeconds: Float get() = configuration.attackCooldownSeconds
    val attackRange: Float get() = configuration.attackRange
    val fortressDamage: Int get() = configuration.fortressDamage
    val attacksFromRange: Boolean
    val detonatesOnContact: Boolean
    val returnsAfterFortressAttack: Boolean

    fun incomingDamage(damage: Int): Int = damage
}

class DefenderUnit(
    override val configuration: UnitConfiguration = UnitConfigurations.DEFENDER,
) : UnitDefinition {
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterFortressAttack = false
}

class SapperUnit(
    override val configuration: UnitConfiguration = UnitConfigurations.SAPPER,
) : UnitDefinition {
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterFortressAttack = true

    override fun incomingDamage(damage: Int): Int =
        (damage * 0.45f).toInt().coerceAtLeast(1)
}

class DemolisherUnit(
    override val configuration: UnitConfiguration = UnitConfigurations.DEMOLISHER,
) : UnitDefinition {
    override val attacksFromRange = false
    override val detonatesOnContact = true
    override val returnsAfterFortressAttack = false
}

class RagingBoarUnit(
    override val configuration: UnitConfiguration = UnitConfigurations.RAGING_BOAR,
) : UnitDefinition {
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterFortressAttack = false
}

class SlingmasterUnit(
    override val configuration: UnitConfiguration = UnitConfigurations.SLINGMASTER,
) : UnitDefinition {
    override val attacksFromRange = true
    override val detonatesOnContact = false
    override val returnsAfterFortressAttack = false
}

object UnitFactory {
    fun create(configuration: UnitConfiguration): UnitDefinition = when (configuration.id) {
        UnitConfigurations.DEFENDER.id -> DefenderUnit(configuration)
        UnitConfigurations.SAPPER.id -> SapperUnit(configuration)
        UnitConfigurations.DEMOLISHER.id -> DemolisherUnit(configuration)
        UnitConfigurations.RAGING_BOAR.id -> RagingBoarUnit(configuration)
        UnitConfigurations.SLINGMASTER.id -> SlingmasterUnit(configuration)
        else -> error("unknown unit configuration: ${configuration.id}")
    }

    fun create(id: String): UnitDefinition =
        UnitConfigurations.all.firstOrNull { it.id == id }?.let(::create)
            ?: error("unknown unit definition: $id")

    fun all(): Set<UnitDefinition> = UnitConfigurations.all.map(::create).toSet()

    fun byId(id: String): UnitDefinition = create(id)

    fun defender(): UnitDefinition = create(UnitConfigurations.DEFENDER)
    fun sapper(): UnitDefinition = create(UnitConfigurations.SAPPER)
    fun demolisher(): UnitDefinition = create(UnitConfigurations.DEMOLISHER)
    fun ragingBoar(): UnitDefinition = create(UnitConfigurations.RAGING_BOAR)
    fun slingmaster(): UnitDefinition = create(UnitConfigurations.SLINGMASTER)
}
