package com.urkaaaz.campaign

import com.urkaaaz.domain.SpellType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignMilestoneDefinitionTest {
    @Test
    fun defaultRegistryContainsConfiguredHasteAndHealMilestones() {
        val registry = CampaignMilestoneRegistry.default()

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
            CampaignMilestoneDefinition(
                id = "special_spell",
                completedLevel = 5,
                availableFromLevel = 6,
                spellType = SpellType.CATAPULT_LOCK,
                preferInNewDefaultLoadout = false,
            )
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
