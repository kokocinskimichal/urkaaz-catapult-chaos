package com.urkaaaz.campaign

import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.VictoryRulesConfig
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory

/** Tutorial instructions are data and do not contain Android views or resource IDs. */
data class TutorialParameters(
    val enabled: Boolean,
    val stepKeys: List<String> = emptyList(),
)

data class EnemyConfiguration(
    val aiProfile: AiProfileSelection = AiProfileSelection(),
)

/**
 * Complete static definition of one campaign level.
 *
 * This type describes a match scenario only. It deliberately does not store
 * unlocked levels, gold, lives, shop state, or screen/navigation state.
 */
data class CampaignLevelDefinition(
    val level: Int,
    val enemy: EnemyConfiguration,
    val availableUnits: Set<UnitDefinition>,
    val availableAmmunition: Set<AmmunitionType>,
    val availableSpells: Set<SpellType>,
    val startingAmmunition: Int,
    val initialWind: WindSnapshot = WindSnapshot(),
    val tutorial: TutorialParameters = TutorialParameters(enabled = false),
    val mapDefinition: MapDefinition,
) {
    init {
        require(level in 1..MAX_LEVELS) { "level must be between 1 and $MAX_LEVELS" }
        require(startingAmmunition >= 0) { "startingAmmunition must not be negative" }
    }

    fun toMatchScenario(
        playerId: PlayerId = PlayerId("player"),
        opponentId: PlayerId = PlayerId("opponent"),
        playerLoadout: PlayerLoadoutConfig? = null,
    ): MatchScenario = MatchScenario(
        scenarioId = "campaign-$level",
        battlefield = BattlefieldConfig(
            WorldDimensions.WIDTH.toInt(),
            WorldDimensions.HEIGHT.toInt(),
        ),
        players = listOf(
            PlayerConfig(playerId, Team.BLUE),
            PlayerConfig(opponentId, Team.RED),
        ),
        initialResources = ResourceSnapshot(
            ammunition = availableAmmunition.associate { it.name to startingAmmunition },
        ),
        availableUnits = availableUnits.map { it.id }.sorted(),
        availableAmmunition = availableAmmunition.map { it.name }.sorted(),
        availableSpells = availableSpells.map { it.name }.sorted(),
        playerLoadout = playerLoadout,
        wind = initialWind,
        victoryRules = VictoryRulesConfig(defeatAllOpponents = true),
        mapDefinition = mapDefinition,
        aiProfile = enemy.aiProfile,
    )

    companion object {
        const val MAX_LEVELS = 100

        fun forLevel(
            level: Int,
            mapRepository: MapDefinitionRepository = MapDefinitionRepository.bundled,
        ): CampaignLevelDefinition {
            val safeLevel = level.coerceIn(1, MAX_LEVELS)
            val mapId = when (safeLevel) {
                CHAPTER_ONE_LEVEL_THREE -> CHAPTER_ONE_LEVEL_THREE_MAP_ID
                CHAPTER_ONE_LEVEL_FOUR -> CHAPTER_ONE_LEVEL_FOUR_MAP_ID
                else -> "demo-map"
            }
            val baseMapDefinition = mapRepository.require(mapId)
            val chapterBiome = CampaignChapterRegistry.default()
                .chapterForLevel(safeLevel)
                .biome
                .mapId
            val mapDefinition = if (baseMapDefinition.biome == chapterBiome) {
                baseMapDefinition
            } else {
                baseMapDefinition.copy(biome = chapterBiome)
            }
            return CampaignLevelDefinition(
                level = safeLevel,
                enemy = EnemyConfiguration(aiProfile = aiProfileFor(safeLevel)),
                availableUnits = buildSet {
                    add(UnitFactory.defender())
                    if (safeLevel >= 5) add(UnitFactory.sapper())
                    if (safeLevel >= 8) add(UnitFactory.demolisher())
                    if (safeLevel >= 11) add(UnitFactory.ragingBoar())
                    if (safeLevel >= 13) add(UnitFactory.slingmaster())
                },
                availableAmmunition = buildSet {
                    add(AmmunitionType.ROCK)
                    add(AmmunitionType.SIEGE_BOMB)
                    if (safeLevel >= 10) add(AmmunitionType.POWDER_BARREL)
                    if (safeLevel >= 31) add(AmmunitionType.FIRE_RAIN)
                    if (safeLevel >= 41) add(AmmunitionType.PLAGUE_CAULDRON)
                },
                availableSpells = buildSet {
                    add(SpellType.FORTRESS_SHIELD)
                    CampaignMilestoneRegistry.default()
                        .milestonesAvailableAt(safeLevel)
                        .forEach { add(it.spellType) }
                    if (safeLevel >= 11) add(SpellType.ICE_TRAP)
                    if (safeLevel >= 21) add(SpellType.RUNIC_BASTION)
                },
                startingAmmunition = if (safeLevel <= 10) 8 + (safeLevel - 1) * 2 else 30,
                initialWind = WindSnapshot(
                    direction = if (safeLevel % 2 == 0) -1f else 1f,
                    strength = 0.25f + ((safeLevel - 1) % 4) * 0.15f,
                ),
                tutorial = TutorialParameters(
                    enabled = safeLevel == 1,
                    stepKeys = if (safeLevel == 1) {
                        listOf("aim", "fire", "advance")
                    } else {
                        emptyList()
                    },
                ),
                mapDefinition = mapDefinition,
            )
        }

        private fun aiProfileFor(level: Int): AiProfileSelection {
            val profileId = when {
                level <= 10 -> AiProfileId.CADET
                level <= 30 -> AiProfileId.VETERAN
                else -> AiProfileId.ELITE
            }
            val progression = if (profileId == AiProfileId.ELITE && level >= 31) {
                ((level - 31) * 100) / (MAX_LEVELS - 31)
            } else {
                0
            }
            return AiProfileSelection(
                profileId = profileId,
                progressionPercent = progression,
            )
        }

        private const val CHAPTER_ONE_LEVEL_THREE = 3
        private const val CHAPTER_ONE_LEVEL_THREE_MAP_ID = "chapter1-level3"
        private const val CHAPTER_ONE_LEVEL_FOUR = 4
        private const val CHAPTER_ONE_LEVEL_FOUR_MAP_ID = "chapter1-level4"
    }
}
