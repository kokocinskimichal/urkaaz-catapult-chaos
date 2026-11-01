package com.urkaaaz.campaign

import com.urkaaaz.domain.GameBalanceFixtures
import kotlin.test.Test
import kotlin.test.assertTrue

class DecorationPlacementAuditTest {
    @Test
    fun demoMapHasNoUnvalidatedGameplayObjectsOrDecorations() {
        val map = DemoLevelDefinitions.default(
            GameBalanceFixtures.balance.runtime.campaign,
        ).mapDefinition

        assertTrue(map.decorations.isEmpty())
        assertTrue(MapDefinitionValidator.validate(map).isValid)
    }
}
