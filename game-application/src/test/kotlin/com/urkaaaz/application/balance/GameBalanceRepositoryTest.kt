package com.urkaaaz.application.balance

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossVictoryRule
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameBalanceRepositoryTest {
    @Test
    fun bundledBalanceIsStrictlyLoadedAndContainsAllInitialBossProfiles() {
        val result = assertIs<GameBalanceRead.Available>(GameBalanceRepository.bundled.load())

        assertEquals(10, result.balance.config.bosses.size)
        assertEquals("boss.central-ancient-war-oak.level-05", result.balance.config.bosses.first().id)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_WINDUP_SECONDS,
            result.balance.config.bosses.first().attacks.single().windupSeconds,
        )
        assertEquals(432f, result.balance.config.bosses.first().collision.width)
        assertEquals(576f, result.balance.config.bosses.first().collision.height)
        val oakLevel45 = result.balance.config.bosses.single {
            it.id == "boss.central-ancient-war-oak.level-45"
        }
        assertEquals(432f, oakLevel45.collision.width)
        assertEquals(576f, oakLevel45.collision.height)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_WINDUP_SECONDS,
            oakLevel45.attacks.single().windupSeconds,
        )
        val frozenTroll = result.balance.config.bosses.single {
            it.id == "boss.central-frozen-troll.level-15"
        }
        assertEquals(1_440f, frozenTroll.collision.width)
        assertEquals(0f, frozenTroll.attacks.single().windupSeconds)
        assertTrue(result.balance.contentHash.matches(Regex("[a-f0-9]{64}")))
        (5..CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL step 5).forEach { level ->
            val encounter = requireNotNull(
                CampaignLevelDefinition.forLevel(level).toMatchScenario().bossEncounter,
            )
            val profile = result.balance.config.bossProfile(encounter.profileId)
            assertEquals(
                profile.archetype == BossArchetype.CENTRAL_OBJECTIVE,
                encounter.victoryRule == BossVictoryRule.CENTRAL_DAMAGE_RACE,
            )
        }
    }

    @Test
    fun unknownFieldsAreRejected() {
        val invalid = """
            {
              "schemaVersion": 1,
              "balanceRevision": "invalid",
              "bosses": [],
              "extra": true
            }
        """.trimIndent()
        val repository = GameBalanceRepository {
            ByteArrayInputStream(invalid.toByteArray())
        }

        assertEquals(
            GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun unsupportedAbilityKindReturnsTypedInvalidDataFailure() {
        val content = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
            .replace("\"kind\": \"ENRAGE\"", "\"kind\": \"FLY\"")
        val repository = GameBalanceRepository {
            ByteArrayInputStream(content.toByteArray())
        }

        assertEquals(
            GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun contentHashChangesWhenBalanceFileChanges() {
        fun repository(content: String) = GameBalanceRepository {
            ByteArrayInputStream(content.toByteArray())
        }
        val content = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val changed = content.replace("boss-legacy-baseline-5", "boss-legacy-baseline-6")
        val first = assertIs<GameBalanceRead.Available>(repository(content).load()).balance
        val second = assertIs<GameBalanceRead.Available>(repository(changed).load()).balance

        assertNotEquals(first.contentHash, second.contentHash)
    }
}
