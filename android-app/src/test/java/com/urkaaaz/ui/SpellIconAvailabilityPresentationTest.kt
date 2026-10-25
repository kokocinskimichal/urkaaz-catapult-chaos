package com.urkaaaz.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellIconAvailabilityPresentationTest {
    @Test
    fun activeRunicBastionDoesNotHighlightItsSpellIcon() {
        assertFalse(
            SpellIconAvailabilityPresentation.isHighlighted(
                spellType = "RUNIC_BASTION",
                active = true,
                ready = false,
            ),
        )
        assertTrue(
            SpellIconAvailabilityPresentation.isHighlighted(
                spellType = "RUNIC_BASTION",
                active = false,
                ready = true,
            ),
        )
        assertFalse(
            SpellIconAvailabilityPresentation.isHighlighted(
                spellType = "RUNIC_BASTION",
                active = false,
                ready = false,
            ),
        )
        assertTrue(
            SpellIconAvailabilityPresentation.isHighlighted(
                spellType = "FORTRESS_SHIELD",
                active = true,
                ready = false,
            ),
        )
    }
}
