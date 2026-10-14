package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface MapLoadResult {
    data class Success(val definition: MapDefinition) : MapLoadResult

    data class Failure(val mapId: String, val message: String) : MapLoadResult
}

class MapDefinitionRepository(
    private val openResource: (String) -> java.io.InputStream?,
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, MapLoadResult>()

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    }

    fun load(mapId: String): MapLoadResult =
        cache.computeIfAbsent(mapId) { loadUncached(it) }

    private fun loadUncached(mapId: String): MapLoadResult {
        if (!MAP_ID.matches(mapId)) return MapLoadResult.Failure(mapId, "invalid map id")
        return try {
            val input = openResource("maps/$mapId.json")
                ?: return MapLoadResult.Failure(mapId, "maps/$mapId.json is missing")
            val definition = input.bufferedReader().use { json.decodeFromString<MapDefinition>(it.readText()) }
            if (definition.mapId != mapId) {
                MapLoadResult.Failure(mapId, "mapId: expected '$mapId', found '${definition.mapId}'")
            } else {
                val validation = MapDefinitionValidator.validate(definition)
                if (validation.isValid) {
                    MapLoadResult.Success(definition)
                } else {
                    MapLoadResult.Failure(
                        mapId,
                        validation.errors.joinToString("; ") { "${it.path}: ${it.message}" },
                    )
                }
            }
        } catch (error: SerializationException) {
            MapLoadResult.Failure(mapId, error.message ?: "invalid map JSON")
        } catch (error: IllegalArgumentException) {
            MapLoadResult.Failure(mapId, error.message ?: "invalid map JSON")
        } catch (error: IOException) {
            MapLoadResult.Failure(mapId, error.message ?: "unable to read map JSON")
        }
    }

    fun require(mapId: String): MapDefinition = when (val result = load(mapId)) {
        is MapLoadResult.Success -> result.definition
        is MapLoadResult.Failure -> error("Unable to load map '${result.mapId}': ${result.message}")
    }

    companion object {
        private val MAP_ID = Regex("[a-z0-9][a-z0-9-]*")
        val bundled = MapDefinitionRepository { path ->
            MapDefinitionRepository::class.java.classLoader.getResourceAsStream(path)
        }
    }
}
