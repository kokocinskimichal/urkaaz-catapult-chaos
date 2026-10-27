package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectileAnimationTest {
    @Test
    fun impactKeepsFlightSizeUntilRockBreaksApart() {
        val animation = ProjectileAnimation(
            flightFrames = emptyList(),
            impactFrames = emptyList(),
            flightRenderSize = 18f,
            impactRenderSize = 36f,
            impactFullRenderSizeStartFrame = 9,
        )

        assertEquals(18f, animation.impactRenderSizeAt(0))
        assertEquals(18f, animation.impactRenderSizeAt(8))
        assertEquals(36f, animation.impactRenderSizeAt(9))
    }

    @Test
    fun sapperBombUsesRequestedWorldSize() {
        assertEquals(30f, BattlefieldRenderer.SAPPER_BOMB_FLIGHT_RENDER_SIZE_WORLD)
    }
}
