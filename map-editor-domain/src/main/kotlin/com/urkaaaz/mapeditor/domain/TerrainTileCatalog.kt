package com.urkaaaz.mapeditor.domain

import com.urkaaaz.contracts.EdgeProfile
import com.urkaaaz.contracts.TerrainTileGeometry

data class TerrainTilePreset(
    val id: String,
    val displayName: String,
    val leftProfile: EdgeProfile,
    val rightProfile: EdgeProfile,
    val heightSamples: List<Float>,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(heightSamples.size >= 2)
    }

    fun geometry(size: Float = DEFAULT_TILE_SIZE): TerrainTileGeometry =
        TerrainTileGeometry(
            widthWorldUnits = size,
            heightWorldUnits = size,
            leftProfile = leftProfile,
            rightProfile = rightProfile,
            heightSamples = heightSamples,
        )

    override fun toString(): String = displayName

    companion object {
        const val DEFAULT_TILE_SIZE = 20f
    }
}

object TerrainTileCatalog {
    val presets: List<TerrainTilePreset> = listOf(
        TerrainTilePreset("flat", "Flat", EdgeProfile.FLAT, EdgeProfile.FLAT, listOf(0f, 0f)),
        TerrainTilePreset("gentle-up", "Łagodne wzniesienie", EdgeProfile.FLAT, EdgeProfile.SLOPE_UP, listOf(0f, -1f)),
        TerrainTilePreset("gentle-down", "Łagodny spadek", EdgeProfile.SLOPE_DOWN, EdgeProfile.FLAT, listOf(0f, 1f)),
        TerrainTilePreset("slope-up", "Wzniesienie", EdgeProfile.FLAT, EdgeProfile.SLOPE_UP, listOf(0f, -2f)),
        TerrainTilePreset("slope-down", "Spadek", EdgeProfile.SLOPE_DOWN, EdgeProfile.FLAT, listOf(0f, 2f)),
        TerrainTilePreset("steep-up", "Strome wzniesienie", EdgeProfile.FLAT, EdgeProfile.SLOPE_UP, listOf(0f, -4f)),
        TerrainTilePreset("steep-down", "Stromy spadek", EdgeProfile.SLOPE_DOWN, EdgeProfile.FLAT, listOf(0f, 4f)),
        TerrainTilePreset("ridge", "Grzbiet", EdgeProfile.SLOPE_UP, EdgeProfile.SLOPE_DOWN, listOf(0f, -2f, 0f)),
        TerrainTilePreset("valley", "Dolina", EdgeProfile.SLOPE_DOWN, EdgeProfile.SLOPE_UP, listOf(0f, 2f, 0f)),
        TerrainTilePreset("plateau", "Płaskowyż", EdgeProfile.SLOPE_UP, EdgeProfile.SLOPE_DOWN, listOf(-2f, -2f, -2f)),
    )

    fun byId(id: String): TerrainTilePreset =
        presets.firstOrNull { it.id == id }
            ?: error("Unknown terrain tile preset: $id")
}
