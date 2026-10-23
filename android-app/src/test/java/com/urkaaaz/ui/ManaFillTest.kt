package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManaFillTest {
    @Test
    fun mapsManaToClampedFillFraction() {
        assertEquals(0f, ManaFill.fraction(0, 100), 0f)
        assertEquals(0.5f, ManaFill.fraction(50, 100), 0f)
        assertEquals(1f, ManaFill.fraction(100, 100), 0f)
        assertEquals(0f, ManaFill.fraction(-10, 100), 0f)
        assertEquals(1f, ManaFill.fraction(120, 100), 0f)
        assertEquals(0f, ManaFill.fraction(10, 0), 0f)
    }
}
