package com.urkaaaz.simulation

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.BossEncounterConfig
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.domain.ManaConfiguration
import com.urkaaaz.domain.WorldBounds

/** Tunable, host-independent constants for the deterministic simulation. */
data class SimulationConfig(
    val bounds: WorldBounds,
    val terrainBiome: String = "GREEN_FRONTIER",
    val gravityAccelerationY: Float = 420f,
    val windAccelerationX: Float = 0f,
    val windAccelerationScale: Float = 390f,
    val fortressCollisionRadius: Float = 23f,
    val unitCollisionRadius: Float = WorldScale.UNIT_COLLISION_RADIUS,
    val defaultCatapultHeightRatio: Float = 0.72f,
    val blueFortressMaxHealth: Int = 500,
    val redFortressMaxHealth: Int = 500,
    val startingLimitedAmmunition: Int = 3,
    val startingMana: Int = ManaConfiguration.STARTING_MANA,
    val maximumMana: Int = ManaConfiguration.MAXIMUM_MANA,
    val manaRegenerationAmount: Int = ManaConfiguration.REGENERATION_AMOUNT,
    val manaRegenerationIntervalMilliseconds: Long =
        ManaConfiguration.REGENERATION_INTERVAL_MILLISECONDS,
    val matchDurationMilliseconds: Long = DEFAULT_MATCH_DURATION_MILLISECONDS,
    val bossEncounterMatchDurationMilliseconds: Long = matchDurationMilliseconds,
    val mapDefinition: MapDefinition? = null,
    val bossEncounter: BossEncounterConfig? = null,
    val bossProfile: BossBalanceProfile? = null,
    val balanceRevision: String? = null,
    val balanceContentHash: String? = null,
    val playerLoadout: PlayerLoadoutConfig? = null,
    val availableAmmunitionTypes: Set<String> = emptySet(),
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
        require(startingLimitedAmmunition >= 0) {
            "starting limited ammunition must not be negative"
        }
        require(maximumMana > 0) { "maximum mana must be positive" }
        require(startingMana in 0..maximumMana) {
            "starting mana must be within mana bounds"
        }
        require(manaRegenerationAmount > 0) {
            "mana regeneration amount must be positive"
        }
        require(manaRegenerationIntervalMilliseconds > 0) {
            "mana regeneration interval must be positive"
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
    }

    companion object {
        const val DEFAULT_MATCH_DURATION_MILLISECONDS = 300_000L
    }
}
