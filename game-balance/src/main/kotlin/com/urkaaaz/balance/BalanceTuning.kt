package com.urkaaaz.balance

import com.urkaaaz.domain.AmmunitionTuning
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.GameBalance
import com.urkaaaz.domain.SupplyBalance
import com.urkaaaz.domain.UnitConfigurations
import com.urkaaaz.domain.UnitTuning
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** Global tuning knobs searched by [BalanceTuner]; 1.0 / default supply reproduce the shipped game. */
data class TuningCandidate(
    val unitHealth: Double = 1.0,
    val unitDamage: Double = 1.0,
    val unitSpeed: Double = 1.0,
    val startingSupply: Int = 20,
    val ammunitionReload: Double = 1.0,
    val ammunitionDamage: Double = 1.0,
) {
    fun toBalance(): GameBalance = GameBalance.tuned(
        unitTunings = UnitConfigurations.all.associate {
            it.id to UnitTuning(
                healthMultiplier = unitHealth.toFloat(),
                damageMultiplier = unitDamage.toFloat(),
                speedMultiplier = unitSpeed.toFloat(),
            )
        },
        ammunitionTunings = AmmunitionType.entries
            .filter { !it.unlimited }
            .associateWith {
                AmmunitionTuning(
                    reloadMultiplier = ammunitionReload.toFloat(),
                    damageMultiplier = ammunitionDamage.toFloat(),
                )
            },
        supply = SupplyBalance(startingSupply = startingSupply),
    )

    fun describe() = "hp×%.2f dmg×%.2f speed×%.2f supply=%d reload×%.2f ammoDmg×%.2f".format(
        unitHealth, unitDamage, unitSpeed, startingSupply, ammunitionReload, ammunitionDamage,
    )
}

data class TuningResult(
    val candidate: TuningCandidate,
    val aggregates: List<BalanceAggregate>,
) {
    /** Root-mean-square deviation of BLUE win rate from the difficulty curve; lower is better. */
    val score: Double = kotlin.math.sqrt(
        aggregates.map { (it.blueWinRate - DifficultyTargets.targetWinRate(it.level)).let { d -> d * d } }
            .average(),
    )
    val worstDeviation: Double =
        aggregates.maxOf { abs(it.blueWinRate - DifficultyTargets.targetWinRate(it.level)) }
}

/** Random search over [TuningCandidate]s; candidate 0 is always the shipped balance for reference. */
object BalanceTuner {
    val DEFAULT_LEVELS = listOf(1, 10, 20, 31, 50, 100)

    fun candidates(count: Int, random: Random): List<TuningCandidate> =
        listOf(TuningCandidate()) + List(count) {
            TuningCandidate(
                unitHealth = random.nextDouble(0.6, 1.6).round(),
                unitDamage = random.nextDouble(0.6, 1.6).round(),
                unitSpeed = random.nextDouble(0.7, 1.4).round(),
                startingSupply = random.nextInt(8, 21),
                ammunitionReload = random.nextDouble(0.6, 1.6).round(),
                ammunitionDamage = random.nextDouble(0.7, 1.4).round(),
            )
        }

    fun evaluate(candidate: TuningCandidate, levels: List<Int>, seeds: List<Long>): TuningResult {
        val balance = candidate.toBalance()
        return TuningResult(candidate, levels.map { BalanceBaseline.runLevel(it, seeds, balance = balance) })
    }

    fun markdown(results: List<TuningResult>, levels: List<Int>): String = buildString {
        appendLine("# Balance tuning ranking")
        appendLine()
        appendLine("| Rank | Score (RMSE) | Worst | Candidate | " + levels.joinToString(" | ") { "L$it" } + " |")
        appendLine("|---|---|---|---|" + levels.joinToString("") { "---|" })
        results.forEachIndexed { index, result ->
            val rates = result.aggregates.joinToString(" | ") { "%.0f%%".format(it.blueWinRate * 100) }
            appendLine(
                "| ${index + 1} | %.3f | %.2f | %s | %s |".format(
                    result.score, result.worstDeviation, result.candidate.describe(), rates,
                ),
            )
        }
        appendLine()
        appendLine("Targets: " + levels.joinToString(", ") { "L$it %.0f%%".format(DifficultyTargets.targetWinRate(it) * 100) })
    }

    private fun Double.round() = (this * 20).toInt() / 20.0

    /** Arguments: `[candidates] [seedsPerLevel] [outputDir] [levels,comma,separated] [randomSeed]`. */
    @JvmStatic
    fun main(args: Array<String>) {
        val count = args.getOrNull(0)?.toInt() ?: 30
        val seedCount = args.getOrNull(1)?.toInt() ?: 6
        val outputDir = File(args.getOrNull(2) ?: "build/balance-reports")
        val levels = args.getOrNull(3)?.split(',')?.map(String::toInt) ?: DEFAULT_LEVELS
        val random = Random(args.getOrNull(4)?.toLong() ?: 1L)
        val seeds = (1..seedCount).map { it.toLong() }

        val results = candidates(count, random).mapIndexed { index, candidate ->
            evaluate(candidate, levels, seeds).also {
                println("candidate $index: score %.3f  %s".format(it.score, candidate.describe()))
            }
        }.sortedBy { it.score }

        outputDir.mkdirs()
        File(outputDir, "tuning.md").writeText(markdown(results, levels))
        println("best: ${results.first().candidate.describe()} (score %.3f)".format(results.first().score))
        println("reports written to ${outputDir.absolutePath}")
    }
}
