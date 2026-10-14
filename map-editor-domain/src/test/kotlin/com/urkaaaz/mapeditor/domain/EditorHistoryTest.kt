package com.urkaaaz.mapeditor.domain

import com.urkaaaz.contracts.*
import kotlin.test.*

class EditorHistoryTest {
    @Test
    fun defaultDocumentIsImmediatelyLockableAndUsesFixedWorldDimensions() {
        val history = EditorHistory(EditorDocumentFactory.newMap("default", "green_frontier"))
        assertIs<EditorResult.Success>(history.execute(EditorCommand.LockTerrain))
        assertEquals(7_200, history.current.map.world.width)
        assertEquals(4_800, history.current.map.world.height)
        assertEquals(3_180f, history.current.map.terrain.samples[84].height)
    }

    @Test
    fun terrainLocksAndDecorationCannotUnlockIt() {
        val history = EditorHistory(EditorDocumentFactory.newMap("test", "green_frontier"))
        assertIs<EditorResult.Success>(history.execute(EditorCommand.LockTerrain))
        val placed = history.execute(EditorCommand.PlaceMapObject("terrain_green_frontier_tree_00", 1_200f, 3_180f))
        assertIs<EditorResult.Success>(placed)
        assertEquals(EditorPhase.DECORATION_EDITING, history.current.phase)
        assertEquals(1, history.current.map.decorations.size)
    }

    @Test
    fun deletedIdsAreNeverReused() {
        val history = EditorHistory(EditorDocumentFactory.newMap("test", "green_frontier"))
        history.execute(EditorCommand.LockTerrain)
        val first = assertIs<EditorResult.Success>(history.execute(EditorCommand.PlaceMapObject("tree", 1_200f, 3_180f)))
        val id = first.document.map.decorations.single().id
        history.execute(EditorCommand.RemoveMapObject(id))
        val second = assertIs<EditorResult.Success>(history.execute(EditorCommand.PlaceMapObject("tree", 1_200f, 3_180f)))
        assertNotEquals(id, second.document.map.decorations.single().id)
    }

    @Test
    fun randomTerrainKeepsFortressFoundationsFlatAtTheirAssignedHeights() {
        val history = EditorHistory(EditorDocumentFactory.newMap("random", "green_frontier"))
        val result = assertIs<EditorResult.Success>(
            history.execute(EditorCommand.GenerateRandomTerrain(seed = 42L)),
        )
        assertEquals(3_180f, result.document.map.terrain.samples[10].height)
        assertEquals(3_180f, result.document.map.terrain.samples[27].height)
        assertEquals(3_180f, result.document.map.terrain.samples[153].height)
        assertEquals(3_180f, result.document.map.terrain.samples[170].height)
    }
}
