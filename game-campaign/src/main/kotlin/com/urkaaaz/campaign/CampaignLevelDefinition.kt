package com.urkaaaz.campaign

import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.BossEncounterConfig
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.VictoryRulesConfig
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitIds
import com.urkaaaz.domain.CampaignBalance

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
    val availableUnits: Set<String>,
    val availableAmmunition: Set<AmmunitionType>,
    val availableSpells: Set<SpellType>,
    val initialWind: WindSnapshot = WindSnapshot(),
    val tutorial: TutorialParameters = TutorialParameters(enabled = false),
    val mapDefinition: MapDefinition,
) {
    init {
        require(level in 1..MAX_LEVELS) { "level must be between 1 and $MAX_LEVELS" }
    }

    fun toMatchScenario(
        playerId: PlayerId = PlayerId("player"),
        opponentId: PlayerId = PlayerId("opponent"),
        playerLoadout: PlayerLoadoutConfig? = null,
        playerInventory: PlayerInventoryConfig? = null,
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
        availableUnits = availableUnits.sorted(),
        availableAmmunition = availableAmmunition.map { it.name }.sorted(),
        availableSpells = availableSpells.map { it.name }.sorted(),
        playerLoadout = playerLoadout,
        playerInventory = playerInventory,
        wind = initialWind,
        victoryRules = VictoryRulesConfig(defeatAllOpponents = true),
        mapDefinition = mapDefinition,
        aiProfile = enemy.aiProfile,
        bossEncounter = bossEncounterForLevel(level),
    )

    companion object {
        const val MAX_LEVELS = 100
        const val LAST_COMPLETE_CAMPAIGN_LEVEL = 54

        fun bossProfileIdForLevel(level: Int): String? =
            bossEncounterForLevel(level)?.profileId

        private fun bossEncounterForLevel(level: Int): BossEncounterConfig? {
            val profileId = when (level) {
                5 -> "boss.central-ancient-war-oak.level-05"
                10 -> "boss.mobile-boulderbloom.level-10"
                15 -> "boss.central-frozen-troll.level-15"
                20 -> "boss.mobile-old-yeti-sovereign.level-20"
                25 -> "boss.central-copperwood-siege-lord.level-25"
                30 -> "boss.mobile-copperwood-final-boss.level-30"
                35 -> "boss.central-burntwood-warden.level-35"
                40 -> "boss.mobile-scorchbound-king.level-40"
                45 -> "boss.central-ancient-war-oak.level-45"
                50 -> "boss.mobile-thorncrown.level-50"
                else -> return null
            }
            return BossEncounterConfig(
                profileId = profileId,
                spawnXRatio = if (level % 10 == 5) 0.5f else 0.78f,
                victoryRule = if (level % 10 == 5) {
                    BossVictoryRule.CENTRAL_DAMAGE_RACE
                } else {
                    BossVictoryRule.DEFEAT_BOSS
                },
            )
        }

        fun forLevel(
            level: Int,
            campaignBalance: CampaignBalance,
            mapRepository: MapDefinitionRepository = MapDefinitionRepository.bundled,
        ): CampaignLevelDefinition {
            val safeLevel = level.coerceIn(1, MAX_LEVELS)
            val chapterRegistry = CampaignChapterRegistry.default()
            val chapter = chapterRegistry.chapterForLevel(safeLevel)
            val chapterNumber =
                chapterRegistry.chapters.indexOfFirst { it.id == chapter.id } + 1
            val campaignMapId = "chapter$chapterNumber-level$safeLevel"
            val mapId = if (mapRepository.contains(campaignMapId)) campaignMapId else "demo-map"
            val baseMapDefinition = mapRepository.require(mapId)
            val chapterBiome = chapter.biome.mapId
            val mapDefinition = if (baseMapDefinition.biome == chapterBiome) {
                baseMapDefinition
            } else {
                baseMapDefinition.copy(biome = chapterBiome)
            }
            return CampaignLevelDefinition(
                level = safeLevel,
                enemy = EnemyConfiguration(aiProfile = aiProfileFor(safeLevel, campaignBalance)),
                availableUnits = buildSet {
                    add(UnitIds.DEFENDER)
                    if (safeLevel >= 5) add(UnitIds.SAPPER)
                    if (safeLevel >= 8) add(UnitIds.DEMOLISHER)
                    if (safeLevel >= 11) add(UnitIds.RAGING_BOAR)
                    if (safeLevel >= 13) add(UnitIds.SLINGMASTER)
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
                initialWind = WindSnapshot(
                    direction = if (safeLevel % 2 == 0) -1f else 1f,
                    strength = campaignBalance.initialWindStrength(safeLevel),
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

        private fun aiProfileFor(level: Int, campaignBalance: CampaignBalance): AiProfileSelection {
            val band = campaignBalance.aiDifficultyFor(level)
            return AiProfileSelection(band.profileId, band.progressionPercent(level))
        }

    }
}
