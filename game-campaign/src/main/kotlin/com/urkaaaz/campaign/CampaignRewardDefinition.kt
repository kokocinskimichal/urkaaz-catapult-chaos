package com.urkaaaz.campaign

import com.urkaaaz.domain.RewardBalance
import com.urkaaaz.domain.CampaignBalance

sealed interface RewardCondition {
    data object DirectEnemyUnitHit : RewardCondition
    data class FastWin(val maximumTimeMilliseconds: Long) : RewardCondition {
        init {
            require(maximumTimeMilliseconds > 0) {
                "fast-win threshold must be positive"
            }
        }
    }
    data object UntouchedFortress : RewardCondition
    data class FortressHitStreak(val minimumHits: Int) : RewardCondition {
        init {
            require(minimumHits >= 2) { "fortress streak reward must start at hit two" }
        }
    }
}

data class AchievementDefinition(
    val id: String,
    val gold: Long,
    val condition: RewardCondition,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) {
            "achievement id must be a stable lowercase identifier"
        }
        require(gold >= 0L) { "achievement gold must not be negative" }
    }
}

data class LevelRewardDefinition(
    val level: Int,
    val winGold: Long,
    val achievements: List<AchievementDefinition>,
) {
    init {
        require(level in 1..CampaignLevelDefinition.MAX_LEVELS) {
            "reward level must be a valid campaign level"
        }
        require(winGold >= 0L) { "win gold must not be negative" }
        require(achievements.map { it.id }.distinct().size == achievements.size) {
            "achievement IDs must be unique within a level"
        }
    }
}

class CampaignRewardRegistry(
    definitions: List<LevelRewardDefinition>,
    val milestones: CampaignMilestoneRegistry,
) {
    private val byLevel = definitions.associateBy { it.level }

    init {
        require(byLevel.size == definitions.size) { "reward levels must be unique" }
    }

    fun forLevel(level: Int): LevelRewardDefinition? = byLevel[level]

    companion object {
        fun default(
            balance: RewardBalance,
            campaignBalance: CampaignBalance,
        ): CampaignRewardRegistry =
            CampaignRewardRegistry(
                definitions = (1..CampaignLevelDefinition.MAX_LEVELS).map { level ->
                    LevelRewardDefinition(
                        level = level,
                        winGold = balance.winGold,
                        achievements = listOf(
                            AchievementDefinition(
                                id = "direct_enemy_unit_hit",
                                gold = balance.directEnemyUnitHitGold,
                                condition = RewardCondition.DirectEnemyUnitHit,
                            ),
                            AchievementDefinition(
                                id = "fast_win",
                                gold = balance.fastWinGold,
                                condition = RewardCondition.FastWin(
                                    balance.fastWinThresholdMilliseconds,
                                ),
                            ),
                            AchievementDefinition(
                                id = "untouched_fortress",
                                gold = balance.untouchedFortressGold,
                                condition = RewardCondition.UntouchedFortress,
                            ),
                            AchievementDefinition(
                                id = "fortress_hit_streak",
                                gold = balance.fortressHitStreakGoldPerHitBeyondFirst,
                                condition = RewardCondition.FortressHitStreak(
                                    balance.fortressHitStreakStartHits,
                                ),
                            ),
                        ),
                    )
                },
                milestones = CampaignMilestoneRegistry.default(campaignBalance),
            )
    }
}
