package com.urkaaaz.campaign

import com.urkaaaz.domain.GameBalanceFixtures

internal fun CampaignLevelDefinition.Companion.forLevel(
    level: Int,
    mapRepository: MapDefinitionRepository = MapDefinitionRepository.bundled,
): CampaignLevelDefinition =
    forLevel(level, GameBalanceFixtures.balance.runtime.campaign, mapRepository)

internal fun CampaignLevelRegistry.Companion.default(): CampaignLevelRegistry =
    default(GameBalanceFixtures.balance.runtime.campaign)

internal fun CampaignLevelRegistry.Companion.using(
    repository: MapDefinitionRepository,
): CampaignLevelRegistry =
    using(GameBalanceFixtures.balance.runtime.campaign, repository)
