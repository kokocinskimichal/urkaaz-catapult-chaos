package com.urkaaaz.simulation

import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapGenerationMetadata
import com.urkaaaz.contracts.MapWorldDimensions
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
                    schemaVersion = 1,
                    mapId = "simulation-test-map",
                    biome = "green_frontier",
                    world = MapWorldDimensions(7_200, 4_800),
                    terrain = TerrainProfile(
                        sampleStep = 40f,
                        samples = (0..7_200 step 40).map {
                            TerrainSample(it.toFloat(), 3_650f + it / 720f)
                        },
                    ),
                    protectedTerrainZones = emptyList(),
                    fortresses = emptyList(),
                    decorations = emptyList(),
                    generationMetadata = MapGenerationMetadata(
                        seed = 7L,
                        generatorVersion = "test",
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
