package com.urkaaaz.mapeditor.format

import com.urkaaaz.mapeditor.domain.EditorDocumentFactory
import com.urkaaaz.mapeditor.domain.EditorPhase
import kotlin.test.*

class MapDocumentCodecTest {
    @Test
    fun roundTripIsDeterministic() {
        val document = EditorDocumentFactory.newMap("sample", "green_frontier")
        val first = MapDocumentCodec.encode(document)
        val second = MapDocumentCodec.encode(MapDocumentCodec.decode(first))
        assertEquals(first, second)
    }

    @Test
    fun futureSchemaIsRejected() {
        val json = MapDocumentCodec.encode(EditorDocumentFactory.newMap("sample", "green_frontier"))
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertFailsWith<IllegalArgumentException> { MapDocumentCodec.decode(json) }
    }
}
