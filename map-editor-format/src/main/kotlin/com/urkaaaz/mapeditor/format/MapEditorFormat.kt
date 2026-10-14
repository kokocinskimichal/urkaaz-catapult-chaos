package com.urkaaaz.mapeditor.format

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.mapeditor.domain.EditorDocument
import com.urkaaaz.mapeditor.domain.EditorPhase
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.moveTo

const val CURRENT_SCHEMA_VERSION = 1

object MapDocumentCodec {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun encode(document: EditorDocument): String =
        json.encodeToString(document.map)

    fun decode(text: String): EditorDocument {
        val map = try {
            json.decodeFromString<MapDefinition>(text)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Invalid map JSON: ${error.message}", error)
        }
        require(map.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "unsupported schemaVersion ${map.schemaVersion}"
        }
        return EditorDocument(map.schemaVersion, map, EditorPhase.TERRAIN_EDITING)
    }

    fun read(path: Path): EditorDocument = decode(Files.readString(path))

    fun writeDraft(path: Path, document: EditorDocument) {
        atomicWrite(path, encode(document))
    }

    fun writeFinal(path: Path, document: EditorDocument) {
        require(document.phase == EditorPhase.READY_TO_EXPORT) { "document must be ready to export" }
        atomicWrite(path, encode(document))
    }

    private fun atomicWrite(path: Path, content: String) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = path.resolveSibling("${path.fileName}.writing")
        Files.writeString(temporary, content)
        temporary.moveTo(path, overwrite = true)
    }
}
