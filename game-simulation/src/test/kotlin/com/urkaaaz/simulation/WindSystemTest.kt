package com.urkaaaz.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WindSystemTest {
    @Test
    fun windRemainsStableBeforeInitialChangeDelay() {
        val wind = WindSystem(initialAccelerationX = 0.5f)

        wind.update(7.9f)

        assertEquals(0.5f, wind.accelerationX())
    }

    @Test
    fun windChangesDeterministicallyAfterDelayAndTransition() {
        val first = WindSystem(initialAccelerationX = 0.5f)
        val second = WindSystem(initialAccelerationX = 0.5f)

        repeat(110) {
            first.update(0.1f)
            second.update(0.1f)
        }

        assertNotEquals(0.5f, first.accelerationX())
        assertEquals(first.accelerationX(), second.accelerationX())
        assertEquals(first.snapshot(), second.snapshot())
    }
}
