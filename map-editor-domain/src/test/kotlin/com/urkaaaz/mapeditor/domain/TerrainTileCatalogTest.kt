package com.urkaaaz.mapeditor.domain

import com.urkaaaz.contracts.EdgeProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainTileCatalogTest {
    @Test
    fun containsTheCoreTerrainShapes() {
        assertEquals(
            setOf(
                "flat", "gentle-up", "gentle-down", "slope-up", "slope-down",
                "steep-up", "steep-down", "ridge", "valley", "plateau",
            ),
            TerrainTileCatalog.presets.map { it.id }.toSet(),
        )
    }

    @Test
    fun containsMultipleSlopeStrengths() {
        assertEquals(
            setOf("gentle-up", "slope-up", "steep-up", "gentle-down", "slope-down", "steep-down"),
            TerrainTileCatalog.presets.map { it.id }.toSet().intersect(
                setOf("gentle-up", "slope-up", "steep-up", "gentle-down", "slope-down", "steep-down"),
            ),
        )
    }

    @Test
    fun presetGeometryKeepsProfilesAndSamples() {
        val ridge = TerrainTileCatalog.byId("ridge")
        val geometry = ridge.geometry()

        assertEquals(EdgeProfile.SLOPE_UP, geometry.leftProfile)
        assertEquals(EdgeProfile.SLOPE_DOWN, geometry.rightProfile)
        assertTrue(geometry.heightSamples.size >= 2)
    }

    @Test
    fun downPresetsMoveTerrainDownInScreenCoordinates() {
        assertTrue(TerrainTileCatalog.byId("slope-down").heightSamples.last() > 0f)
        assertTrue(TerrainTileCatalog.byId("steep-down").heightSamples.last() > 0f)
        assertTrue(TerrainTileCatalog.byId("valley").heightSamples[1] > 0f)
    }
}
