package com.urkaaaz.application

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiMatchCoordinatorTest {
    private val redPlayer = PlayerId("red-ai")
    private val scenario = MatchScenario(
        scenarioId = "ai-coordinator",
        battlefield = BattlefieldConfig(width = 1_000, height = 600),
        players = listOf(
            PlayerConfig(PlayerId("blue-player"), Team.BLUE),
            PlayerConfig(redPlayer, Team.RED),
        ),
        availableUnits = listOf("DEFENDER"),
        availableAmmunition = listOf("ROCK"),
        aiProfile = AiProfileSelection(AiProfileId.CADET),
    )

    private val snapshot = MatchSnapshot(
        status = MatchStatus.RUNNING,
        matchId = MatchId("ai-coordinator-match"),
        phase = MatchPhase.PLAYER_TURN,
        catapults = listOf(
            CatapultSnapshot(EntityId("blue-catapult"), Team.BLUE, 100f, 400f),
            CatapultSnapshot(EntityId("red-catapult"), Team.RED, 900f, 400f),
        ),
        resourcesByTeam = mapOf(
            Team.RED to ResourceSnapshot(
                supply = 20,
                ammunition = mapOf("ROCK" to 1, "SIEGE_BOMB" to 5),
            ),
            Team.BLUE to ResourceSnapshot(supply = 20),
        ),
    )

    @Test
    fun firstDecisionIncludesCombatAndRecruitmentAndUsesTheProfileCadence() {
        val coordinator = AiMatchCoordinator(scenario)
        coordinator.start(randomSeed = 12L)

        val first = coordinator.decide(snapshot, elapsedMilliseconds = 33)
        val beforeCadence = coordinator.decide(snapshot, elapsedMilliseconds = 599)
        val atCadence = coordinator.decide(snapshot, elapsedMilliseconds = 1)

        assertEquals(redPlayer, first.first().playerId)
        assertEquals(4, first.size)
        assertTrue(beforeCadence.isEmpty())
        assertEquals(3, atCadence.size)
        assertTrue(atCadence.none { it is MatchCommand.DeployUnit })
    }

    @Test
    fun cadetRecruitmentWaitsEightSecondsWhileCombatDecisionsContinue() {
        val coordinator = AiMatchCoordinator(scenario)
        coordinator.start(randomSeed = 15L)
        var elapsed = 0L
        val recruitmentTimes = mutableListOf<Long>()
        var combatDecisionDuringCooldown = false

        repeat(300) {
            elapsed += 33L
            val commands = coordinator.decide(snapshot, elapsedMilliseconds = 33L)
            if (commands.any { it is MatchCommand.DeployUnit }) {
                recruitmentTimes += elapsed
            } else if (
                recruitmentTimes.isNotEmpty() &&
                commands.any { it is MatchCommand.Aim }
            ) {
                combatDecisionDuringCooldown = true
            }
        }

        assertEquals(2, recruitmentTimes.size)
        assertTrue(recruitmentTimes[1] - recruitmentTimes[0] >= 8_000L)
        assertTrue(combatDecisionDuringCooldown)
    }

    @Test
    fun unitAutomationCanBeDisabledWithoutDisablingCatapultDecisions() {
        val coordinator = AiMatchCoordinator(scenario)
        coordinator.setUnitAutomationEnabled(false)
        coordinator.start(randomSeed = 13L)

        val commands = coordinator.decide(snapshot, elapsedMilliseconds = 33)

        assertEquals(3, commands.size)
        assertTrue(commands.none { it is com.urkaaaz.contracts.MatchCommand.DeployUnit })
    }

    @Test
    fun switchingAmmunitionStartsItsReloadBeforeTheAiFires() {
        val coordinator = AiMatchCoordinator(
            scenario.copy(availableAmmunition = listOf("SIEGE_BOMB")),
        )
        coordinator.setUnitAutomationEnabled(false)
        coordinator.start(randomSeed = 16L)

        val previousAmmunitionReloading = snapshot.copy(
            reloadRemainingSecondsByAmmunition = mapOf(
                Team.RED to mapOf("ROCK" to 2f),
            ),
        )
        val selection = coordinator.decide(
            previousAmmunitionReloading,
            elapsedMilliseconds = 33,
        )
        assertEquals(1, selection.size)
        assertTrue(selection.single() is MatchCommand.SelectAmmo)

        val reloadingSnapshot = snapshot.copy(
            reloadRemainingSecondsByAmmunition = mapOf(
                Team.RED to mapOf("SIEGE_BOMB" to 5.5f),
            ),
        )
        assertTrue(
            coordinator.decide(
                reloadingSnapshot,
                elapsedMilliseconds = 1_100,
            ).isEmpty(),
        )

        val loadedSnapshot = reloadingSnapshot.copy(
            reloadRemainingSecondsByAmmunition = mapOf(
                Team.RED to mapOf("SIEGE_BOMB" to 0f),
            ),
        )
        assertTrue(
            coordinator.decide(
                loadedSnapshot,
                elapsedMilliseconds = 1_100,
            ).any { it is MatchCommand.Fire },
        )
    }

    @Test
    fun blueCoordinatorControlsTheBlueTeamWithItsOwnProfile() {
        val bluePlayer = PlayerId("blue-player")
        val coordinator = AiMatchCoordinator(
            scenario,
            team = Team.BLUE,
            profileOverride = AiProfileSelection(AiProfileId.ELITE),
        )
        coordinator.start(randomSeed = 21L)

        val commands = coordinator.decide(snapshot, elapsedMilliseconds = 33)

        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it.playerId == bluePlayer })
        val aim = commands.filterIsInstance<MatchCommand.Aim>().single()
        assertEquals(EntityId("blue-catapult"), aim.catapultId)
    }

    @Test
    fun blueCoordinatorWaitsForTheBlueReload() {
        val coordinator = AiMatchCoordinator(scenario, team = Team.BLUE)
        coordinator.setUnitAutomationEnabled(false)
        coordinator.start(randomSeed = 22L)
        val reloading = snapshot.copy(
            reloadRemainingSeconds = mapOf(Team.BLUE to 3f),
        )

        val commands = coordinator.decide(reloading, elapsedMilliseconds = 33)

        assertTrue(commands.none { it is MatchCommand.Fire || it is MatchCommand.Aim })
    }

    @Test
    fun pausedAndStoppedMatchesDoNotProduceAiCommands() {
        val coordinator = AiMatchCoordinator(scenario)
        coordinator.start(randomSeed = 14L)
        coordinator.pause()

        assertTrue(coordinator.decide(snapshot, elapsedMilliseconds = 5_000).isEmpty())

        coordinator.resume()
        assertTrue(coordinator.decide(snapshot, elapsedMilliseconds = 33).isNotEmpty())
        coordinator.stop()
        assertTrue(coordinator.decide(snapshot, elapsedMilliseconds = 1_000).isEmpty())
    }

    @Test
    fun campaignRestrictionsAndDebugOverridesAreExposedByTheCoordinator() {
        val coordinator = AiMatchCoordinator(scenario)
        assertEquals(setOf("DEFENDER"), coordinator.allowedUnitIds())
        assertEquals(setOf("ROCK"), coordinator.allowedAmmunitionIds())

        coordinator.setAllowedUnitIds(setOf("SAPPER"))

        assertEquals(setOf("SAPPER"), coordinator.allowedUnitIds())
    }
}
