package com.urkaaaz.application.campaign

import com.urkaaaz.application.balance.GameBalanceRepository
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignLevelRegistry
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.campaign.DemoLevelDefinition
import com.urkaaaz.campaign.DemoLevelDefinitions
import com.urkaaaz.campaign.MapDefinitionRepository

object BundledCampaignBalance {
    fun bossPresentationKeyForLevel(level: Int): String? {
        val profileId = CampaignLevelDefinition.bossProfileIdForLevel(level) ?: return null
        return GameBalanceRepository.bundled.requireConfig()
            .bossProfile(profileId)
            .presentationKey
    }

    fun levelDefinition(
        level: Int,
        repository: MapDefinitionRepository = MapDefinitionRepository.bundled,
    ): CampaignLevelDefinition =
        CampaignLevelDefinition.forLevel(
            level,
            GameBalanceRepository.bundled.requireConfig().runtime.campaign,
            repository,
        )

    fun levelRegistry(
        repository: MapDefinitionRepository = MapDefinitionRepository.bundled,
    ): CampaignLevelRegistry =
        CampaignLevelRegistry.default(
            GameBalanceRepository.bundled.requireConfig().runtime.campaign,
            repository,
        )

    fun demoLevelDefinition(
        repository: MapDefinitionRepository = MapDefinitionRepository.bundled,
    ): DemoLevelDefinition =
        DemoLevelDefinitions.fromRepository(
            repository,
            GameBalanceRepository.bundled.requireConfig().runtime.campaign,
        )

    fun milestoneRegistry(): CampaignMilestoneRegistry =
        CampaignMilestoneRegistry.default(
            GameBalanceRepository.bundled.requireConfig().runtime.campaign,
        )

    fun rewardRegistry(): CampaignRewardRegistry {
        val balance = GameBalanceRepository.bundled.requireConfig()
        return CampaignRewardRegistry.default(
            balance.runtime.rewards,
            balance.runtime.campaign,
        )
    }
}
