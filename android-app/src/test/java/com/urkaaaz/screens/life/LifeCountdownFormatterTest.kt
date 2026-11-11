package com.urkaaaz.screens.life

import org.junit.Assert.assertEquals
import org.junit.Test

class LifeCountdownFormatterTest {
    @Test
    fun roundsRemainingTimeUpAndFormatsLongDurations() {
        assertEquals("0:00", LifeCountdownFormatter.format(0L))
        assertEquals("0:01", LifeCountdownFormatter.format(1L))
        assertEquals("14:59", LifeCountdownFormatter.format(898_001L))
        assertEquals("1:00:00", LifeCountdownFormatter.format(3_600_000L))
        assertEquals("0:00", LifeCountdownFormatter.format(-1L))
    }
}
