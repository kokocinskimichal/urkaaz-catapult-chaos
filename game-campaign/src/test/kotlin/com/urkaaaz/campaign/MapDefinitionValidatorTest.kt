package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapWorldDimensions
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

        assertTrue(MapDefinitionValidator.validate(definition).isValid)
        assertEquals(181, definition.terrain.samples.size)
        assertEquals(0f, definition.terrain.samples.first().x)
        assertEquals(7_200f, definition.terrain.samples.last().x)
        assertEquals(0, definition.decorations.size)
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
    fun everyCampaignLevelExplicitlyUsesBundledDemoMapWithoutChangingSettings() {
        val registry = CampaignLevelRegistry.default()
        val levels = (1..CampaignLevelDefinition.MAX_LEVELS).map(registry::level)

        assertTrue(levels.all { it.mapDefinition.mapId == "demo-map" })
        assertNotEquals(levels[0].availableUnits, levels[99].availableUnits)
        assertEquals(levels[0].mapDefinition.terrain.samples, levels[99].mapDefinition.terrain.samples)
    }
}
