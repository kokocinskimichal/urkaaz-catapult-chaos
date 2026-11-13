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
        assertEquals(7, result.balance.config.schemaVersion)
        assertTrue(result.balance.config.balanceRevision.matches(Regex("game-balance-\\d+")))
        assertEquals(5, result.balance.config.runtime.campaign.lives.initialLives)
        assertEquals(5, result.balance.config.runtime.campaign.lives.maximumLives)
        assertEquals(
            900_000L,
            result.balance.config.runtime.campaign.lives.regenerationIntervalMilliseconds,
        )
        assertEquals(8_000, result.balance.config.bosses.first().maxHealth)
        assertEquals(1.2f, result.balance.config.runtime.combat.gameplaySpeedMultiplier)
        assertEquals(0.9f, result.balance.config.runtime.combat.unitMovementSpeedScale)
        assertEquals(0.44f, result.balance.config.runtime.combat.garrisonMovementSpeedScale)
        assertEquals(GameBalanceFixtures.balance.units.all, result.balance.config.units.all)
        assertEquals(GameBalanceFixtures.balance.ammunition.all, result.balance.config.ammunition.all)
        assertEquals(GameBalanceFixtures.balance.spells.all, result.balance.config.spells.all)
        assertEquals(GameBalanceFixtures.balance.mana, result.balance.config.mana)
        val expectedRuntime = GameBalanceFixtures.balance.runtime.copy(
            campaign = GameBalanceFixtures.balance.runtime.campaign.copy(
                fortressHealthOverrides =
                    result.balance.config.runtime.campaign.fortressHealthOverrides,
            ),
        )
        assertEquals(expectedRuntime, result.balance.config.runtime)
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
            "\"schemaVersion\": 7,",
            "\"schemaVersion\": 7,\n  \"extra\": true,",
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
    fun campaignFortressHealthOverridesLoadSparsePerTeamValues() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val customized = replaceCampaignFortressHealthOverrides(
            bundled,
            """
                [
                  {"level": 31, "blueMaximumHealth": 700},
                  {"level": 32, "redMaximumHealth": 900}
                ]
            """.trimIndent(),
        )
        val repository = GameBalanceRepository {
            ByteArrayInputStream(customized.toByteArray())
        }
        val overrides = assertIs<GameBalanceRead.Available>(repository.load())
            .balance.config.runtime.campaign.fortressHealthOverrides

        assertEquals(2, overrides.size)
        assertEquals(31, overrides[0].level)
        assertEquals(700, overrides[0].blueMaximumHealth)
        assertEquals(null, overrides[0].redMaximumHealth)
        assertEquals(32, overrides[1].level)
        assertEquals(null, overrides[1].blueMaximumHealth)
        assertEquals(900, overrides[1].redMaximumHealth)
    }

    @Test
    fun invalidCampaignFortressHealthOverridesFailBalanceLoading() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalidOverrides = listOf(
            """[{"level": 31, "blueMaximumHealth": 700}, {"level": 31, "redMaximumHealth": 800}]""",
            """[{"level": 31, "blueMaximumHealth": null, "redMaximumHealth": 800}]""",
            """[{"level": 31.5, "blueMaximumHealth": 700}]""",
            """[{"level": 101, "redMaximumHealth": 800}]""",
            """[{"level": 31, "blueMaximumHealth": 0}]""",
            """[{"level": 31, "blueMaximumHealth": 1.5}]""",
            """[{"level": 31, "blueMaximumHealth": 2147483648}]""",
            """[{"level": 31, "blueMaximumHealth": "700"}]""",
            """[{"level": 31, "blueMaximumHealth": 700, "extra": 1}]""",
            """[{"level": 31}]""",
        )

        invalidOverrides.forEach { overrides ->
            val invalid = replaceCampaignFortressHealthOverrides(
                bundled,
                overrides,
            )
            val repository = GameBalanceRepository {
                ByteArrayInputStream(invalid.toByteArray())
            }

            assertEquals(
                GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
                repository.load(),
                overrides,
            )
        }
    }

    private fun replaceCampaignFortressHealthOverrides(
        content: String,
        overridesJson: String,
    ): String {
        val pattern = Regex(""""fortressHealthOverrides"\s*:\s*\[[\s\S]*?\]""")
        require(pattern.containsMatchIn(content)) {
            "Bundled campaign fortress health overrides field is missing"
        }
        return pattern.replaceFirst(content, "\"fortressHealthOverrides\": $overridesJson")
    }

    @Test
    fun unsupportedBalanceSchemaIsRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalid = bundled.replace("\"schemaVersion\": 7,", "\"schemaVersion\": 6,")
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
    fun invalidCampaignLifeBalanceIsRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalidDocuments = listOf(
            bundled.replace("\"initialLives\": 5", "\"initialLives\": 6"),
            bundled.replace("\"maximumLives\": 5", "\"maximumLives\": 0"),
            bundled.replace(
                "\"regenerationIntervalMilliseconds\": 900000",
                "\"regenerationIntervalMilliseconds\": 0",
            ),
        )

        invalidDocuments.forEach { invalid ->
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
        val changed = content + "\n"
        val first = assertIs<GameBalanceRead.Available>(repository(content).load()).balance
        val second = assertIs<GameBalanceRead.Available>(repository(changed).load()).balance

        assertNotEquals(first.contentHash, second.contentHash)
    }
}
