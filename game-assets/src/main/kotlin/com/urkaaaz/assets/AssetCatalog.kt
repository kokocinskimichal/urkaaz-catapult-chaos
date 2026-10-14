package com.urkaaaz.assets

import java.io.InputStream

enum class AssetCategory { TREE, ROCK, PROP, RELIC, CAP, TILE, SOIL, BACKGROUND, OTHER }

data class AssetKey(
    val biome: String,
    val category: AssetCategory,
    val variant: String,
    val fileName: String,
) {
    val stableKey: String get() = "terrain_${biome}_${category.name.lowercase()}_$variant"
}

object TerrainAssetNaming {
    private val pattern = Regex("""terrain_([^_]+(?:_[^_]+)*)_(tree|rock|prop|relic|cap|tile|soil)_([^.]+)\.png""")

    fun parse(fileName: String): AssetKey? {
        val match = pattern.matchEntire(fileName) ?: return null
        val biome = match.groupValues[1]
        val category = AssetCategory.valueOf(match.groupValues[2].uppercase())
        return AssetKey(biome, category, match.groupValues[3], fileName)
    }
}

class AssetCatalog(
    private val resourceRoot: String = "terrain",
    private val classLoader: ClassLoader = AssetCatalog::class.java.classLoader,
) {
    fun assetsForBiome(biome: String): List<AssetKey> =
        index().filter { it.biome == biome }.sortedWith(compareBy({ it.category }, { it.variant }))

    fun find(fileName: String): AssetKey? = TerrainAssetNaming.parse(fileName)

    fun open(asset: AssetKey): InputStream? =
        classLoader.getResourceAsStream("$resourceRoot/${asset.biome}/${asset.fileName}")
            ?: classLoader.getResourceAsStream("$resourceRoot/${asset.fileName}")

    fun availableBiomes(): Set<String> = index().mapTo(sortedSetOf()) { it.biome }

    fun allAssets(): List<AssetKey> = index().sortedWith(compareBy({ it.biome }, { it.category }, { it.variant }))

    private fun index(): List<AssetKey> {
        // Resource enumeration is intentionally avoided: it is not portable in jars.
        // A generated asset-index.txt can be added later without changing callers.
        val index = classLoader.getResourceAsStream("$resourceRoot/asset-index.txt")
            ?: return emptyList()
        return index.bufferedReader().useLines { lines ->
            lines.mapNotNull(TerrainAssetNaming::parse).toList()
        }
    }
}
