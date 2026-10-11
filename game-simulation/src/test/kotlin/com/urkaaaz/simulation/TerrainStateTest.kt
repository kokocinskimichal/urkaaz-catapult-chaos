package com.urkaaaz.simulation

import com.urkaaaz.domain.WorldPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainStateTest {
    @Test
    fun craterLowersTerrainHeightAndIncrementsRevision() {
        val terrain = TerrainState(baselineHeight = 500f)
            .deform(WorldPosition(304f, 500f), radius = 100f, depth = 40f)

        assertEquals(1L, terrain.revision)
        assertEquals(540f, terrain.heightAt(304f))
        assertTrue(terrain.heightAt(100f) <= terrain.heightAt(300f))
    }

    @Test
    fun protectedFoundationRemainsOnOriginalHeightmap() {
        val terrain = TerrainState(baselineHeight = 500f)
            .protectRange(centerX = 300f, halfWidth = 60f)
            .deform(WorldPosition(300f, 500f), radius = 100f, depth = 40f)

        assertEquals(500f, terrain.heightAt(300f))
        assertTrue(terrain.heightAt(220f) > 500f)
    }
}
