package com.urkaaaz.campaign

import com.urkaaaz.campaign.chapter1.chapter1Level1MapDefinition
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapObjectPlacement
import com.urkaaaz.contracts.MapObjectType
import com.urkaaaz.contracts.ObjectCollision
import com.urkaaaz.contracts.RelicSize
import com.urkaaaz.contracts.TerrainProfile
import com.urkaaaz.contracts.TerrainSample
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapDefinitionValidatorTest {
    @Test
    fun chapterOneLevelOneIsAcceptedByProductionValidator() {
        assertTrue(MapDefinitionValidator.validate(chapter1Level1MapDefinition).isValid)
    }

    @Test
    fun terrainSlopeIsRejectedWithTypedError() {
        val invalid = chapter1Level1MapDefinition.copy(
            terrain = TerrainProfile(
                samples = listOf(
                    TerrainSample(0f, 3_650f),
                    TerrainSample(40f, 3_900f),
                ),
            ),
        )

        val result = MapDefinitionValidator.validate(invalid)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is MapValidationError.TerrainSlopeExceeded })
    }

    @Test
    fun environmentalDecorationOnSlopeIsRejected() {
        val decorationX = chapter1Level1MapDefinition.worldWidth / 2f
        val decorationY = 3_700f
        val invalid = chapter1Level1MapDefinition.copy(
            objects = chapter1Level1MapDefinition.objects + MapObjectPlacement(
                id = "synthetic-slope-decoration",
                type = MapObjectType.DECORATION,
                x = decorationX,
                y = decorationY,
                assetKey = "decoration.chapter-1.tree",
            ),
            terrain = TerrainProfile(
                samples = listOf(
                    TerrainSample(decorationX - 40f, decorationY),
                    TerrainSample(decorationX, decorationY + 100f),
                    TerrainSample(decorationX + 40f, decorationY),
                ),
            ),
        )

        val result = MapDefinitionValidator.validate(invalid)

        assertTrue(result.errors.any { it is MapValidationError.DecorationOnSteepSlope })
    }

    @Test
    fun registryExposesScenarioWithTheSameMapDefinition() {
        val registry = CampaignLevelRegistry.default()
        val level = registry.level(1)
        val scenario = registry.scenario(1)

        assertTrue(registry.contains(1))
        assertTrue(scenario.mapDefinition === level.mapDefinition)
    }

    @Test
    fun campaignLevelsHaveDeterministicallyDifferentTerrainProfiles() {
        val registry = CampaignLevelRegistry.default()
        val first = registry.mapDefinition(1)
        val second = registry.mapDefinition(2)
        val laterChapter = registry.mapDefinition(11)

        assertTrue(first.terrain.samples != second.terrain.samples)
        assertTrue(second.terrain.samples != laterChapter.terrain.samples)
        assertTrue(second.terrain.samples.maxOf { it.height } -
            second.terrain.samples.minOf { it.height } > 100f)
        assertTrue(first.terrain.samples.maxOf { it.height } -
            first.terrain.samples.minOf { it.height } > 950f)
        assertTrue(second.terrain.samples.zipWithNext().all {
            kotlin.math.abs(it.second.height - it.first.height) <=
                MapDefinitionValidator.MAX_TERRAIN_DELTA
        })
        assertTrue(first.fortresses.map { it.foundationHeight } !=
            second.fortresses.map { it.foundationHeight })
    }

    @Test
    fun relicFootprintMustBeFullyInsideSoil() {
        val relic = chapter1Level1MapDefinition.objects.first { it.relicSize == RelicSize.SMALL }
        val invalid = chapter1Level1MapDefinition.copy(
            objects = chapter1Level1MapDefinition.objects.map {
                if (it.id == relic.id) it.copy(y = it.y - 100f) else it
            },
        )

        val result = MapDefinitionValidator.validate(invalid)

        assertTrue(result.errors.any {
            it.path == "objects[0].footprint" &&
                it.message.contains("fully inside soil")
        })
    }

    @Test
    fun relicCannotOverlapCatapultFoundation() {
        val relic = chapter1Level1MapDefinition.objects.first { it.relicSize == RelicSize.SMALL }
        val invalid = chapter1Level1MapDefinition.copy(
            objects = chapter1Level1MapDefinition.objects.map {
                if (it.id == relic.id) {
                    it.copy(
                        x = 450f,
                        y = 3700f,
                        type = MapObjectType.DECORATION,
                        collision = ObjectCollision.NONE,
                    )
                } else {
                    it
                }
            },
        )

        val result = MapDefinitionValidator.validate(invalid)

        assertTrue(result.errors.any { it is MapValidationError.DecorationOnFoundation })
    }
}
