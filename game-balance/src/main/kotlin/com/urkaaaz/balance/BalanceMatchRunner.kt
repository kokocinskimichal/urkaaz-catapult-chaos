package com.urkaaaz.balance

import com.urkaaaz.application.AiMatchCoordinator
import com.urkaaaz.application.MatchSession
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.TerrainBiome
import com.urkaaaz.domain.GameBalance

/**
 * Headless AI-versus-AI match definition. BLUE plays the reference "player"
 * profile, RED plays the profile selected by the campaign level unless
 * [redProfile] overrides it.
 */
data class BalanceMatchConfig(
    val scenario: MatchScenario,
    val seed: Long,
    val blueProfile: AiProfileSelection = AiProfileSelection(AiProfileId.ELITE),
    val redProfile: AiProfileSelection = scenario.aiProfile,
    val stepMilliseconds: Long = DEFAULT_STEP_MILLISECONDS,
    val maxDurationMilliseconds: Long = DEFAULT_MAX_DURATION_MILLISECONDS,
    val blueFortressMaxHealth: Int = DEFAULT_FORTRESS_HEALTH,
    val redFortressMaxHealth: Int = DEFAULT_FORTRESS_HEALTH,
    val balance: GameBalance = GameBalance.default,
) {
    init {
        require(stepMilliseconds > 0) { "step must be positive" }
        require(maxDurationMilliseconds > 0) { "max duration must be positive" }
        require(scenario.mapDefinition != null) { "balance scenario requires a map definition" }
        require(scenario.players.any { it.team == Team.BLUE } && scenario.players.any { it.team == Team.RED }) {
            "balance scenario requires a BLUE and a RED player"
        }
    }

    /** Same match with the initial wind direction reversed, to cancel the wind's side bias. */
    fun withMirroredWind(): BalanceMatchConfig = copy(
        scenario = scenario.copy(wind = scenario.wind.copy(direction = -scenario.wind.direction)),
    )

    companion object {
        const val DEFAULT_STEP_MILLISECONDS = 33L
        const val DEFAULT_MAX_DURATION_MILLISECONDS = 600_000L
        const val DEFAULT_FORTRESS_HEALTH = 500

        fun forCampaignLevel(
            level: Int,
            seed: Long,
            blueProfile: AiProfileSelection = AiProfileSelection(AiProfileId.ELITE),
            redProfile: AiProfileSelection? = null,
            balance: GameBalance = GameBalance.default,
        ): BalanceMatchConfig {
            val scenario = CampaignLevelDefinition.forLevel(level).toMatchScenario(
                playerId = PlayerId("balance-blue"),
                opponentId = PlayerId("balance-red"),
            )
            return BalanceMatchConfig(
                scenario = scenario,
                seed = seed,
                blueProfile = blueProfile,
                redProfile = redProfile ?: scenario.aiProfile,
                balance = balance,
            )
        }
    }
}

data class BalanceMatchResult(
    val seed: Long,
    val outcome: MatchOutcome?,
    val timedOut: Boolean,
    val simulatedMilliseconds: Long,
    val blueFortressHealth: Int,
    val redFortressHealth: Int,
    val rejectedCommands: Int,
    val finalSnapshot: MatchSnapshot,
)

/** Receives every step so metrics can be collected without changing the runner. */
fun interface BalanceMatchObserver {
    fun onStep(
        snapshot: MatchSnapshot,
        events: List<MatchEvent>,
        commands: List<MatchCommand>,
        elapsedMilliseconds: Long,
    )
}

/** Runs one deterministic match through the application layer without any host UI. */
object BalanceMatchRunner {
    private const val RED_SEED_SALT = -7046029254386353131L

    fun run(
        config: BalanceMatchConfig,
        observer: BalanceMatchObserver? = null,
    ): BalanceMatchResult {
        val scenario = config.scenario
        val mapDefinition = requireNotNull(scenario.mapDefinition)
        val biome = TerrainBiome.fromMapId(mapDefinition.biome)
            ?: error("map references an unsupported biome '${mapDefinition.biome}'")
        val bluePlayer = scenario.players.first { it.team == Team.BLUE }.playerId
        val session = MatchSession(
            terrainBiome = biome.name,
            initialWind = scenario.wind,
            mapDefinition = mapDefinition,
            scenario = scenario,
            blueFortressMaxHealth = config.blueFortressMaxHealth,
            redFortressMaxHealth = config.redFortressMaxHealth,
            balance = config.balance,
            randomSeedProvider = { config.seed },
        )
        val blueAi = AiMatchCoordinator(scenario, team = Team.BLUE, profileOverride = config.blueProfile, balance = config.balance)
        val redAi = AiMatchCoordinator(scenario, team = Team.RED, profileOverride = config.redProfile, balance = config.balance)
        val matchId = MatchId("balance-${config.seed}")
        var commandSequence = 0L
        var rejected = 0

        var snapshot = session.dispatch(
            MatchCommand.Start(CommandId("balance-start"), matchId, bluePlayer, config.seed),
        )
        blueAi.start(config.seed)
        redAi.start(config.seed xor RED_SEED_SALT)

        var elapsed = 0L
        var timedOut = false
        while (snapshot.status == MatchStatus.RUNNING) {
            if (elapsed >= config.maxDurationMilliseconds) {
                timedOut = true
                break
            }
            snapshot = session.dispatch(
                MatchCommand.AdvanceSimulation(
                    CommandId("balance-step-${++commandSequence}"),
                    matchId,
                    bluePlayer,
                    config.stepMilliseconds,
                ),
            )
            elapsed += config.stepMilliseconds
            val events = session.consumeEvents().toMutableList()
            val issued = mutableListOf<MatchCommand>()
            if (snapshot.status == MatchStatus.RUNNING) {
                val commands = blueAi.decide(snapshot, config.stepMilliseconds) +
                    redAi.decide(snapshot, config.stepMilliseconds)
                for (command in commands) {
                    if (snapshot.status != MatchStatus.RUNNING) break
                    try {
                        snapshot = session.dispatch(command)
                        issued += command
                        events += session.consumeEvents()
                    } catch (_: IllegalStateException) {
                        rejected++
                    } catch (_: IllegalArgumentException) {
                        rejected++
                    }
                }
            }
            observer?.onStep(snapshot, events, issued, elapsed)
        }

        return BalanceMatchResult(
            seed = config.seed,
            outcome = snapshot.outcome,
            timedOut = timedOut,
            simulatedMilliseconds = elapsed,
            blueFortressHealth = snapshot.fortresses.firstOrNull { it.team == Team.BLUE }?.health ?: 0,
            redFortressHealth = snapshot.fortresses.firstOrNull { it.team == Team.RED }?.health ?: 0,
            rejectedCommands = rejected,
            finalSnapshot = snapshot,
        )
    }
}
