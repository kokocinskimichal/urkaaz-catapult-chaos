package com.urkaaaz.application.shop

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MatchStockUsageTrackerTest {
    @Test
    fun tracksOnlyUniqueBlueStockUsesForCurrentGeneration() {
        val playerId = PlayerId("blue-player")
        val matchId = MatchId("tracker-match")
        val tracker = MatchStockUsageTracker(
            PlayerInventoryConfig(
                playerId = playerId,
                ammunitionStocks = mapOf("POWDER_BARREL" to 2),
                specialSpellStocks = mapOf("CATAPULT_LOCK" to 1),
            ),
        )
        tracker.onMatchGenerationStarted(matchId)
        val shot = MatchEvent.ProjectileFired(
            eventId = EventId("shot-1"),
            matchId = matchId,
            simulationTimeMilliseconds = 1,
            projectileId = EntityId("projectile-1"),
            catapultId = EntityId("blue-catapult"),
            ammunitionType = "POWDER_BARREL",
            shooterTeam = Team.BLUE,
        )
        tracker.onEvents(
            listOf(
                shot,
                shot,
                shot.copy(
                    eventId = EventId("red-shot"),
                    projectileId = EntityId("projectile-red"),
                    catapultId = EntityId("red-catapult"),
                    shooterTeam = Team.RED,
                ),
                shot.copy(
                    eventId = EventId("unowned-ammo"),
                    projectileId = EntityId("projectile-2"),
                    ammunitionType = "FIRE_RAIN",
                ),
                MatchEvent.SpellCast(
                    eventId = EventId("spell-1"),
                    matchId = matchId,
                    simulationTimeMilliseconds = 2,
                    playerId = playerId,
                    spellType = "CATAPULT_LOCK",
                ),
                MatchEvent.SpellCast(
                    eventId = EventId("other-spell"),
                    matchId = matchId,
                    simulationTimeMilliseconds = 3,
                    playerId = playerId,
                    spellType = "HASTE",
                ),
                shot.copy(
                    eventId = EventId("other-match"),
                    matchId = MatchId("other-match"),
                    projectileId = EntityId("projectile-3"),
                ),
            ),
        )

        val request = assertNotNull(tracker.settlementRequest())
        assertEquals(MatchStockSettlementId(matchId, 1), request.id)
        assertEquals(mapOf("POWDER_BARREL" to 1L), request.usage.ammunitionUsed)
        assertEquals(mapOf("CATAPULT_LOCK" to 1L), request.usage.specialSpellsUsed)
    }

    @Test
    fun generationResetClearsUsageAndAllowsRepeatedEventIds() {
        val matchId = MatchId("restarted-match")
        val tracker = MatchStockUsageTracker(
            PlayerInventoryConfig(
                playerId = PlayerId("blue-player"),
                ammunitionStocks = mapOf("FIRE_RAIN" to 2),
                specialSpellStocks = emptyMap(),
            ),
        )
        val event = MatchEvent.ProjectileFired(
            eventId = EventId("event-1"),
            matchId = matchId,
            simulationTimeMilliseconds = 1,
            projectileId = EntityId("projectile-1"),
            catapultId = EntityId("blue-catapult"),
            ammunitionType = "FIRE_RAIN",
            shooterTeam = Team.BLUE,
        )

        tracker.onMatchGenerationStarted(matchId)
        tracker.onEvents(listOf(event))
        tracker.onMatchGenerationStarted(matchId)
        tracker.onEvents(listOf(event))

        val request = assertNotNull(tracker.settlementRequest())
        assertEquals(MatchStockSettlementId(matchId, 2), request.id)
        assertEquals(mapOf("FIRE_RAIN" to 1L), request.usage.ammunitionUsed)
    }
}
