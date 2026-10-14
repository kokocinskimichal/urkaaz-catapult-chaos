package com.urkaaaz.mapeditor.assets

import com.urkaaaz.assets.AssetCatalog
import com.urkaaaz.assets.AssetKey
import java.io.InputStream

class EditorAssetProvider(private val catalog: AssetCatalog = AssetCatalog()) {
    fun assetsForBiome(biome: String): List<AssetKey> = catalog.assetsForBiome(biome)
    fun open(asset: AssetKey): InputStream? = catalog.open(asset)
    fun availableBiomes(): Set<String> = catalog.availableBiomes()
}
