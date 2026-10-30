package com.urkaaaz.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BossEncounterAssignmentTest {
    @Test
    fun bossLevelsFiveThroughFiftyHaveStableEncounterAssignments() {
        val expected = mapOf(
            5 to "boss.central-ancient-war-oak.level-05",
            10 to "boss.mobile-thorncrown.level-10",
            15 to "boss.central-frozen-troll.level-15",
            20 to "boss.mobile-old-yeti-sovereign.level-20",
            25 to "boss.central-copperwood-siege-lord.level-25",
            30 to "boss.mobile-copperwood-final-boss.level-30",
            35 to "boss.central-burntwood-warden.level-35",
            40 to "boss.mobile-scorchbound-king.level-40",
            45 to "boss.central-ancient-war-oak.level-45",
            50 to "boss.mobile-thorncrown.level-50",
        )

        expected.forEach { (level, profileId) ->
            assertEquals(
                profileId,
                CampaignLevelDefinition.forLevel(level)
                    .toMatchScenario()
                    .bossEncounter
                    ?.profileId,
            )
        }
    }

    @Test
    fun levelsAfterFiftyDoNotPretendToHaveBossProfiles() {
        (55..CampaignLevelDefinition.MAX_LEVELS step 5).forEach { level ->
            assertNull(
                CampaignLevelDefinition.forLevel(level)
                    .toMatchScenario()
                    .bossEncounter,
            )
        }
    }
}
