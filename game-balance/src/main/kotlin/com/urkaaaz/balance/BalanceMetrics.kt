package com.urkaaaz.balance

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.Team

/** Per-team counters for one match. */
data class TeamMatchMetrics(
    val shotsByAmmunition: Map<String, Int> = emptyMap(),
    val fortressDamageByAmmunition: Map<String, Int> = emptyMap(),
    val unitDamageByAmmunition: Map<String, Int> = emptyMap(),
    val unitMeleeDamage: Int = 0,
    val deploymentsByUnit: Map<String, Int> = emptyMap(),
    val unitsLost: Int = 0,
    val spellsCast: Int = 0,
) {
    val shots: Int get() = shotsByAmmunition.values.sum()
    val fortressDamageDealt: Int get() = fortressDamageByAmmunition.values.sum()
    val deployments: Int get() = deploymentsByUnit.values.sum()
}

data class MatchMetrics(
    val level: Int,
    val seed: Long,
    val outcome: MatchOutcome?,
    val timedOut: Boolean,
    val durationSeconds: Double,
    val blueFortressHealthFraction: Double,
    val redFortressHealthFraction: Double,
    val rejectedCommands: Int,
    val blue: TeamMatchMetrics,
    val red: TeamMatchMetrics,
) {
    /** Remaining health margin of the BLUE fortress: positive when BLUE wins comfortably. */
    val blueMargin: Double get() = blueFortressHealthFraction - redFortressHealthFraction
}

/** Collects metrics from the step stream of [BalanceMatchRunner]. */
class MatchMetricsCollector : BalanceMatchObserver {
    private class Counters {
        val shots = mutableMapOf<String, Int>()
        val fortressDamage = mutableMapOf<String, Int>()
        val unitDamage = mutableMapOf<String, Int>()
        val deployments = mutableMapOf<String, Int>()
        var melee = 0
        var unitsLost = 0
        var spells = 0

        fun toMetrics() = TeamMatchMetrics(
            shotsByAmmunition = shots.toMap(),
            fortressDamageByAmmunition = fortressDamage.toMap(),
            unitDamageByAmmunition = unitDamage.toMap(),
            unitMeleeDamage = melee,
            deploymentsByUnit = deployments.toMap(),
            unitsLost = unitsLost,
            spellsCast = spells,
        )
    }

    private val counters = mapOf(Team.BLUE to Counters(), Team.RED to Counters())
    private val teamByEntity = mutableMapOf<String, Team>()
    private val ammunitionByProjectile = mutableMapOf<String, String>()

    override fun onStep(
        snapshot: MatchSnapshot,
        events: List<MatchEvent>,
        commands: List<MatchCommand>,
        elapsedMilliseconds: Long,
    ) {
        snapshot.units.forEach { teamByEntity[it.entityId.value] = it.team }
        snapshot.fortresses.forEach { teamByEntity[it.entityId.value] = it.team }
        snapshot.catapults.forEach { teamByEntity[it.entityId.value] = it.team }
        events.forEach(::record)
    }

    private fun record(event: MatchEvent) {
        when (event) {
            is MatchEvent.ProjectileFired -> teamByEntity[event.catapultId.value]?.let {
                ammunitionByProjectile[event.projectileId.value] = event.ammunitionType
                counters.getValue(it).shots.merge(event.ammunitionType, 1, Int::plus)
            }
            is MatchEvent.UnitDeployed -> teamByEntity[event.unitId.value]?.let {
                counters.getValue(it).deployments.merge(event.unitType, 1, Int::plus)
            }
            is MatchEvent.UnitDefeated -> teamByEntity[event.unitId.value]?.let {
                counters.getValue(it).unitsLost++
            }
            is MatchEvent.DamageApplied -> {
                val victim = teamByEntity[event.targetId.value] ?: return
                val attacker = victim.opponent()
                val target = counters.getValue(attacker)
                // The engine reports a generic type on damage events; resolve it from the firing event.
                val ammunition = event.sourceProjectileId?.let { ammunitionByProjectile[it.value] } ?: "UNKNOWN"
                if (event.targetLayer == FORTRESS_LAYER) {
                    target.fortressDamage.merge(ammunition, event.amount, Int::plus)
                } else {
                    target.unitDamage.merge(ammunition, event.amount, Int::plus)
                }
            }
            is MatchEvent.AttackHit -> teamByEntity[event.attackerId.value]?.let {
                counters.getValue(it).melee += event.finalDamage
            }
            is MatchEvent.SpellCast -> Unit
            else -> Unit
        }
    }

    fun finish(level: Int, result: BalanceMatchResult): MatchMetrics {
        val fortresses = result.finalSnapshot.fortresses
        fun fraction(team: Team) = fortresses.firstOrNull { it.team == team }
            ?.let { it.health.toDouble() / it.maxHealth.coerceAtLeast(1) } ?: 0.0
        return MatchMetrics(
            level = level,
            seed = result.seed,
            outcome = result.outcome,
            timedOut = result.timedOut,
            durationSeconds = result.simulatedMilliseconds / 1000.0,
            blueFortressHealthFraction = fraction(Team.BLUE),
            redFortressHealthFraction = fraction(Team.RED),
            rejectedCommands = result.rejectedCommands,
            blue = counters.getValue(Team.BLUE).toMetrics(),
            red = counters.getValue(Team.RED).toMetrics(),
        )
    }

    private fun Team.opponent() = if (this == Team.BLUE) Team.RED else Team.BLUE

    private companion object {
        const val FORTRESS_LAYER = "FORTRESS"
    }
}

/** Aggregated statistics of many matches sharing one configuration. */
data class BalanceAggregate(
    val level: Int,
    val matches: Int,
    val blueWinRate: Double,
    val redWinRate: Double,
    val drawRate: Double,
    val timeoutRate: Double,
    val averageDurationSeconds: Double,
    val averageBlueMargin: Double,
    val averageBlueShots: Double,
    val averageRedShots: Double,
    val averageBlueDeployments: Double,
    val averageRedDeployments: Double,
    val fortressDamageShareByAmmunition: Map<String, Double>,
    val averageRejectedCommands: Double,
)

object BalanceAggregator {
    fun aggregate(level: Int, results: List<MatchMetrics>): BalanceAggregate {
        require(results.isNotEmpty()) { "at least one match is required" }
        val n = results.size.toDouble()
        fun rate(predicate: (MatchMetrics) -> Boolean) = results.count(predicate) / n
        val damage = mutableMapOf<String, Int>()
        results.forEach { match ->
            (match.blue.fortressDamageByAmmunition.asSequence() + match.red.fortressDamageByAmmunition.asSequence())
                .forEach { (ammo, amount) -> damage.merge(ammo, amount, Int::plus) }
        }
        val totalDamage = damage.values.sum().coerceAtLeast(1)
        return BalanceAggregate(
            level = level,
            matches = results.size,
            blueWinRate = rate { it.outcome == MatchOutcome.BLUE_WIN },
            redWinRate = rate { it.outcome == MatchOutcome.RED_WIN },
            drawRate = rate { it.outcome == MatchOutcome.DRAW },
            timeoutRate = rate { it.timedOut },
            averageDurationSeconds = results.sumOf { it.durationSeconds } / n,
            averageBlueMargin = results.sumOf { it.blueMargin } / n,
            averageBlueShots = results.sumOf { it.blue.shots } / n,
            averageRedShots = results.sumOf { it.red.shots } / n,
            averageBlueDeployments = results.sumOf { it.blue.deployments } / n,
            averageRedDeployments = results.sumOf { it.red.deployments } / n,
            fortressDamageShareByAmmunition = damage.toSortedMap().mapValues { it.value.toDouble() / totalDamage },
            averageRejectedCommands = results.sumOf { it.rejectedCommands } / n,
        )
    }
}
