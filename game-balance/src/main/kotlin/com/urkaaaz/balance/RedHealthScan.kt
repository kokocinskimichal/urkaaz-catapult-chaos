package com.urkaaaz.balance

/** Scans RED fortress HP per level to find the value whose BLUE win rate is closest to the target curve. */
object RedHealthScan {
    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.getOrNull(0)?.toInt() ?: 20
        val levels = args.getOrNull(1)?.split(',')?.map(String::toInt) ?: listOf(1, 10, 20, 30, 31, 50, 75, 100)
        val healths = listOf(250, 300, 350, 400, 450, 500, 600, 700, 800, 1000, 1300)
        val seeds = (1..seedCount).map { it.toLong() }
        levels.forEach { level ->
            val target = DifficultyTargets.targetWinRate(level)
            val rates = healths.map { hp ->
                val rate = seeds.flatMap { seed ->
                    val config = BalanceMatchConfig.forCampaignLevel(level, seed).copy(redFortressMaxHealth = hp)
                    listOf(config, config.withMirroredWind()).map {
                        val collector = MatchMetricsCollector()
                        collector.finish(level, BalanceMatchRunner.run(it, collector))
                    }
                }.let { m -> BalanceAggregator.aggregate(level, m).blueWinRate }
                hp to rate
            }
            val best = rates.minBy { kotlin.math.abs(it.second - target) }
            println("level $level target %.0f%% best redHp=%d (%.0f%%) | %s".format(
                target * 100, best.first, best.second * 100,
                rates.joinToString { "${it.first}:%.0f".format(it.second * 100) },
            ))
        }
    }
}
