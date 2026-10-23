package com.urkaaaz.application

import com.urkaaaz.ai.AiBallistics
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.simulation.SimulationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class AiBallisticsConstantsTest {
    @Test
    fun aiBallisticsMirrorsTheSimulationDefaults() {
        val defaults = SimulationConfig(bounds = WorldBounds(1f, 1f))

        assertEquals(defaults.gravityAccelerationY, AiBallistics.GRAVITY_ACCELERATION)
        assertEquals(defaults.windAccelerationScale, AiBallistics.WIND_ACCELERATION_SCALE)
    }
}
