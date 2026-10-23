package com.urkaaaz.balance

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.domain.GameBalance
import java.io.File

/**
 * Baseline Balance Simulation: ELITE (BLUE) against each level's campaign profile,
 * plus an ELITE-vs-ELITE symmetry check. Every seed is played twice, with the initial wind direction
 * mirrored, because wind favors one side early in short matches. Arguments: `[seedsPerLevel] [outputDir] [levels,comma,separated]`.
 */
object BalanceBaseline {
    val DEFAULT_LEVELS = listOf(1, 3, 4, 10, 11, 20, 30, 31, 50, 100)
    val SYMMETRY_LEVELS = listOf(30, 31)

    fun runLevel(
        level: Int,
        seeds: List<Long>,
        redProfile: AiProfileSelection? = null,
        balance: GameBalance = GameBalance.default,
    ): BalanceAggregate {
        val matches = seeds.flatMap { seed ->
            val config = BalanceMatchConfig.forCampaignLevel(level, seed, redProfile = redProfile, balance = balance)
            listOf(config, config.withMirroredWind()).map { variant ->
                val collector = MatchMetricsCollector()
                collector.finish(level, BalanceMatchRunner.run(variant, collector))
            }
        }
        return BalanceAggregator.aggregate(level, matches)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.getOrNull(0)?.toInt() ?: 20
        val outputDir = File(args.getOrNull(1) ?: "build/balance-reports")
        val levels = args.getOrNull(2)?.split(',')?.map(String::toInt) ?: DEFAULT_LEVELS
        val seeds = (1..seedCount).map { it.toLong() }

        val campaign = levels.map { level ->
            runLevel(level, seeds).also { println("level $level: BLUE win ${it.blueWinRate}, avg ${it.averageDurationSeconds}s") }
        }
        val symmetry = SYMMETRY_LEVELS.map { level ->
            runLevel(level, seeds, AiProfileSelection(AiProfileId.ELITE))
                .also { println("symmetry ELITE vs ELITE (level $level): BLUE win ${it.blueWinRate}") }
        }

        outputDir.mkdirs()
        File(outputDir, "baseline.md").writeText(
            BalanceReport.markdown(campaign) +
                "\n## Symmetry check: ELITE vs ELITE (levels ${SYMMETRY_LEVELS.joinToString()}; odd/even level = wind direction)\n\n" +
                "| Level" + BalanceReport.markdown(symmetry).substringAfter("\n| Level"),
        )
        File(outputDir, "baseline.csv").writeText(BalanceReport.csv(campaign + symmetry))
        File(outputDir, "baseline.json").writeText(BalanceReport.json(campaign))
        println("reports written to ${outputDir.absolutePath}")
    }
}
