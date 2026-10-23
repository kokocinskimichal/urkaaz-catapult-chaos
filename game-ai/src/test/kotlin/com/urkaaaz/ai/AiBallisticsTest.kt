package com.urkaaaz.ai

import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.AmmunitionType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiBallisticsTest {
    private fun power(team: Team, targetX: Float, wind: WindSnapshot): Float = assertNotNull(
        AiBallistics.powerFor(team, 450f, 2855f, targetX, 2855f, 43f, AmmunitionType.SIEGE_BOMB, wind),
    )

    @Test
    fun solvedPowerLandsOnTheTarget() {
        val wind = WindSnapshot(direction = 1f, strength = 0.5f)
        val solved = power(Team.BLUE, 3_000f, wind)

        val landing = assertNotNull(
            AiBallistics.landingX(Team.BLUE, 450f, 2855f, 2855f, 43f, solved, AmmunitionType.SIEGE_BOMB, wind),
        )
        assertTrue(abs(landing - 3_000f) < 5f)
    }

    @Test
    fun tailwindNeedsLessPowerThanHeadwind() {
        val tailwind = power(Team.BLUE, 3_000f, WindSnapshot(direction = 1f, strength = 0.6f))
        val headwind = power(Team.BLUE, 3_000f, WindSnapshot(direction = -1f, strength = 0.6f))

        assertTrue(tailwind < headwind)
    }

    @Test
    fun unreachableTargetsReturnNull() {
        val result = AiBallistics.powerFor(
            Team.BLUE, 450f, 2855f, 60_000f, 2855f, 43f, AmmunitionType.SIEGE_BOMB, WindSnapshot(),
        )
        assertTrue(result == null)
    }
}
