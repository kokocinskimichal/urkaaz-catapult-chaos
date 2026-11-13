package com.urkaaaz.campaign

import com.urkaaaz.domain.CampaignBalance
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SpellCatalog

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
    }
}

class CampaignMilestoneRegistry(
    definitions: List<CampaignMilestoneDefinition>,
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

    fun validate(spells: SpellCatalog) {
        require(definitionsById.values.all { !spells.definition(it.spellType).special }) {
            "campaign milestones can only unlock regular spells"
        }
    }

    companion object {
        private data class MilestoneTemplate(
            val id: String,
            val completedLevel: Int,
            val spellType: SpellType,
            val preferInNewDefaultLoadout: Boolean,
        )

        private val MILESTONE_TEMPLATES = listOf(
            MilestoneTemplate(
                id = "campaign_spell_haste",
                completedLevel = 5,
                spellType = SpellType.HASTE,
                preferInNewDefaultLoadout = true,
            ),
            MilestoneTemplate(
                id = "campaign_spell_heal",
                completedLevel = 7,
                spellType = SpellType.HEAL,
                preferInNewDefaultLoadout = true,
            ),
        )

        fun default(campaignBalance: CampaignBalance): CampaignMilestoneRegistry =
            CampaignMilestoneRegistry(
                MILESTONE_TEMPLATES.map { milestone ->
                    CampaignMilestoneDefinition(
                        id = milestone.id,
                        completedLevel = milestone.completedLevel,
                        availableFromLevel = campaignBalance.contentAvailability
                            .availableFromLevel(milestone.spellType)
                            ?: throw IllegalArgumentException(
                                "campaign milestone spell ${milestone.spellType} must be available",
                            ),
                        spellType = milestone.spellType,
                        preferInNewDefaultLoadout = milestone.preferInNewDefaultLoadout,
                    )
                },
            )
    }
}
