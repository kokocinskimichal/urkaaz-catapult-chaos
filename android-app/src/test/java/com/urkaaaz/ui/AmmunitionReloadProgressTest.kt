package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmmunitionReloadProgressTest {
    @Test
    fun `progress increases as remaining reload time decreases`() {
        assertEquals(0f, ammunitionReloadProgress(8f, 8f), 0f)
        assertEquals(0.5f, ammunitionReloadProgress(4f, 8f), 0f)
        assertEquals(1f, ammunitionReloadProgress(0f, 8f), 0f)
    }

    @Test
    fun `progress stays within bounds when remaining time is outside duration`() {
        assertEquals(0f, ammunitionReloadProgress(10f, 8f), 0f)
        assertEquals(1f, ammunitionReloadProgress(-1f, 8f), 0f)
    }

    @Test
    fun `ammunition without reload is always ready`() {
        assertEquals(1f, ammunitionReloadProgress(0f, 0f), 0f)
    }
}
