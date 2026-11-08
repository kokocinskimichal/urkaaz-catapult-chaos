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
    val attackType: AttackType
    val attacksFromRange: Boolean
    val detonatesOnContact: Boolean
    val returnsAfterSiegeMission: Boolean
    val participatesInCombatSynergy: Boolean
    val statusImmunities: Set<UnitStatus>
        get() = emptySet()
    val statusResistance: Map<UnitStatus, Float>
        get() = emptyMap()

    fun incomingDamage(damage: Int): Int = damage
}

class DefenderUnit(override val configuration: UnitConfiguration) : UnitDefinition {
    override val attackType = AttackType.MELEE
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterSiegeMission = false
    override val participatesInCombatSynergy = true
}

class SapperUnit(override val configuration: UnitConfiguration) : UnitDefinition {
    override val attackType = AttackType.SIEGE_MISSION
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterSiegeMission = true
    override val participatesInCombatSynergy = false

    override fun incomingDamage(damage: Int): Int =
        (damage * configuration.incomingDamageMultiplier).toInt().coerceAtLeast(1)
}

class DemolisherUnit(override val configuration: UnitConfiguration) : UnitDefinition {
    override val attackType = AttackType.CONTACT_EXPLOSIVE
    override val attacksFromRange = false
    override val detonatesOnContact = true
    override val returnsAfterSiegeMission = false
    override val participatesInCombatSynergy = false
}

class RagingBoarUnit(override val configuration: UnitConfiguration) : UnitDefinition {
    override val attackType = AttackType.MELEE
    override val attacksFromRange = false
    override val detonatesOnContact = false
    override val returnsAfterSiegeMission = false
    override val participatesInCombatSynergy = true
}

class SlingmasterUnit(override val configuration: UnitConfiguration) : UnitDefinition {
    override val attackType = AttackType.RANGED
    override val attacksFromRange = true
    override val detonatesOnContact = false
    override val returnsAfterSiegeMission = false
    override val participatesInCombatSynergy = true
}

object UnitFactory {
    fun create(configuration: UnitConfiguration): UnitDefinition = when (configuration.id) {
        UnitIds.DEFENDER -> DefenderUnit(configuration)
        UnitIds.SAPPER -> SapperUnit(configuration)
        UnitIds.DEMOLISHER -> DemolisherUnit(configuration)
        UnitIds.RAGING_BOAR -> RagingBoarUnit(configuration)
        UnitIds.SLINGMASTER -> SlingmasterUnit(configuration)
        else -> error("unknown unit configuration: ${configuration.id}")
    }

    fun create(id: String, catalog: UnitCatalog): UnitDefinition =
        create(catalog.configuration(id))

    fun all(catalog: UnitCatalog): Set<UnitDefinition> = catalog.all.map(::create).toSet()

    fun byId(id: String, catalog: UnitCatalog): UnitDefinition = create(id, catalog)
}
