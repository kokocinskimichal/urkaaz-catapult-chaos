package com.urkaaaz.simulation

import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapGenerationMetadata
import com.urkaaaz.contracts.TerrainProfile
import com.urkaaaz.contracts.TerrainSample
import com.urkaaaz.domain.WorldBounds
import kotlin.test.Test
import kotlin.test.assertEquals

class MapDefinitionSimulationTest {
    @Test
    fun simulationUsesMaterializedMapTerrainAndPreservesFoundation() {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("map-definition-test"),
            config = SimulationConfig(
                bounds = WorldBounds(7_200f, 4_800f),
                terrainBiome = "GREEN_FRONTIER",
                mapDefinition = MapDefinition(
                    levelId = "simulation-test-map",
                    schemaVersion = 1,
                    worldWidth = 80f,
                    worldHeight = 100f,
                    terrain = TerrainProfile(
                        samples = listOf(
                            TerrainSample(0f, 3_650f),
                            TerrainSample(40f, 3_660f),
                            TerrainSample(80f, 3_670f),
                        ),
                    ),
                    protectedTerrainZones = emptyList(),
                    fortresses = emptyList(),
                    catapults = emptyList(),
                    objects = emptyList(),
                    initialCameraAnchor = InitialCameraAnchor.CENTER,
                    generationMetadata = MapGenerationMetadata(
                        seed = 7L,
                        generatorVersion = "test",
                        authoringVersion = "test",
                        source = "simulation-test",
                    ),
                ),
            ),
        )

        simulation.start()
        val before = simulation.snapshot()
        simulation.advance(1_000)
        val after = simulation.snapshot()

        assertEquals(40f, before.terrain.sampleSpacing)
        assertEquals(3_650f, before.terrain.heightSamples.first())
        assertEquals(before.terrain.revision, after.terrain.revision)
    }
}
