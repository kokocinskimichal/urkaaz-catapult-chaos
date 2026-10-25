package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpellIconProgressPresentationTest {
    @Test
    fun shadeTracksElapsedDuration() {
        assertEquals(0f, SpellIconProgressPresentation.coveredFraction(15_000L, 15_000L), 0.0001f)
        assertEquals(0.5f, SpellIconProgressPresentation.coveredFraction(7_500L, 15_000L), 0.0001f)
        assertEquals(1f, SpellIconProgressPresentation.coveredFraction(0L, 15_000L), 0.0001f)
    }

    @Test
    fun shadeClampsInvalidOrOutOfRangeDurations() {
        assertEquals(0f, SpellIconProgressPresentation.coveredFraction(null, 15_000L), 0.0001f)
        assertEquals(0f, SpellIconProgressPresentation.coveredFraction(10_000L, 0L), 0.0001f)
        assertEquals(0f, SpellIconProgressPresentation.coveredFraction(20_000L, 15_000L), 0.0001f)
        assertEquals(1f, SpellIconProgressPresentation.coveredFraction(-1_000L, 15_000L), 0.0001f)
    }
}
