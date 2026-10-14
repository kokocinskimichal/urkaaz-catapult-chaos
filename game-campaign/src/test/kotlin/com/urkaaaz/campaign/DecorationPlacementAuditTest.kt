package com.urkaaaz.campaign

import kotlin.test.Test
import kotlin.test.assertTrue

class DecorationPlacementAuditTest {
    @Test
    fun demoMapHasNoUnvalidatedGameplayObjectsOrDecorations() {
        val map = DemoLevelDefinitions.default.mapDefinition

        assertTrue(map.decorations.isEmpty())
        assertTrue(MapDefinitionValidator.validate(map).isValid)
    }
}
