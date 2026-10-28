package com.urkaaaz.application.progression

import com.urkaaaz.application.campaign.CampaignLoadoutRewardsRead
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutPurchaseResult

class CampaignAwareLoadoutProgressionPort(
    private val playerProgression: LoadoutProgressionPort,
    private val campaignSystem: CampaignSystem,
) : LoadoutProgressionPort {
    override suspend fun snapshot(level: Int): LoadoutProgressionRead {
        val campaignRewards = when (val result = campaignSystem.loadoutRewards(level)) {
            is CampaignLoadoutRewardsRead.Available -> result.rewards
            is CampaignLoadoutRewardsRead.Rejected ->
                return LoadoutProgressionRead.Blocked(
                    when (result.reason) {
                        CampaignSelectionRejection.INVALID_LEVEL ->
                            LoadoutProgressionFailure.INVALID_LEVEL
                        CampaignSelectionRejection.LEVEL_LOCKED ->
                            LoadoutProgressionFailure.CAMPAIGN_LEVEL_LOCKED
                        CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED ->
                            LoadoutProgressionFailure.CAMPAIGN_LEVEL_COMPLETED
                    },
                )
            is CampaignLoadoutRewardsRead.Failure ->
                return LoadoutProgressionRead.Blocked(result.reason.toLoadoutFailure())
        }
        val playerSnapshot = when (val result = playerProgression.snapshot(level)) {
            is LoadoutProgressionRead.Available -> result.snapshot
            is LoadoutProgressionRead.Blocked -> return result
        }
        return LoadoutProgressionRead.Available(
            playerSnapshot.copy(
                unlockedSpellTypes =
                    playerSnapshot.unlockedSpellTypes + campaignRewards.unlockedSpellTypes,
                defaultSpellTypes =
                    playerSnapshot.defaultSpellTypes + campaignRewards.defaultSpellTypes,
            ),
        )
    }

    override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
        playerProgression.purchaseSpell(spellType)

    private fun CampaignProgressFailure.toLoadoutFailure(): LoadoutProgressionFailure =
        when (this) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                LoadoutProgressionFailure.STORAGE_UNAVAILABLE
            CampaignProgressFailure.INVALID_DATA ->
                LoadoutProgressionFailure.INVALID_PROFILE
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                LoadoutProgressionFailure.UNSUPPORTED_PROFILE_VERSION
        }
}
