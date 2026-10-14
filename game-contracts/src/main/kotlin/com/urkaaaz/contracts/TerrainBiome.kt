package com.urkaaaz.contracts

enum class TerrainBiome(val mapId: String) {
    GREEN_FRONTIER("green_frontier"),
    FROSTBOUND("frostbound"),
    COPPERWOOD("copperwood"),
    ASHEN_MARCH("ashen_march"),
    SUNKEN_MARSHES("sunken_marshes"),
    ;

    companion object {
        fun fromMapId(mapId: String): TerrainBiome? =
            entries.firstOrNull { it.mapId == mapId }
    }
}
