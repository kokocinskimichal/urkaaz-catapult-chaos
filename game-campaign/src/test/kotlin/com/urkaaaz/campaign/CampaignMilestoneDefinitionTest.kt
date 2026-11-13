package com.urkaaaz.campaign

import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.domain.SpellType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignMilestoneDefinitionTest {
    @Test
    fun defaultRegistryContainsConfiguredHasteAndHealMilestones() {
        val registry = CampaignMilestoneRegistry.default(
            GameBalanceFixtures.balance.runtime.campaign,
        )

        assertEquals(
            CampaignMilestoneDefinition(
                id = "campaign_spell_haste",
                completedLevel = 5,
                availableFromLevel = 6,
                spellType = SpellType.HASTE,
                preferInNewDefaultLoadout = true,
            ),
            registry.milestone("campaign_spell_haste"),
        )
        assertEquals(
            CampaignMilestoneDefinition(
                id = "campaign_spell_heal",
                completedLevel = 7,
                availableFromLevel = 8,
                spellType = SpellType.HEAL,
                preferInNewDefaultLoadout = true,
            ),
            registry.milestone("campaign_spell_heal"),
        )
    }

    @Test
    fun milestoneAvailabilityUsesCampaignBalanceWithoutChangingRewardLevels() {
        val campaignBalance = GameBalanceFixtures.balance.runtime.campaign
        val availability = campaignBalance.contentAvailability.copy(
            spells = campaignBalance.contentAvailability.spells.map { entry ->
                when (entry.spellType) {
                    SpellType.HASTE -> entry.copy(availableFromLevel = 9)
                    SpellType.HEAL -> entry.copy(availableFromLevel = 12)
                    else -> entry
                }
            },
        )
        val registry = CampaignMilestoneRegistry.default(
            campaignBalance.copy(contentAvailability = availability),
        )

        assertEquals(5, registry.milestone("campaign_spell_haste")?.completedLevel)
        assertEquals(9, registry.milestone("campaign_spell_haste")?.availableFromLevel)
        assertEquals(7, registry.milestone("campaign_spell_heal")?.completedLevel)
        assertEquals(12, registry.milestone("campaign_spell_heal")?.availableFromLevel)
    }

    @Test
    fun invalidMilestoneDefinitionsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            CampaignMilestoneDefinition(
                id = "Haste",
                completedLevel = 5,
                availableFromLevel = 6,
                spellType = SpellType.HASTE,
                preferInNewDefaultLoadout = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CampaignMilestoneDefinition(
                id = "late_reward",
                completedLevel = 5,
                availableFromLevel = 5,
                spellType = SpellType.HASTE,
                preferInNewDefaultLoadout = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CampaignMilestoneRegistry(
                listOf(
                    CampaignMilestoneDefinition(
                        id = "special_spell",
                        completedLevel = 5,
                        availableFromLevel = 6,
                        spellType = SpellType.CATAPULT_LOCK,
                        preferInNewDefaultLoadout = false,
                    ),
                ),
            ).validate(GameBalanceFixtures.balance.spells)
        }
    }

    @Test
    fun registryRejectsDuplicateMilestoneIds() {
        val definition = CampaignMilestoneDefinition(
            id = "same_reward",
            completedLevel = 2,
            availableFromLevel = 3,
            spellType = SpellType.HASTE,
            preferInNewDefaultLoadout = false,
        )

        assertFailsWith<IllegalArgumentException> {
            CampaignMilestoneRegistry(listOf(definition, definition))
        }
    }
}
