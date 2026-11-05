package com.urkaaaz.application.balance

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.domain.GameBalanceFixtures
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
        assertEquals(5, result.balance.config.units.all.size)
        assertEquals(5, result.balance.config.ammunition.all.size)
        assertEquals(11, result.balance.config.spells.all.size)
        assertEquals(6, result.balance.config.schemaVersion)
        assertEquals("game-balance-10", result.balance.config.balanceRevision)
        assertEquals(8_000, result.balance.config.bosses.first().maxHealth)
        assertEquals(1.2f, result.balance.config.runtime.combat.gameplaySpeedMultiplier)
        assertEquals(0.9f, result.balance.config.runtime.combat.unitMovementSpeedScale)
        assertEquals(0.44f, result.balance.config.runtime.combat.garrisonMovementSpeedScale)
        assertEquals(GameBalanceFixtures.balance.units.all, result.balance.config.units.all)
        assertEquals(GameBalanceFixtures.balance.ammunition.all, result.balance.config.ammunition.all)
        assertEquals(GameBalanceFixtures.balance.spells.all, result.balance.config.spells.all)
        assertEquals(GameBalanceFixtures.balance.mana, result.balance.config.mana)
        assertEquals(GameBalanceFixtures.balance.runtime, result.balance.config.runtime)
        assertEquals(
            220f,
            result.balance.config.ammunition
                .definition(com.urkaaaz.domain.AmmunitionType.POWDER_BARREL).damage.unitRadius,
        )
        (5..CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL step 5).forEach { level ->
            val encounter = requireNotNull(
                CampaignLevelDefinition.forLevel(
                    level,
                    GameBalanceFixtures.balance.runtime.campaign,
                ).toMatchScenario().bossEncounter,
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
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalid = bundled.replace(
            "\"schemaVersion\": 6,",
            "\"schemaVersion\": 6,\n  \"extra\": true,",
        )
        val repository = GameBalanceRepository {
            ByteArrayInputStream(invalid.toByteArray())
        }

        assertEquals(
            GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun unsupportedBalanceSchemaIsRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalid = bundled.replace("\"schemaVersion\": 6,", "\"schemaVersion\": 5,")
        val repository = GameBalanceRepository {
            ByteArrayInputStream(invalid.toByteArray())
        }

        assertEquals(
            GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun gameplaySpeedMultiplierOutsideTheSupportedRangeIsRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }

        listOf("0.99", "1.51").forEach { multiplier ->
            val invalid = bundled.replace(
                "\"gameplaySpeedMultiplier\": 1.2,",
                "\"gameplaySpeedMultiplier\": $multiplier,",
            )
            val repository = GameBalanceRepository {
                ByteArrayInputStream(invalid.toByteArray())
            }

            assertEquals(
                GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
                repository.load(),
            )
        }
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
    fun invalidUnitAmmunitionSpellAndManaSectionsFailWithoutFallbacks() {
        val content = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val rockProfile = requireNotNull(
            Regex(
                "\"type\"\\s*:\\s*\"ROCK\"\\s*,\\s*\"effect\"\\s*:\\s*\"DIRECT\"\\s*," +
                    "\\s*\"shopCostGold\"\\s*:\\s*null",
            ).find(content),
        ) { "Bundled ROCK profile must keep a nullable shop price" }
        val invalidRockPrice = content.replaceRange(
            rockProfile.range,
            rockProfile.value.replace("null", "1"),
        )
        val invalidDocuments = listOf(
            content.replace("\"supplyCost\": 10", "\"supplyCost\": -1"),
            content.replace("\"kind\": \"DIRECT_IMPACT\"", "\"kind\": \"SCRIPT\""),
            content.replace("\"shopCostGold\": 250", "\"shopCostGold\": -1"),
            invalidRockPrice,
            content.replace("\"manaCost\": 50", "\"manaCost\": 0"),
            content.replace("\"startingMana\": 100", "\"startingMana\": 101"),
            content.replace("\"initialAimPower\": 50", "\"initialAimPower\": 101"),
        )

        invalidDocuments.forEach { invalid ->
            assertNotEquals(content, invalid)
            val repository = GameBalanceRepository {
                ByteArrayInputStream(invalid.toByteArray())
            }
            assertEquals(
                GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
                repository.load(),
            )
        }
    }

    @Test
    fun contentHashChangesWhenBalanceFileChanges() {
        fun repository(content: String) = GameBalanceRepository {
            ByteArrayInputStream(content.toByteArray())
        }
        val content = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val changed = content.replace("game-balance-10", "game-balance-11")
        val first = assertIs<GameBalanceRead.Available>(repository(content).load()).balance
        val second = assertIs<GameBalanceRead.Available>(repository(changed).load()).balance

        assertNotEquals(first.contentHash, second.contentHash)
    }
}
