package com.urkaaaz.domain

import com.urkaaaz.contracts.Team

enum class AmmunitionHitLayer {
    TERRAIN,
    UNIT,
    FORTRESS,
    BOSS,
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
    val startingCount: Int,
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
    val gravityResponse: Float,
    val drag: Float,
    val initialSpeedMultiplier: Float,
    val maximumLifetimeSeconds: Float,
    val apexFragmentation: ApexFragmentationProfile?,
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
    val friendlyFire: FriendlyFirePolicy,
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
    val falloff: AmmunitionFalloff,
    val minimumDamage: Int,
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
    ) : AmmunitionImpactBehavior {
        init {
            require(radius >= 0f) { "explosion radius must not be negative" }
            require(lifetimeSeconds > 0f) { "explosion lifetime must be positive" }
        }
    }

    data class LingeringArea(
        val radius: Float,
        val durationSeconds: Float,
        val tickIntervalSeconds: Float,
        val damagePerTick: Int,
        val fortressDamagePerTick: Int,
        val driftsWithWind: Boolean,
        val windDriftMultiplier: Float,
        val followsTerrain: Boolean,
        val status: UnitStatus?,
        val unitFriendlyFire: FriendlyFirePolicy,
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

data class AmmunitionDefinition(
    val type: AmmunitionType,
    val effect: AmmunitionEffect,
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

class AmmunitionCatalog(definitions: List<AmmunitionDefinition>) {
    val all: List<AmmunitionDefinition> = definitions.toList()
    private val byType = all.associateBy(AmmunitionDefinition::type)

    init {
        require(byType.size == all.size) { "ammunition definitions must be unique" }
        require(byType.keys == AmmunitionType.values().toSet()) {
            "ammunition catalog must define every stable ammunition ID exactly once"
        }
    }

    fun definition(type: AmmunitionType): AmmunitionDefinition =
        byType[type] ?: error("unknown ammunition definition: ${type.name}")

    fun find(id: String): AmmunitionDefinition? =
        AmmunitionType.values().firstOrNull { it.name == id }?.let(byType::get)
}
