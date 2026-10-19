package com.urkaaaz.campaign

import com.urkaaaz.domain.SpellType

data class CampaignMilestoneDefinition(
    val id: String,
    val completedLevel: Int,
    val availableFromLevel: Int,
    val spellType: SpellType,
    val preferInNewDefaultLoadout: Boolean,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) {
            "milestone id must be a stable lowercase identifier"
        }
        require(completedLevel in 1..CampaignLevelDefinition.MAX_LEVELS) {
            "completedLevel must be a valid campaign level"
        }
        require(availableFromLevel in (completedLevel + 1)..CampaignLevelDefinition.MAX_LEVELS) {
            "availableFromLevel must be after completion and within the campaign"
        }
        require(!spellType.special) { "campaign milestones can only unlock regular spells" }
    }
}

class CampaignMilestoneRegistry(
    definitions: List<CampaignMilestoneDefinition> = DEFAULT_DEFINITIONS,
) {
    private val definitionsById = definitions.associateBy { it.id }

    init {
        require(definitionsById.size == definitions.size) {
            "campaign milestone IDs must be unique"
        }
    }

    fun milestone(id: String): CampaignMilestoneDefinition? = definitionsById[id]

    fun milestonesForCompletedLevel(level: Int): List<CampaignMilestoneDefinition> =
        definitionsById.values
            .filter { it.completedLevel == level }
            .sortedBy { it.id }

    fun milestonesAvailableAt(level: Int): List<CampaignMilestoneDefinition> =
        definitionsById.values
            .filter { it.availableFromLevel <= level }
            .sortedBy { it.id }

    companion object {
        private val DEFAULT_DEFINITIONS = listOf(
            CampaignMilestoneDefinition(
                id = "campaign_spell_haste",
                completedLevel = 5,
                availableFromLevel = 6,
                spellType = SpellType.HASTE,
                preferInNewDefaultLoadout = true,
            ),
            CampaignMilestoneDefinition(
                id = "campaign_spell_heal",
                completedLevel = 7,
                availableFromLevel = 8,
                spellType = SpellType.HEAL,
                preferInNewDefaultLoadout = true,
            ),
        )

        fun default(): CampaignMilestoneRegistry = CampaignMilestoneRegistry()
    }
}
