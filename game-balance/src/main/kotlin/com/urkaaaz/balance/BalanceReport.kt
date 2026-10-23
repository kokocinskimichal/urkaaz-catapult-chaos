package com.urkaaaz.balance

import com.urkaaaz.domain.AmmunitionCatalog
import com.urkaaaz.domain.AmmunitionType
import java.util.Locale

/** Plain-text renderers so reports need no extra dependencies. */
object BalanceReport {
    const val ELITE_LIMITATIONS =
        "ELITE heuristics: wind-aware aiming but terrain-blind, ammunition chosen by unit damage " +
            "only (ignores reload and fortress damage), no spells."

    fun csv(aggregates: List<BalanceAggregate>): String = buildString {
        appendLine(
            "level,matches,blue_win,red_win,draw,timeout,avg_duration_s,avg_blue_margin," +
                "blue_shots,red_shots,blue_deployments,red_deployments,rejected",
        )
        aggregates.forEach { a ->
            appendLine(
                listOf(
                    a.level.toString(), a.matches.toString(), f(a.blueWinRate), f(a.redWinRate), f(a.drawRate),
                    f(a.timeoutRate), f(a.averageDurationSeconds), f(a.averageBlueMargin),
                    f(a.averageBlueShots), f(a.averageRedShots), f(a.averageBlueDeployments),
                    f(a.averageRedDeployments), f(a.averageRejectedCommands),
                ).joinToString(","),
            )
        }
    }

    fun json(aggregates: List<BalanceAggregate>): String = buildString {
        appendLine("{")
        appendLine("  \"limitations\": \"$ELITE_LIMITATIONS\",")
        appendLine("  \"reloadSecondsByAmmunition\": {${reloadEntries().joinToString(", ") { "\"${it.first}\": ${f(it.second)}" }}},")
        appendLine("  \"levels\": [")
        aggregates.forEachIndexed { index, a ->
            val share = a.fortressDamageShareByAmmunition.entries
                .joinToString(", ") { "\"${it.key}\": ${f(it.value)}" }
            append(
                "    {\"level\": ${a.level}, \"matches\": ${a.matches}, \"blueWinRate\": ${f(a.blueWinRate)}, " +
                    "\"targetWinRate\": ${f(DifficultyTargets.targetWinRate(a.level))}, \"verdict\": \"${DifficultyTargets.verdict(a).name}\", " +
                    "\"redWinRate\": ${f(a.redWinRate)}, \"drawRate\": ${f(a.drawRate)}, " +
                    "\"timeoutRate\": ${f(a.timeoutRate)}, \"avgDurationSeconds\": ${f(a.averageDurationSeconds)}, " +
                    "\"avgBlueMargin\": ${f(a.averageBlueMargin)}, \"avgBlueShots\": ${f(a.averageBlueShots)}, " +
                    "\"avgRedShots\": ${f(a.averageRedShots)}, \"fortressDamageShare\": {$share}}",
            )
            appendLine(if (index < aggregates.lastIndex) "," else "")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun markdown(aggregates: List<BalanceAggregate>): String = buildString {
        appendLine("# Balance Simulation report")
        appendLine()
        appendLine("BLUE = ELITE reference player, RED = campaign-level profile.")
        appendLine("Target BLUE win rate: ${pct(DifficultyTargets.START_WIN_RATE)} (level 1), ${pct(DifficultyTargets.ELITE_WIN_RATE)} (level ${DifficultyTargets.FIRST_ELITE_LEVEL}), " +
            "${pct(DifficultyTargets.END_WIN_RATE)} (level 100), tolerance ±${pct(DifficultyTargets.TOLERANCE)}.")
        appendLine()
        appendLine("> $ELITE_LIMITATIONS")
        appendLine()
        appendLine("Reload (s): " + reloadEntries().joinToString(", ") { "${it.first} ${f(it.second)}" })
        appendLine()
        appendLine("| Level | Matches | BLUE win | Target | Verdict | RED win | Draw | Timeout | Avg time s | BLUE margin | Shots B/R |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        aggregates.forEach { a ->
            appendLine(
                "| ${a.level} | ${a.matches} | ${pct(a.blueWinRate)} | ${pct(DifficultyTargets.targetWinRate(a.level))} | " +
                    "${DifficultyTargets.verdict(a).name} | ${pct(a.redWinRate)} | ${pct(a.drawRate)} | " +
                    "${pct(a.timeoutRate)} | ${f(a.averageDurationSeconds)} | ${f(a.averageBlueMargin)} | " +
                    "${f(a.averageBlueShots)}/${f(a.averageRedShots)} |",
            )
        }
    }

    private fun reloadEntries(): List<Pair<String, Double>> = AmmunitionType.values().map {
        it.name to AmmunitionCatalog.definition(it).reloadSeconds.toDouble()
    }

    private fun f(value: Double) = String.format(Locale.ROOT, "%.3f", value)

    private fun pct(value: Double) = String.format(Locale.ROOT, "%.0f%%", value * 100)
}
