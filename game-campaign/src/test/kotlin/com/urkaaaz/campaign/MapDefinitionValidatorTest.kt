package com.urkaaaz.campaign

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
    @Test
    fun bundledDemoMapIsValidAndCoversTheFixedWorld() {
        val definition = DemoLevelDefinitions.default.mapDefinition

        val result = MapDefinitionValidator.validate(definition)

        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(181, definition.terrain.samples.size)
        assertEquals(0f, definition.terrain.samples.first().x)
        assertEquals(7_200f, definition.terrain.samples.last().x)
        assertEquals(0, definition.decorations.size)
        assertEquals(1f, definition.initialCameraZoom)
    }

    @Test
    fun importedChapterOneLevelThreeMapIsValidAndUsesItsAuthoredData() {
        val definition = CampaignLevelDefinition.forLevel(3).mapDefinition

        assertTrue(MapDefinitionValidator.validate(definition).isValid)
        assertEquals("chapter1-level3", definition.mapId)
        assertEquals("green_frontier", definition.biome)
        assertEquals(181, definition.terrain.samples.size)
        assertTrue(definition.terrain.samples.all { it.height == 3_180f })
        assertEquals(240f, definition.fortresses.first { it.owner.name == "BLUE" }.halfWidth)
        assertEquals(240f, definition.fortresses.first { it.owner.name == "RED" }.halfWidth)
        assertEquals("terrain_green_frontier_tree_00", definition.decorations.single().assetKey)
        assertEquals(4_748.3364f, definition.decorations.single().x, 0.001f)
        assertEquals(1L, definition.generationMetadata?.seed)
        assertEquals("web", definition.generationMetadata?.generatorVersion)
        assertEquals("map-editor-web/0.1.0", definition.generationMetadata?.authoringVersion)
        assertEquals("PLAYER_FORTRESS", definition.initialCameraAnchor.name)
        assertEquals(1.3f, definition.initialCameraZoom)
    }

    @Test
    fun importedChapterOneLevelFourMapIsValidAndPreservesAuthoredTerrainAndRelics() {
        val definition = CampaignLevelDefinition.forLevel(4).mapDefinition
        val relics = definition.decorations.filter { it.relicSize != null }

        assertTrue(MapDefinitionValidator.validate(definition).isValid)
        assertEquals("chapter1-level4", definition.mapId)
        assertEquals("green_frontier", definition.biome)
        assertEquals(181, definition.terrain.samples.size)
        assertEquals(2_870f, definition.terrain.samples.first().height)
        assertEquals(3_390f, definition.terrain.samples.last().height)
        assertTrue(
            abs(definition.terrain.samples.single { it.x == 5_040f }.height - 3_179.936f) < 0.01f,
        )
        assertTrue(
            abs(definition.terrain.samples.single { it.x == 6_080f }.height - 3_386.802f) < 0.01f,
        )
        assertEquals(2_870f, definition.fortresses.single { it.owner.name == "BLUE" }.foundationHeight)
        assertEquals(3_390f, definition.fortresses.single { it.owner.name == "RED" }.foundationHeight)
        assertEquals(10, definition.decorations.size)
        assertEquals(2, relics.size)
        assertTrue(relics.any { abs(it.x - 811.189f) < 0.01f })
        assertTrue(relics.any { abs(it.x - 4_197.316f) < 0.01f })
        assertEquals(1L, definition.generationMetadata?.seed)
        assertEquals("web", definition.generationMetadata?.generatorVersion)
        assertEquals("map-editor-web/0.1.0", definition.generationMetadata?.authoringVersion)
        assertEquals("PLAYER_FORTRESS", definition.initialCameraAnchor.name)
        assertEquals(1f, definition.initialCameraZoom)
    }

    @Test
    fun groundedDecorationsMayBeOffsetFromTerrainAndPlacedOnSlopes() {
        val source = DemoLevelDefinitions.default.mapDefinition
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
        val source = DemoLevelDefinitions.default.mapDefinition
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
        val definition = DemoLevelDefinitions.default.mapDefinition
            .copy(world = MapWorldDimensions(width = 7_199, height = 4_800))

        val result = MapDefinitionValidator.validate(definition)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.path == "world.width" })
    }

    @Test
    fun rejectsUnavailableCameraAnchorsAndUnsupportedZoom() {
        val definition = DemoLevelDefinitions.default.mapDefinition
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
        val source = DemoLevelDefinitions.default.mapDefinition
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
    fun chapterOneLevelsThreeAndFourUseTheirImportedMaps() {
        val registry = CampaignLevelRegistry.default()
        val levels = (1..CampaignLevelDefinition.MAX_LEVELS).map(registry::level)

        assertEquals("chapter1-level3", levels[2].mapDefinition.mapId)
        assertEquals("chapter1-level4", levels[3].mapDefinition.mapId)
        assertTrue(levels.filterIndexed { index, _ -> index != 2 && index != 3 }
            .all { it.mapDefinition.mapId == "demo-map" })
        assertNotEquals(levels[0].availableUnits, levels[99].availableUnits)
        assertEquals(levels[0].mapDefinition.terrain.samples, levels[1].mapDefinition.terrain.samples)
        assertNotEquals(levels[0].mapDefinition.terrain.samples, levels[2].mapDefinition.terrain.samples)
    }
}
