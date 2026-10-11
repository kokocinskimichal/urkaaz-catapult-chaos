package com.urkaaaz.ai

import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.UnitFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiAgentTest {
    private val snapshot = MatchSnapshot(
        status = MatchStatus.RUNNING,
        matchId = MatchId("match-1"),
        phase = MatchPhase.PLAYER_TURN,
        activeTeam = Team.RED,
        catapults = listOf(
            CatapultSnapshot(EntityId("blue-catapult"), Team.BLUE, 100f, 400f),
            CatapultSnapshot(EntityId("red-catapult"), Team.RED, 900f, 400f),
        ),
        fortresses = listOf(
            FortressSnapshot(EntityId("blue-fortress"), Team.BLUE, 500, 500),
            FortressSnapshot(EntityId("red-fortress"), Team.RED, 500, 500),
        ),
        resources = ResourceSnapshot(
            gold = 10,
            supply = 100,
            ammunition = mapOf("ROCK" to 1, "SIEGE_BOMB" to 1),
        ),
    )

    @Test
    fun aiReturnsHumanCompatibleCommandsFromSnapshotOnly() {
        val commands = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                randomSeed = 42,
            ),
        ).decide(snapshot)

        assertEquals(3, commands.size)
        assertTrue(commands[0] is MatchCommand.SelectAmmo)
        assertTrue(commands[1] is MatchCommand.Aim)
        assertTrue(commands[2] is MatchCommand.Fire)
        val aim = commands[1] as MatchCommand.Aim
        assertTrue(aim.directionDegrees in 28f..68f)
        assertTrue(aim.power in 55f..96f)
        assertTrue(commands.all { it.matchId == MatchId("match-1") })
    }

    @Test
    fun lowHealthChangesTemperamentToDesperateAndCanDeployUnit() {
        val lowHealth = snapshot.copy(
            fortresses = snapshot.fortresses.map {
                if (it.team == Team.RED) it.copy(health = 100) else it
            },
        )
        val deployment = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                temperament = AiTemperament.CAUTIOUS,
                allowedUnits = setOf(UnitFactory.sapper()),
            ),
        ).chooseDeployment(lowHealth)

        assertEquals("SAPPER", deployment?.unitType)
    }

    @Test
    fun aiDoesNotDeploySecondActiveSapper() {
        val withSapper = snapshot.copy(
            units = listOf(
                com.urkaaaz.contracts.UnitSnapshot(
                    entityId = EntityId("red-sapper-1"),
                    team = Team.RED,
                    unitType = UnitFactory.sapper().id,
                    x = 700f,
                    y = 400f,
                    health = UnitFactory.sapper().maxHealth,
                    maxHealth = UnitFactory.sapper().maxHealth,
                ),
            ),
        )
        val deployment = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                allowedUnits = setOf(UnitFactory.sapper()),
            ),
        ).chooseDeployment(withSapper)

        assertEquals(null, deployment)
    }

    @Test
    fun aiDoesNotDecideWhenItIsNotItsTurn() {
        val commands = AiAgent(
            AiConfiguration(PlayerId("ai-player"), Team.RED),
        ).decide(snapshot.copy(activeTeam = Team.BLUE))

        assertEquals(3, commands.size)
    }
}
