package com.urkaaaz.campaign

import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MapDecoration
import com.urkaaaz.contracts.MapWorldDimensions
import com.urkaaaz.contracts.RelicSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapDefinitionValidatorTest {
    private val campaignBalance = GameBalanceFixtures.balance.runtime.campaign

    @Test
    fun bundledDemoMapIsValidAndCoversTheFixedWorld() {
        val definition = DemoLevelDefinitions.default(campaignBalance).mapDefinition

        val result = MapDefinitionValidator.validate(definition)

        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(181, definition.terrain.samples.size)
        assertEquals(0f, definition.terrain.samples.first().x)
        assertEquals(7_200f, definition.terrain.samples.last().x)
        assertEquals(0, definition.decorations.size)
        assertEquals(1f, definition.initialCameraZoom)
    }

    @Test
    fun generatedChapterOneLevelThreeMapIsValidAndUsesItsSavedRandomMapData() {
        val definition = CampaignLevelDefinition.forLevel(3).mapDefinition

        assertTrue(MapDefinitionValidator.validate(definition).isValid)
        assertEquals("chapter1-level3", definition.mapId)
        assertEquals("green_frontier", definition.biome)
        assertEquals(181, definition.terrain.samples.size)
        assertTrue(
            abs(definition.fortresses.single { it.owner.name == "BLUE" }.foundationHeight - 3_307.1512f) < 0.01f,
        )
        assertTrue(
            abs(definition.fortresses.single { it.owner.name == "RED" }.foundationHeight - 3_169.4959f) < 0.01f,
        )
        assertEquals(240f, definition.fortresses.first { it.owner.name == "BLUE" }.halfWidth)
        assertEquals(240f, definition.fortresses.first { it.owner.name == "RED" }.halfWidth)
        assertEquals(4, definition.decorations.size)
        assertEquals(1_327_317_795L, definition.generationMetadata?.seed)
        assertEquals("studio-random-map/1", definition.generationMetadata?.generatorVersion)
        assertEquals("urkaaz-studio/0.1.0", definition.generationMetadata?.authoringVersion)
        assertEquals("PLAYER_FORTRESS", definition.initialCameraAnchor.name)
        assertEquals(1f, definition.initialCameraZoom)
    }

    @Test
    fun generatedChapterOneLevelFourMapIsValidAndUsesItsSavedRandomMapData() {
        val definition = CampaignLevelDefinition.forLevel(4).mapDefinition
        val relics = definition.decorations.filter { it.relicSize != null }

        assertTrue(MapDefinitionValidator.validate(definition).isValid)
        assertEquals("chapter1-level4", definition.mapId)
        assertEquals("green_frontier", definition.biome)
        assertEquals(181, definition.terrain.samples.size)
        assertTrue(
            abs(definition.fortresses.single { it.owner.name == "BLUE" }.foundationHeight - 2_784.9663f) < 0.01f,
        )
        assertTrue(
            abs(definition.fortresses.single { it.owner.name == "RED" }.foundationHeight - 3_016.5233f) < 0.01f,
        )
        assertEquals(5, definition.decorations.size)
        assertTrue(relics.isEmpty())
        assertEquals(3_781_372_522L, definition.generationMetadata?.seed)
        assertEquals("studio-random-map/1", definition.generationMetadata?.generatorVersion)
        assertEquals("urkaaz-studio/0.1.0", definition.generationMetadata?.authoringVersion)
        assertEquals("PLAYER_FORTRESS", definition.initialCameraAnchor.name)
        assertEquals(1f, definition.initialCameraZoom)
    }

    @Test
    fun groundedDecorationsMayBeOffsetFromTerrainAndPlacedOnSlopes() {
        val source = DemoLevelDefinitions.default(campaignBalance).mapDefinition
        val terrain = source.terrain.copy(
            samples = source.terrain.samples.map { sample ->
                if (sample.x == 3_560f) sample.copy(height = 2_880f) else sample
            },
        )
        val definition = source.copy(
            terrain = terrain,
            decorations = listOf(
                MapDecoration(
                    id = "tree-on-slope",
                    assetKey = "terrain_green_frontier_tree_00",
                    x = 3_600f,
                    y = 3_000f,
                    scale = 1f,
                    rotationDegrees = 0f,
                ),
            ),
        )

        val result = MapDefinitionValidator.validate(definition)

        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun acceptsSharedMediumRelicsForAnyBiome() {
        val source = DemoLevelDefinitions.default(campaignBalance).mapDefinition
        val definition = source.copy(
            biome = "frostbound",
            decorations = listOf(
                MapDecoration(
                    id = "shared-medium-relic",
                    assetKey = "relic.shared.medium.broken_orc_statue",
                    x = 3_600f,
                    y = 3_300f,
                    scale = 1f,
                    rotationDegrees = 0f,
                    relicSize = RelicSize.MEDIUM,
                ),
            ),
        )

        val result = MapDefinitionValidator.validate(definition)

        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun rejectsWorldDimensionsThatDifferFromTheCanonicalWorld() {
        val definition = DemoLevelDefinitions.default(campaignBalance).mapDefinition
            .copy(world = MapWorldDimensions(width = 7_199, height = 4_800))

        val result = MapDefinitionValidator.validate(definition)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.path == "world.width" })
    }

    @Test
    fun rejectsUnavailableCameraAnchorsAndUnsupportedZoom() {
        val definition = DemoLevelDefinitions.default(campaignBalance).mapDefinition
        val invalid = definition.copy(
            fortresses = definition.fortresses.filter { it.owner.name == "RED" },
            initialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
            initialCameraZoom = 4.1f,
            generationMetadata = definition.generationMetadata?.copy(authoringVersion = " "),
        )

        val result = MapDefinitionValidator.validate(invalid)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.path == "initialCameraAnchor" })
        assertTrue(result.errors.any { it.path == "initialCameraZoom" })
        assertTrue(result.errors.any { it.path == "generationMetadata.authoringVersion" })
    }

    @Test
    fun strictDecoderRejectsUnknownFieldsAndEnums() {
        val source = DemoLevelDefinitions.default(campaignBalance).mapDefinition
        val repository = MapDefinitionRepository {
            """{"schemaVersion":1,"mapId":"demo-map","biome":"unknown","world":{"width":7200,"height":4800},"terrain":{"sampleStep":40,"heightSamples":[]},"protectedZones":[],"fortresses":[],"decorations":[],"unexpected":true}"""
                .byteInputStream()
        }

        val result = repository.load(source.mapId)

        val failure = assertIs<MapLoadResult.Failure>(result)
        assertTrue(failure.message.contains("unexpected") || failure.message.contains("biome"))
    }

    @Test
    fun missingMapAndInvalidMapIdsAreExplicitFailures() {
        assertIs<MapLoadResult.Failure>(MapDefinitionRepository { null }.load("missing-map"))
        assertEquals(
            "invalid map id",
            (MapDefinitionRepository { null }.load("../demo-map") as MapLoadResult.Failure).message,
        )
    }

    @Test
    fun chapterOneBundledMapsAreUsedAndMissingMapsFallBackToDemo() {
        val registry = CampaignLevelRegistry.default(campaignBalance)
        val levels = (1..CampaignLevelDefinition.MAX_LEVELS).map(registry::level)

        assertEquals("chapter1-level1", levels[0].mapDefinition.mapId)
        assertEquals("chapter1-level2", levels[1].mapDefinition.mapId)
        assertEquals("chapter1-level3", levels[2].mapDefinition.mapId)
        assertEquals("chapter1-level4", levels[3].mapDefinition.mapId)
        assertEquals("chapter1-level5", levels[4].mapDefinition.mapId)
        assertEquals("demo-map", levels[5].mapDefinition.mapId)
        assertNotEquals(levels[0].availableUnits, levels[99].availableUnits)
        assertNotEquals(levels[0].mapDefinition.terrain.samples, levels[1].mapDefinition.terrain.samples)
        assertNotEquals(levels[0].mapDefinition.terrain.samples, levels[2].mapDefinition.terrain.samples)
    }
}
