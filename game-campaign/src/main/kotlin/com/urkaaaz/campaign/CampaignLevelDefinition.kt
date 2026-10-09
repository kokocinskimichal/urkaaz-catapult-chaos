package com.urkaaaz.campaign

import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitConfig
import com.urkaaaz.contracts.VictoryRulesConfig
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitType

/** Biome selected by a campaign level; rendering maps it to visual assets later. */
enum class TerrainBiome {
    GREEN_FRONTIER,
    FROSTBOUND,
    COPPERWOOD,
    ASHEN_MARCH,
    SUNKEN_MARSHES,
}

/** Campaign-only difficulty tier consumed by the future AI module. */
enum class CampaignAiDifficulty {
    EASY,
    MEDIUM,
    HARD,
}

/** Terrain parameters that are meaningful to simulation but not to rendering. */
data class TerrainVariant(
    val biome: TerrainBiome,
    val width: Int = 1_600,
    val height: Int = 900,
)

/** Tutorial instructions are data and do not contain Android views or resource IDs. */
data class TutorialParameters(
    val enabled: Boolean,
    val stepKeys: List<String> = emptyList(),
)

/** Enemy tuning kept with a level definition rather than with UI or persistence. */
data class EnemyConfiguration(
    val difficulty: CampaignAiDifficulty,
    val aimErrorScale: Float,
    val unitDecisionDelayMilliseconds: Long,
    val healthMultiplier: Float,
    val fortressScale: Float,
) {
    init {
        require(aimErrorScale > 0f) { "aimErrorScale must be positive" }
        require(unitDecisionDelayMilliseconds >= 0) {
            "unitDecisionDelayMilliseconds must not be negative"
        }
        require(healthMultiplier > 0f) { "healthMultiplier must be positive" }
        require(fortressScale > 0f) { "fortressScale must be positive" }
    }
}

/**
 * Complete static definition of one campaign level.
 *
 * This type describes a match scenario only. It deliberately does not store
 * unlocked levels, gold, lives, shop state, or screen/navigation state.
 */
data class CampaignLevelDefinition(
    val level: Int,
    val terrain: TerrainVariant,
    val enemy: EnemyConfiguration,
    val availableUnits: Set<UnitType>,
    val availableAmmunition: Set<AmmunitionType>,
    val availableSpells: Set<SpellType>,
    val startingAmmunition: Int,
    val initialWind: WindSnapshot = WindSnapshot(),
    val tutorial: TutorialParameters = TutorialParameters(enabled = false),
) {
    init {
        require(level in 1..MAX_LEVELS) { "level must be between 1 and $MAX_LEVELS" }
        require(startingAmmunition >= 0) { "startingAmmunition must not be negative" }
    }

    fun toMatchScenario(
        playerId: PlayerId = PlayerId("player"),
        opponentId: PlayerId = PlayerId("opponent"),
    ): MatchScenario = MatchScenario(
        scenarioId = "campaign-$level",
        battlefield = BattlefieldConfig(terrain.width, terrain.height),
        players = listOf(
            PlayerConfig(playerId, Team.BLUE),
            PlayerConfig(opponentId, Team.RED),
        ),
        initialResources = ResourceSnapshot(
            ammunition = availableAmmunition.associate { it.name to startingAmmunition },
        ),
        availableAmmunition = availableAmmunition.map { it.name }.sorted(),
        availableSpells = availableSpells.map { it.name }.sorted(),
        initialUnits = availableUnits.map {
            UnitConfig(
                entityId = EntityId("level-$level-${it.name.lowercase()}"),
                unitType = it.name,
                team = Team.BLUE,
            )
        }.sortedBy { it.unitType },
        wind = initialWind,
        victoryRules = VictoryRulesConfig(defeatAllOpponents = true),
    )

    companion object {
        const val MAX_LEVELS = 100

        fun forLevel(level: Int): CampaignLevelDefinition {
            val safeLevel = level.coerceIn(1, MAX_LEVELS)
            val milestone = (safeLevel - 1) / 10
            return CampaignLevelDefinition(
                level = safeLevel,
                terrain = TerrainVariant(biome = biomeFor(safeLevel)),
                enemy = EnemyConfiguration(
                    difficulty = difficultyFor(safeLevel),
                    aimErrorScale = when (safeLevel) {
                        1 -> 1.25f
                        2 -> 1.10f
                        3 -> 1.00f
                        4 -> 0.92f
                        6 -> 1.00f
                        7 -> 0.88f
                        8 -> 0.76f
                        9 -> 0.64f
                        else -> 1f
                    },
                    unitDecisionDelayMilliseconds =
                        (1_100L - milestone * 90L).coerceAtLeast(180L),
                    healthMultiplier = when (safeLevel) {
                        1 -> 0.60f
                        2 -> 0.68f
                        3 -> 0.78f
                        4 -> 0.88f
                        6 -> 0.86f
                        7 -> 0.96f
                        8 -> 1.06f
                        9 -> 1.16f
                        else -> (0.60f + milestone * 0.08f).coerceAtMost(1.35f)
                    },
                    fortressScale = when (safeLevel) {
                        1 -> 0.70f
                        2 -> 0.76f
                        3 -> 0.82f
                        4 -> 0.88f
                        6 -> 0.86f
                        7 -> 0.94f
                        8 -> 1.02f
                        9 -> 1.10f
                        else -> (0.70f + milestone * 0.10f).coerceAtMost(1.50f)
                    },
                ),
                availableUnits = buildSet {
                    add(UnitType.DEFENDER)
                    if (safeLevel >= 5) add(UnitType.SAPPER)
                    if (safeLevel >= 8) add(UnitType.DEMOLISHER)
                    if (safeLevel >= 11) add(UnitType.RAGING_BOAR)
                    if (safeLevel >= 13) add(UnitType.SLINGMASTER)
                },
                availableAmmunition = buildSet {
                    add(AmmunitionType.ROCK)
                    add(AmmunitionType.SIEGE_BOMB)
                    if (safeLevel >= 21) add(AmmunitionType.POWDER_BARREL)
                    if (safeLevel >= 31) add(AmmunitionType.FIRE_RAIN)
                    if (safeLevel >= 41) add(AmmunitionType.PLAGUE_CAULDRON)
                },
                availableSpells = buildSet {
                    add(SpellType.FORTRESS_SHIELD)
                    if (safeLevel >= 6) add(SpellType.HASTE)
                    if (safeLevel >= 8) add(SpellType.HEAL)
                    if (safeLevel >= 11) add(SpellType.ICE_TRAP)
                    if (safeLevel >= 21) add(SpellType.RUNIC_BASTION)
                },
                startingAmmunition = if (safeLevel <= 10) 8 + (safeLevel - 1) * 2 else 30,
                tutorial = TutorialParameters(
                    enabled = safeLevel == 1,
                    stepKeys = if (safeLevel == 1) {
                        listOf("aim", "fire", "advance")
                    } else {
                        emptyList()
                    },
                ),
            )
        }

        private fun biomeFor(level: Int): TerrainBiome = when (level) {
            in 11..20 -> TerrainBiome.FROSTBOUND
            in 21..30 -> TerrainBiome.COPPERWOOD
            in 31..40 -> TerrainBiome.ASHEN_MARCH
            in 41..50 -> TerrainBiome.SUNKEN_MARSHES
            else -> TerrainBiome.GREEN_FRONTIER
        }

        private fun difficultyFor(level: Int): CampaignAiDifficulty = when {
            level in setOf(3, 4, 6, 7) -> CampaignAiDifficulty.MEDIUM
            level in setOf(8, 9) -> CampaignAiDifficulty.HARD
            level <= 10 -> CampaignAiDifficulty.EASY
            level <= 30 -> CampaignAiDifficulty.MEDIUM
            else -> CampaignAiDifficulty.HARD
        }
    }
}
