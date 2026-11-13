package com.urkaaaz.application.balance

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitIds
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        assertEquals(8, result.balance.config.schemaVersion)
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
                contentAvailability =
                    result.balance.config.runtime.campaign.contentAvailability,
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
    fun campaignContentAvailabilityLoadsCanonicalDefaultsAndNeverEntries() {
        val result = assertIs<GameBalanceRead.Available>(GameBalanceRepository.bundled.load())
        val availability = result.balance.config.runtime.campaign.contentAvailability

        assertEquals(UnitIds.all, availability.units.mapTo(mutableSetOf()) { it.unitId })
        assertEquals(
            AmmunitionType.entries.toSet(),
            availability.ammunition.mapTo(mutableSetOf()) { it.ammunitionType },
        )
        assertEquals(
            SpellType.entries.toSet(),
            availability.spells.mapTo(mutableSetOf()) { it.spellType },
        )
        assertEquals(
            mapOf(
                UnitIds.DEFENDER to 1,
                UnitIds.SAPPER to 5,
                UnitIds.DEMOLISHER to 8,
                UnitIds.RAGING_BOAR to 11,
                UnitIds.SLINGMASTER to 13,
            ),
            availability.units.associate { it.unitId to it.availableFromLevel },
        )
        assertEquals(
            mapOf(
                AmmunitionType.ROCK to 1,
                AmmunitionType.SIEGE_BOMB to 1,
                AmmunitionType.POWDER_BARREL to 10,
                AmmunitionType.FIRE_RAIN to 31,
                AmmunitionType.PLAGUE_CAULDRON to 41,
            ),
            availability.ammunition.associate { it.ammunitionType to it.availableFromLevel },
        )
        assertEquals(
            mapOf(
                SpellType.FORTRESS_SHIELD to 1,
                SpellType.HASTE to 6,
                SpellType.HEAL to 8,
                SpellType.ICE_TRAP to 11,
                SpellType.RUNIC_BASTION to 21,
                SpellType.CATAPULT_LOCK to null,
                SpellType.SLOWING_FIELD to null,
                SpellType.VAMPIRIC_BANNER to null,
                SpellType.STONE_SKIN to null,
                SpellType.SIEGE_FRENZY to null,
                SpellType.BERSERKER to null,
            ),
            availability.spells.associate { it.spellType to it.availableFromLevel },
        )
        assertEquals(null, availability.availableFromLevel(SpellType.BERSERKER))
        assertEquals(setOf(UnitIds.DEFENDER), availability.availableUnitsAt(1))
        assertEquals(
            setOf(SpellType.FORTRESS_SHIELD),
            availability.availableSpellsAt(1),
        )
    }

    @Test
    fun campaignContentAvailabilityLoadsEditedUnlockLevels() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val customized = updateCampaignContentAvailability(bundled) { availability ->
            val units = availability.getValue("units").jsonArray.map { it.jsonObject }
                .map { entry ->
                    if (entry.getValue("unitId").jsonPrimitive.content == UnitIds.SAPPER) {
                        JsonObject(entry + ("availableFromLevel" to JsonPrimitive(6)))
                    } else {
                        entry
                    }
                }
            val spells = availability.getValue("spells").jsonArray.map { it.jsonObject }
                .map { entry ->
                    if (entry.getValue("spellType").jsonPrimitive.content == SpellType.HASTE.name) {
                        JsonObject(entry + ("availableFromLevel" to JsonPrimitive(9)))
                    } else {
                        entry
                    }
                }
            JsonObject(
                availability +
                    ("units" to JsonArray(units)) +
                    ("spells" to JsonArray(spells)),
            )
        }
        val availability = assertIs<GameBalanceRead.Available>(
            GameBalanceRepository { ByteArrayInputStream(customized.toByteArray()) }.load(),
        ).balance.config.runtime.campaign.contentAvailability

        assertEquals(
            6,
            availability.units.single { it.unitId == UnitIds.SAPPER }.availableFromLevel,
        )
        assertEquals(9, availability.availableFromLevel(SpellType.HASTE))
        assertEquals(setOf(UnitIds.DEFENDER), availability.availableUnitsAt(5))
        assertEquals(
            setOf(UnitIds.DEFENDER, UnitIds.SAPPER),
            availability.availableUnitsAt(6),
        )
    }

    @Test
    fun invalidCampaignContentAvailabilityFailsBalanceLoading() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalidDocuments = listOf(
            updateCampaignContentAvailability(bundled) { availability ->
                JsonObject(
                    availability + (
                        "units" to JsonArray(
                            availability.getValue("units").jsonArray.dropLast(1),
                        )
                    ),
                )
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val units = availability.getValue("units").jsonArray
                JsonObject(availability + ("units" to JsonArray(units + units.first())))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val ammunition = availability.getValue("ammunition").jsonArray.map { it.jsonObject }
                    .map { entry ->
                        if (entry.getValue("ammunitionType").jsonPrimitive.content == "ROCK") {
                            JsonObject(entry + ("ammunitionType" to JsonPrimitive("UNKNOWN_AMMO")))
                        } else {
                            entry
                        }
                    }
                JsonObject(availability + ("ammunition" to JsonArray(ammunition)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val units = availability.getValue("units").jsonArray.map { it.jsonObject }
                    .map { entry ->
                        if (entry.getValue("unitId").jsonPrimitive.content == UnitIds.SAPPER) {
                            JsonObject(entry + ("availableFromLevel" to JsonPrimitive(101)))
                        } else {
                            entry
                        }
                    }
                JsonObject(availability + ("units" to JsonArray(units)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val units = availability.getValue("units").jsonArray.map { it.jsonObject }
                    .map { entry ->
                        if (entry.getValue("unitId").jsonPrimitive.content == UnitIds.SAPPER) {
                            JsonObject(entry + ("availableFromLevel" to JsonPrimitive(1.5)))
                        } else {
                            entry
                        }
                    }
                JsonObject(availability + ("units" to JsonArray(units)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                    val units = availability.getValue("units").jsonArray.map { it.jsonObject }
                        .map { entry ->
                            if (entry.getValue("unitId").jsonPrimitive.content == UnitIds.SAPPER) {
                                JsonObject(entry - "availableFromLevel")
                            } else {
                                entry
                            }
                        }
                    JsonObject(availability + ("units" to JsonArray(units)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val spells = availability.getValue("spells").jsonArray.map { it.jsonObject }
                    .map { entry ->
                        if (entry.getValue("spellType").jsonPrimitive.content == SpellType.HASTE.name) {
                            JsonObject(entry + ("availableFromLevel" to JsonPrimitive(5)))
                        } else {
                            entry
                        }
                    }
                JsonObject(availability + ("spells" to JsonArray(spells)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                    val spells = availability.getValue("spells").jsonArray.map { it.jsonObject }
                        .map { entry ->
                            if (entry.getValue("spellType").jsonPrimitive.content == SpellType.HASTE.name) {
                                JsonObject(entry + ("availableFromLevel" to JsonNull))
                            } else {
                                entry
                            }
                        }
                    JsonObject(availability + ("spells" to JsonArray(spells)))
            },
            updateCampaignContentAvailability(bundled) { availability ->
                val spells = availability.getValue("spells").jsonArray.map { it.jsonObject }
                    .map { entry ->
                        if (entry.getValue("spellType").jsonPrimitive.content == SpellType.BERSERKER.name) {
                            JsonObject(entry - "availableFromLevel")
                        } else {
                            entry
                        }
                    }
                JsonObject(availability + ("spells" to JsonArray(spells)))
            },
        )

        invalidDocuments.forEach { content ->
            assertEquals(
                GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA),
                GameBalanceRepository {
                    ByteArrayInputStream(content.toByteArray())
                }.load(),
            )
        }
    }

    @Test
    fun unknownFieldsAreRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalid = bundled.replace(
            "\"schemaVersion\": 8,",
            "\"schemaVersion\": 8,\n  \"extra\": true,",
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

    private fun updateCampaignContentAvailability(
        content: String,
        transform: (JsonObject) -> JsonObject,
    ): String {
        val root = Json.parseToJsonElement(content).jsonObject
        val runtime = root.getValue("runtime").jsonObject
        val campaign = runtime.getValue("campaign").jsonObject
        val availability = campaign.getValue("contentAvailability").jsonObject
        val updatedCampaign = JsonObject(
            campaign + ("contentAvailability" to transform(availability)),
        )
        val updatedRuntime = JsonObject(runtime + ("campaign" to updatedCampaign))
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("runtime" to updatedRuntime)))
    }

    @Test
    fun unsupportedBalanceSchemaIsRejected() {
        val bundled = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val invalid = bundled.replace("\"schemaVersion\": 8,", "\"schemaVersion\": 7,")
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
