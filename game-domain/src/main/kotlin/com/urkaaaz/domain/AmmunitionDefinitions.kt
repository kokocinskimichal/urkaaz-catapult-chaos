package com.urkaaaz.domain

import com.urkaaaz.contracts.Team

enum class AmmunitionHitLayer {
    TERRAIN,
    UNIT,
    FORTRESS,
    OUT_OF_BOUNDS,
}

enum class AmmunitionCollisionMode {
    STOP_ON_FIRST_TARGET,
    PIERCE_UNITS,
    IGNORE_UNITS,
    IMPACT_ON_TERRAIN,
}

enum class AmmunitionFalloff {
    NONE,
    LINEAR,
}

enum class FriendlyFirePolicy {
    ENEMY_ONLY,
    ALL_UNITS,
    ALL_TARGETS,
}

data class ReloadProfile(
    val reloadSeconds: Float,
) {
    init {
        require(reloadSeconds >= 0f) { "reload time must not be negative" }
    }
}

data class InventoryProfile(
    val unlimited: Boolean,
    val startingCount: Int = 0,
) {
    init {
        require(startingCount >= 0) { "starting ammunition count must not be negative" }
    }
}

data class ApexFragmentationProfile(
    val fragmentsPerSide: Int,
    val minimumReferenceSpeed: Float,
    val horizontalSpreadPerStep: Float,
    val initialDownwardSpeed: Float,
    val downwardSpreadPerStep: Float,
) {
    init {
        require(fragmentsPerSide > 0) { "fragment count per side must be positive" }
        require(minimumReferenceSpeed > 0f) { "minimum reference speed must be positive" }
        require(horizontalSpreadPerStep >= 0f) {
            "horizontal spread per step must not be negative"
        }
        require(initialDownwardSpeed >= 0f) {
            "initial downward speed must not be negative"
        }
        require(downwardSpreadPerStep >= 0f) {
            "downward spread per step must not be negative"
        }
    }
}

data class FlightProfile(
    val mass: Float,
    val windResponse: Float,
    val gravityResponse: Float = 1f,
    val drag: Float = 0f,
    val initialSpeedMultiplier: Float = 8f,
    val maximumLifetimeSeconds: Float = 30f,
    val apexFragmentation: ApexFragmentationProfile? = null,
) {
    init {
        require(mass > 0f) { "projectile mass must be positive" }
        require(windResponse >= 0f) { "wind response must not be negative" }
        require(gravityResponse >= 0f) { "gravity response must not be negative" }
        require(drag >= 0f) { "drag must not be negative" }
        require(initialSpeedMultiplier > 0f) {
            "initial speed multiplier must be positive"
        }
        require(maximumLifetimeSeconds > 0f) {
            "maximum projectile lifetime must be positive"
        }
    }
}

data class CollisionProfile(
    val hitLayers: Set<AmmunitionHitLayer>,
    val mode: AmmunitionCollisionMode,
    val hitRadius: Float,
    val friendlyFire: FriendlyFirePolicy = FriendlyFirePolicy.ENEMY_ONLY,
) {
    init {
        require(hitLayers.isNotEmpty()) { "collision profile must have hit layers" }
        require(hitRadius >= 0f) { "hit radius must not be negative" }
    }
}

data class DamageProfile(
    val unitDamage: Int,
    val fortressDamage: Int,
    val unitRadius: Float,
    val fortressRadius: Float,
    val falloff: AmmunitionFalloff = AmmunitionFalloff.LINEAR,
    val minimumDamage: Int = 0,
) {
    init {
        require(unitDamage >= 0) { "unit damage must not be negative" }
        require(fortressDamage >= 0) { "fortress damage must not be negative" }
        require(unitRadius >= 0f) { "unit damage radius must not be negative" }
        require(fortressRadius >= 0f) { "fortress damage radius must not be negative" }
        require(minimumDamage >= 0) { "minimum damage must not be negative" }
    }
}

data class TerrainProfile(
    val deformsTerrain: Boolean,
    val craterRadius: Float,
    val craterDepth: Float,
) {
    init {
        require(craterRadius >= 0f) { "crater radius must not be negative" }
        require(craterDepth >= 0f) { "crater depth must not be negative" }
        require(deformsTerrain || (craterRadius == 0f && craterDepth == 0f)) {
            "disabled terrain deformation must have zero crater values"
        }
    }
}

sealed interface AmmunitionImpactBehavior {
    data object DirectImpact : AmmunitionImpactBehavior

    data class Explosion(
        val radius: Float,
        val lifetimeSeconds: Float,
    ) : AmmunitionImpactBehavior

    data class LingeringArea(
        val radius: Float,
        val durationSeconds: Float,
        val tickIntervalSeconds: Float,
        val damagePerTick: Int,
        val fortressDamagePerTick: Int,
        val driftsWithWind: Boolean,
        val windDriftMultiplier: Float = 1f,
        val followsTerrain: Boolean = false,
        val status: UnitStatus? = null,
        val unitFriendlyFire: FriendlyFirePolicy = FriendlyFirePolicy.ENEMY_ONLY,
    ) : AmmunitionImpactBehavior {
        init {
            require(radius >= 0f) { "lingering radius must not be negative" }
            require(durationSeconds > 0f) { "lingering duration must be positive" }
            require(tickIntervalSeconds > 0f) {
                "lingering tick interval must be positive"
            }
            require(damagePerTick >= 0) { "lingering damage must not be negative" }
            require(fortressDamagePerTick >= 0) {
                "lingering fortress damage must not be negative"
            }
            require(windDriftMultiplier >= 0f) {
                "lingering wind drift multiplier must not be negative"
            }
        }
    }
}

enum class UnitStatus {
    BURNING,
    POISONED,
    SLOWED,
    STUNNED,
    HASTED,
}

data class StatusState(
    val remainingSeconds: Float,
    val strength: Float,
) {
    init {
        require(remainingSeconds > 0f) { "status duration must be positive" }
        require(strength >= 0f) { "status strength must not be negative" }
    }
}

sealed class AmmunitionDefinition(
    val type: AmmunitionType,
    val displayKey: String,
    val reload: ReloadProfile,
    val inventory: InventoryProfile,
    val flight: FlightProfile,
    val collision: CollisionProfile,
    val damage: DamageProfile,
    val terrain: TerrainProfile,
    val behavior: AmmunitionImpactBehavior,
    val availableLayers: Set<AmmunitionHitLayer>,
) {
    init {
        require(displayKey.isNotBlank()) { "display key must not be blank" }
        require(availableLayers.isNotEmpty()) { "available layers must not be empty" }
    }

    val reloadSeconds: Float
        get() = reload.reloadSeconds

    val unlimited: Boolean
        get() = inventory.unlimited

    fun canAffect(team: Team, targetTeam: Team?): Boolean =
        targetTeam == null || team != targetTeam ||
            collision.friendlyFire != FriendlyFirePolicy.ENEMY_ONLY
}

object RockAmmunition : AmmunitionDefinition(
    type = AmmunitionType.ROCK,
    displayKey = "ammunition.rock",
    reload = ReloadProfile(0f),
    inventory = InventoryProfile(unlimited = true),
    flight = FlightProfile(mass = 1.5f, windResponse = 1f),
    collision = CollisionProfile(
        hitLayers = setOf(
            AmmunitionHitLayer.TERRAIN,
            AmmunitionHitLayer.UNIT,
            AmmunitionHitLayer.FORTRESS,
        ),
        mode = AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
        hitRadius = 14f,
    ),
    damage = DamageProfile(
        unitDamage = 50,
        fortressDamage = 50,
        unitRadius = 28f,
        fortressRadius = 28f,
        minimumDamage = 50,
    ),
    terrain = TerrainProfile(true, 24f, 14f),
    behavior = AmmunitionImpactBehavior.DirectImpact,
    availableLayers = setOf(
        AmmunitionHitLayer.TERRAIN,
        AmmunitionHitLayer.UNIT,
        AmmunitionHitLayer.FORTRESS,
    ),
)

object SiegeBombAmmunition : AmmunitionDefinition(
    type = AmmunitionType.SIEGE_BOMB,
    displayKey = "ammunition.siege_bomb",
    reload = ReloadProfile(5.5f),
    inventory = InventoryProfile(unlimited = true),
    flight = FlightProfile(mass = 2.5f, windResponse = 0.8f),
    collision = CollisionProfile(
        hitLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
        mode = AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
        hitRadius = 57.5f,
    ),
    damage = DamageProfile(
        unitDamage = 72,
        fortressDamage = 100,
        unitRadius = 115f,
        fortressRadius = 115f,
    ),
    terrain = TerrainProfile(true, 82f, 48f),
    behavior = AmmunitionImpactBehavior.Explosion(115f, 0.8f),
    availableLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
)

object PowderBarrelAmmunition : AmmunitionDefinition(
    type = AmmunitionType.POWDER_BARREL,
    displayKey = "ammunition.powder_barrel",
    reload = ReloadProfile(6.5f),
    inventory = InventoryProfile(unlimited = false, startingCount = 3),
    flight = FlightProfile(mass = 2f, windResponse = 0.9f),
    collision = CollisionProfile(
        hitLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
        mode = AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
        hitRadius = 110f,
        friendlyFire = FriendlyFirePolicy.ALL_UNITS,
    ),
    damage = DamageProfile(
        unitDamage = 120,
        fortressDamage = 120,
        unitRadius = 220f,
        fortressRadius = 220f,
    ),
    terrain = TerrainProfile(true, 150f, 90f),
    behavior = AmmunitionImpactBehavior.Explosion(220f, 0.8f),
    availableLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
)

object FireRainAmmunition : AmmunitionDefinition(
    type = AmmunitionType.FIRE_RAIN,
    displayKey = "ammunition.fire_rain",
    reload = ReloadProfile(8f),
    inventory = InventoryProfile(unlimited = false, startingCount = 3),
    flight = FlightProfile(
        mass = 1.2f,
        windResponse = 1.1f,
        initialSpeedMultiplier = 8f,
        apexFragmentation = ApexFragmentationProfile(
            fragmentsPerSide = 3,
            minimumReferenceSpeed = 120f,
            horizontalSpreadPerStep = 0.09f,
            initialDownwardSpeed = 0.10f,
            downwardSpreadPerStep = 0.025f,
        ),
    ),
    collision = CollisionProfile(
        hitLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
        mode = AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
        hitRadius = 34f,
    ),
    damage = DamageProfile(
        unitDamage = 4,
        fortressDamage = 4,
        unitRadius = 68f,
        fortressRadius = 68f,
    ),
    terrain = TerrainProfile(true, 36f, 10f),
    behavior = AmmunitionImpactBehavior.LingeringArea(
        radius = 68f,
        durationSeconds = 9f,
        tickIntervalSeconds = 0.5f,
        damagePerTick = 4,
        fortressDamagePerTick = 4,
        driftsWithWind = false,
        status = UnitStatus.BURNING,
        unitFriendlyFire = FriendlyFirePolicy.ALL_UNITS,
    ),
    availableLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
)

object PlagueCauldronAmmunition : AmmunitionDefinition(
    type = AmmunitionType.PLAGUE_CAULDRON,
    displayKey = "ammunition.plague_cauldron",
    reload = ReloadProfile(15f),
    inventory = InventoryProfile(unlimited = false, startingCount = 3),
    flight = FlightProfile(mass = 3f, windResponse = 0.6f),
    collision = CollisionProfile(
        hitLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
        mode = AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
        hitRadius = 130f,
    ),
    damage = DamageProfile(
        unitDamage = 8,
        fortressDamage = 0,
        unitRadius = 300f,
        fortressRadius = 300f,
    ),
    terrain = TerrainProfile(false, 0f, 0f),
    behavior = AmmunitionImpactBehavior.LingeringArea(
        radius = 300f,
        durationSeconds = 6f,
        tickIntervalSeconds = 1f,
        damagePerTick = 8,
        fortressDamagePerTick = 0,
        driftsWithWind = true,
        windDriftMultiplier = 0.75f,
        followsTerrain = true,
        status = UnitStatus.POISONED,
    ),
    availableLayers = setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
)

object AmmunitionCatalog {
    val all: List<AmmunitionDefinition> = listOf(
        RockAmmunition,
        SiegeBombAmmunition,
        PowderBarrelAmmunition,
        FireRainAmmunition,
        PlagueCauldronAmmunition,
    )

    fun definition(type: AmmunitionType): AmmunitionDefinition =
        all.firstOrNull { it.type == type }
            ?: error("unknown ammunition definition: ${type.name}")
}
