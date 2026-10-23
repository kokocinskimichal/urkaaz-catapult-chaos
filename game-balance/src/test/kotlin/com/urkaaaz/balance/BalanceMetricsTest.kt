package com.urkaaaz.balance

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceMetricsTest {
    private fun play(seed: Long): MatchMetrics {
        val config = BalanceMatchConfig.forCampaignLevel(
            level = 1,
            seed = seed,
            redProfile = AiProfileSelection(AiProfileId.CADET),
        )
        val collector = MatchMetricsCollector()
        val result = BalanceMatchRunner.run(config, collector)
        return collector.finish(1, result)
    }

    @Test
    fun collectorCountsShotsDeploymentsAndFortressDamage() {
        val metrics = play(3L)

        assertTrue(metrics.blue.shots > 0)
        assertTrue(metrics.blue.deployments > 0)
        assertTrue(metrics.blue.fortressDamageDealt + metrics.red.fortressDamageDealt > 0)
        assertTrue(metrics.durationSeconds > 0.0)
    }

    @Test
    fun aggregateRatesSumToOneAndReportsMentionLimitationsAndReload() {
        val aggregate = BalanceAggregator.aggregate(1, listOf(play(1L), play(2L)))

        assertEquals(2, aggregate.matches)
        assertEquals(
            1.0,
            aggregate.blueWinRate + aggregate.redWinRate + aggregate.drawRate + aggregate.timeoutRate,
            1e-9,
        )
        val markdown = BalanceReport.markdown(listOf(aggregate))
        assertTrue("ELITE heuristics" in markdown)
        assertTrue("SIEGE_BOMB 2.750" in markdown)
        assertTrue(BalanceReport.csv(listOf(aggregate)).lines().size >= 2)
        assertTrue(BalanceReport.json(listOf(aggregate)).contains("\"levels\""))
    }
}
