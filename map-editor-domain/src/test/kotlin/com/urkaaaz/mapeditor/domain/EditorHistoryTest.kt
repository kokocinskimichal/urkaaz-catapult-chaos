package com.urkaaaz.mapeditor.domain

import com.urkaaaz.contracts.*
import kotlin.test.*

class EditorHistoryTest {
    @Test
    fun defaultDocumentIsImmediatelyLockableAndTilesUseTerrainHeight() {
        val history = EditorHistory(EditorDocumentFactory.newMap("default", "green_frontier"))
        assertIs<EditorResult.Success>(history.execute(EditorCommand.LockTerrain))
        assertEquals(0, history.current.map.terrainTiles.size)
        assertEquals(3648f, history.current.map.terrain.samples[84].height)
    }

    @Test
    fun terrainLocksAndDecorationCannotUnlockIt() {
        val history = EditorHistory(EditorDocumentFactory.newMap("test", "green_frontier"))
        assertIs<EditorResult.Success>(history.execute(EditorCommand.LockTerrain))
        val placed = history.execute(EditorCommand.PlaceMapObject("terrain_green_frontier_tree_00.png", 100f, 3648f))
        assertIs<EditorResult.Success>(placed)
        assertEquals(EditorPhase.DECORATION_EDITING, history.current.phase)
        assertIs<EditorResult.Failure>(history.execute(EditorCommand.PlaceTerrainTile(
            TerrainTilePlacement("tile", 0, 0, TerrainTileGeometry(40f, 40f, EdgeProfile.FLAT, EdgeProfile.FLAT, listOf(1f))),
        )))
    }

    @Test
    fun deletedIdsAreNeverReused() {
        val history = EditorHistory(EditorDocumentFactory.newMap("test", "green_frontier"))
        history.execute(EditorCommand.LockTerrain)
        val first = assertIs<EditorResult.Success>(history.execute(EditorCommand.PlaceMapObject("tree", 100f, 3648f)))
        val id = first.document.map.objects.single().id
        history.execute(EditorCommand.RemoveMapObject(id))
        val second = assertIs<EditorResult.Success>(history.execute(EditorCommand.PlaceMapObject("tree", 200f, 3648f)))
        assertNotEquals(id, second.document.map.objects.single().id)
    }

    @Test
    fun randomTerrainKeepsFortressFoundationsFlatAtTheirAssignedHeights() {
        val history = EditorHistory(EditorDocumentFactory.newMap("random", "green_frontier"))
        val result = assertIs<EditorResult.Success>(
            history.execute(EditorCommand.GenerateRandomTerrain(seed = 42L)),
        )
        assertEquals(3648f, result.document.map.terrain.samples[24].height)
        assertEquals(3648f, result.document.map.terrain.samples[34].height)
        assertEquals(3648f, result.document.map.terrain.samples[326].height)
        assertEquals(3648f, result.document.map.terrain.samples[336].height)
    }
}
