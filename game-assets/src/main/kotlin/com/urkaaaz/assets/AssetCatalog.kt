package com.urkaaaz.assets

import com.urkaaaz.contracts.TerrainBiome
import java.io.InputStream

enum class AssetCategory { TREE, ROCK, PROP, RELIC, CAP, TILE, SOIL, BACKGROUND, OTHER }

data class AssetKey(
    val biome: String,
    val category: AssetCategory,
    val variant: String,
    val fileName: String,
) {
    val stableKey: String
        get() = if (biome == SHARED_BIOME && category == AssetCategory.RELIC &&
            variant.startsWith(MEDIUM_RELIC_VARIANT_PREFIX)
        ) {
            "relic.$SHARED_BIOME.medium.${variant.removePrefix(MEDIUM_RELIC_VARIANT_PREFIX)}"
        } else {
            "terrain_${biome}_${category.name.lowercase()}_$variant"
        }

    private companion object {
        const val SHARED_BIOME = "shared"
        const val MEDIUM_RELIC_VARIANT_PREFIX = "medium_"
    }
}

object TerrainAssetNaming {
    private val pattern = Regex("""terrain_([^_]+(?:_[^_]+)*)_(tree|rock|prop|relic|cap|tile|soil)_([^.]+)\.png""")
    private val sharedMediumRelicPattern = Regex("""relic_medium_([a-z0-9_]+)\.png""")

    fun parse(fileName: String): AssetKey? {
        val match = pattern.matchEntire(fileName)
        if (match != null) {
            val biome = match.groupValues[1]
            val category = AssetCategory.valueOf(match.groupValues[2].uppercase())
            return AssetKey(biome, category, match.groupValues[3], fileName)
        }
        val sharedMediumRelic = sharedMediumRelicPattern.matchEntire(fileName) ?: return null
        return AssetKey(
            biome = "shared",
            category = AssetCategory.RELIC,
            variant = "medium_${sharedMediumRelic.groupValues[1]}",
            fileName = fileName,
        )
    }
}

class AssetCatalog(
    private val resourceRoot: String = "terrain",
    private val classLoader: ClassLoader = AssetCatalog::class.java.classLoader,
) {
    fun assetsForBiome(biome: String): List<AssetKey> =
        index().filter {
            it.biome == assetDirectoryBiome(biome) ||
                (it.biome == SHARED_BIOME && it.category == AssetCategory.RELIC)
        }.sortedWith(compareBy({ it.category }, { it.variant }))

    fun find(fileName: String): AssetKey? = TerrainAssetNaming.parse(fileName)

    fun open(asset: AssetKey): InputStream? =
        classLoader.getResourceAsStream("$resourceRoot/${asset.biome}/${asset.fileName}")
            ?: classLoader.getResourceAsStream("$resourceRoot/${asset.fileName}")

    fun availableBiomes(): Set<String> = TerrainBiome.entries
        .mapNotNull { biome -> biome.mapId.takeIf { assetsForBiome(it).isNotEmpty() } }
        .toSortedSet()

    fun allAssets(): List<AssetKey> = index().sortedWith(compareBy({ it.biome }, { it.category }, { it.variant }))

    fun isKnownMapDecoration(assetKey: String, biome: String): Boolean {
        if (assetsForBiome(biome).any { it.stableKey == assetKey }) return true
        val match = RELIC_KEY.matchEntire(assetKey) ?: return false
        return match.groupValues[1] == biome &&
            match.groupValues[2].toIntOrNull()?.let { it in 1..10 } == true &&
            TerrainBiome.fromMapId(biome) != null
    }

    private fun index(): List<AssetKey> {
        // Resource enumeration is intentionally avoided: it is not portable in jars.
        // A generated asset-index.txt can be added later without changing callers.
        val index = classLoader.getResourceAsStream("$resourceRoot/asset-index.txt")
            ?: return emptyList()
        return index.bufferedReader().useLines { lines ->
            lines.mapNotNull(TerrainAssetNaming::parse).toList()
        }
    }

    private fun assetDirectoryBiome(biome: String): String = when (biome) {
        TerrainBiome.FROSTBOUND.mapId -> "frost"
        TerrainBiome.SUNKEN_MARSHES.mapId -> TerrainBiome.GREEN_FRONTIER.mapId
        else -> biome
    }

    private companion object {
        val RELIC_KEY = Regex("""relic\.([a-z_]+)\.small\.(\d+)""")
        const val SHARED_BIOME = "shared"
    }
}
