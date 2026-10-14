package com.urkaaaz.campaign

import com.urkaaaz.campaign.chapter1.chapter1Level2MapDefinition
import kotlin.test.Test
import kotlin.test.assertTrue

class DecorationPlacementAuditTest {
    @Test
    fun decorationsMayRemainOnSlopes() {
        val errors = MapDefinitionValidator.validate(chapter1Level2MapDefinition).errors
        assertTrue(errors.none { it is MapValidationError.DecorationOnSteepSlope })
    }
}
