package com.urkaaaz.simulation

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.BossEncounterConfig
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.WorldBounds

/** Tunable, host-independent constants for the deterministic simulation. */
data class SimulationConfig(
    val bounds: WorldBounds,
    val balance: GameBalanceConfig,
    val terrainBiome: String = "GREEN_FRONTIER",
    val gravityAccelerationY: Float = balance.runtime.physics.gravityAccelerationY,
    val windAccelerationX: Float = 0f,
    val windAccelerationScale: Float = balance.runtime.physics.windAccelerationScale,
    val fortressCollisionRadius: Float = 23f,
    val defaultCatapultHeightRatio: Float = 0.72f,
    val blueFortressMaxHealth: Int = balance.runtime.fortresses.standardMaximumHealth,
    val redFortressMaxHealth: Int = balance.runtime.fortresses.standardMaximumHealth,
    val matchDurationMilliseconds: Long = balance.runtime.match.durationMilliseconds,
    val bossEncounterMatchDurationMilliseconds: Long =
        balance.runtime.match.bossEncounterDurationMilliseconds,
    val mapDefinition: MapDefinition? = null,
    val bossEncounter: BossEncounterConfig? = null,
    val bossProfile: BossBalanceProfile? = null,
    val balanceRevision: String? = null,
    val balanceContentHash: String? = null,
    val playerLoadout: PlayerLoadoutConfig? = null,
    val playerInventory: PlayerInventoryConfig? = null,
    val availableAmmunitionTypes: Set<String> = emptySet(),
    val availableSpellTypes: Set<String> = emptySet(),
    val combatRandomSeed: Long = 0L,
) {
    init {
        require(gravityAccelerationY >= 0f) { "gravity must not be negative" }
        require(windAccelerationScale > 0f) {
            "wind acceleration scale must be positive"
        }
        require(defaultCatapultHeightRatio in 0f..1f) {
            "default catapult height ratio must be between 0 and 1"
        }
        require(blueFortressMaxHealth > 0) {
            "blue fortress max health must be positive"
        }
        require(redFortressMaxHealth > 0) {
            "red fortress max health must be positive"
        }
        require(matchDurationMilliseconds > 0) {
            "match duration must be positive"
        }
        require(bossEncounterMatchDurationMilliseconds > 0) {
            "boss encounter match duration must be positive"
        }
        require((bossEncounter == null) == (bossProfile == null)) {
            "boss encounter and boss balance profile must be provided together"
        }
        if (bossEncounter != null) {
            require(bossEncounter.profileId == bossProfile?.id) {
                "boss encounter profile ID must match the loaded boss profile"
            }
            require(
                (bossProfile?.archetype == BossArchetype.CENTRAL_OBJECTIVE) ==
                    (bossEncounter.victoryRule == BossVictoryRule.CENTRAL_DAMAGE_RACE),
            ) {
                "boss victory rule must match the boss archetype"
            }
        }
        playerInventory?.let { inventory ->
            require(playerLoadout?.playerId == inventory.playerId) {
                "inventory player must match the committed loadout player"
            }
            require(inventory.ammunitionStocks.all { (identifier, _) ->
                balance.ammunition.find(identifier)?.unlimited == false
            }) {
                "inventory ammunition must use known limited ammunition IDs"
            }
            require(inventory.specialSpellStocks.keys.all { identifier ->
                balance.spells.find(identifier)?.special == true
            }) {
                "inventory spells must use known special spell IDs"
            }
        }
        require(availableSpellTypes.all { balance.spells.find(it) != null }) {
            "available spells must use known spell IDs"
        }
    }

}
