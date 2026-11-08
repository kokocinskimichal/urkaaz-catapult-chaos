package com.urkaaaz.application

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BossMatchSessionTest {
    @Test
    fun campaignBossScenarioLoadsJsonProfileAndPinsBalanceIntoReplay() {
        val scenario = CampaignLevelDefinition.forLevel(
            5,
            GameBalanceFixtures.balance.runtime.campaign,
        ).toMatchScenario()
        val session = MatchSession(scenario = scenario, randomSeedProvider = { 12L })
        val matchId = MatchId("boss-match")
        val snapshot = session.dispatch(
            MatchCommand.Start(
                commandId = CommandId("start"),
                matchId = matchId,
                playerId = PlayerId("player"),
            ),
        )

        assertEquals(
            "boss.central-ancient-war-oak.level-05",
            snapshot.boss?.profileId,
        )
        assertEquals("ancient_war_oak", snapshot.boss?.presentationKey)
        assertEquals(scenario.mapDefinition, snapshot.mapDefinition)
        assertEquals("game-balance-13", snapshot.balanceRevision)
        assertEquals(1.2f, snapshot.gameplaySpeedMultiplier)
        assertTrue(snapshot.balanceContentHash?.matches(Regex("[a-f0-9]{64}")) == true)
        assertTrue(session.consumeEvents().any { it is MatchEvent.BossSpawned })

        val replay = assertNotNull(session.replay())
        assertEquals(snapshot.balanceRevision, replay.configuration.balanceRevision)
        assertEquals(snapshot.balanceContentHash, replay.configuration.balanceContentHash)
        val replayed = MatchReplayPlayer.play(replay)
        assertEquals(snapshot.boss, replayed.snapshot.boss)
        assertEquals(snapshot.balanceContentHash, replayed.snapshot.balanceContentHash)
    }

    @Test
    fun chapterOneFinalBossLoadsTheBoulderbloomPresentation() {
        val scenario = CampaignLevelDefinition.forLevel(
            10,
            GameBalanceFixtures.balance.runtime.campaign,
        ).toMatchScenario()
        val session = MatchSession(scenario = scenario, randomSeedProvider = { 12L })
        val snapshot = session.dispatch(
            MatchCommand.Start(
                commandId = CommandId("start-level-10"),
                matchId = MatchId("boulderbloom-match"),
                playerId = PlayerId("player"),
            ),
        )

        assertEquals("boss.mobile-boulderbloom.level-10", snapshot.boss?.profileId)
        assertEquals("boulderbloom", snapshot.boss?.presentationKey)
    }
}
