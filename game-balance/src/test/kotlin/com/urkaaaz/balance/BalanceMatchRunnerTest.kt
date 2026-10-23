package com.urkaaaz.balance

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.MatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BalanceMatchRunnerTest {
    private val config = BalanceMatchConfig.forCampaignLevel(
        level = 1,
        seed = 7L,
        redProfile = AiProfileSelection(AiProfileId.CADET),
    )

    @Test
    fun aiVersusAiMatchFinishesWithAnOutcomeWithinTheTimeLimit() {
        val result = BalanceMatchRunner.run(config)

        assertTrue(!result.timedOut)
        assertEquals(MatchStatus.FINISHED, result.finalSnapshot.status)
        assertNotNull(result.outcome)
        assertTrue(result.simulatedMilliseconds > 0)
    }

    @Test
    fun sameSeedProducesTheSameResult() {
        val first = BalanceMatchRunner.run(config)
        val second = BalanceMatchRunner.run(config)

        assertEquals(first.outcome, second.outcome)
        assertEquals(first.simulatedMilliseconds, second.simulatedMilliseconds)
        assertEquals(first.blueFortressHealth, second.blueFortressHealth)
        assertEquals(first.redFortressHealth, second.redFortressHealth)
    }

    @Test
    fun observerReceivesEveryStepAndTheIssuedCommands() {
        var steps = 0
        var commands = 0

        val result = BalanceMatchRunner.run(config) { _, _, issued, _ ->
            steps++
            commands += issued.size
        }

        assertEquals(result.simulatedMilliseconds / config.stepMilliseconds, steps.toLong())
        assertTrue(commands > 0)
    }

    @Test
    fun timeLimitStopsAMatchThatDoesNotFinish() {
        val result = BalanceMatchRunner.run(config.copy(maxDurationMilliseconds = 1_000L))

        assertTrue(result.timedOut)
        assertEquals(MatchStatus.RUNNING, result.finalSnapshot.status)
    }
}
