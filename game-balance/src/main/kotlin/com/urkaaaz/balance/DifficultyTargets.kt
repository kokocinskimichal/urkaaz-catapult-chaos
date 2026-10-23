package com.urkaaaz.balance

import com.urkaaaz.campaign.CampaignLevelDefinition
import kotlin.math.abs

/**
 * Target BLUE (ELITE reference player) win rate per campaign level, piecewise linear:
 * [START_WIN_RATE] on level 1, [ELITE_WIN_RATE] on the first ELITE level (an equal-skill
 * opponent), [END_WIN_RATE] on the last level, with a tolerance band.
 */
object DifficultyTargets {
    const val START_WIN_RATE = 0.90
    const val ELITE_WIN_RATE = 0.55
    const val END_WIN_RATE = 0.45
    const val FIRST_ELITE_LEVEL = 31
    const val TOLERANCE = 0.10

    fun targetWinRate(level: Int): Double {
        val last = CampaignLevelDefinition.MAX_LEVELS
        val clamped = level.coerceIn(1, last)
        return if (clamped <= FIRST_ELITE_LEVEL) {
            interpolate(START_WIN_RATE, ELITE_WIN_RATE, (clamped - 1).toDouble() / (FIRST_ELITE_LEVEL - 1))
        } else {
            interpolate(ELITE_WIN_RATE, END_WIN_RATE, (clamped - FIRST_ELITE_LEVEL).toDouble() / (last - FIRST_ELITE_LEVEL))
        }
    }

    private fun interpolate(from: Double, to: Double, fraction: Double) = from + (to - from) * fraction

    fun verdict(aggregate: BalanceAggregate): DifficultyVerdict {
        val deviation = aggregate.blueWinRate - targetWinRate(aggregate.level)
        return when {
            abs(deviation) <= TOLERANCE -> DifficultyVerdict.ON_TARGET
            deviation > 0 -> DifficultyVerdict.TOO_EASY
            else -> DifficultyVerdict.TOO_HARD
        }
    }
}

enum class DifficultyVerdict { ON_TARGET, TOO_EASY, TOO_HARD }
