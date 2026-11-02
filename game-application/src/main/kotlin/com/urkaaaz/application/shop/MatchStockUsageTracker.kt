package com.urkaaaz.application.shop

import com.urkaaaz.application.reward.MatchEventObserver
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.Team

class MatchStockUsageTracker(
    initialInventory: PlayerInventoryConfig,
) : MatchEventObserver {
    private var inventory = initialInventory
    private var currentMatchId: MatchId? = null
    private var generation = 0L
    private val processedEventIds = mutableSetOf<EventId>()
    private val ammunitionUsed = linkedMapOf<String, Long>()
    private val specialSpellsUsed = linkedMapOf<String, Long>()
    private var usageOverflowed = false

    override fun onMatchGenerationStarted(matchId: MatchId) {
        generation += 1
        currentMatchId = matchId
        processedEventIds.clear()
        ammunitionUsed.clear()
        specialSpellsUsed.clear()
        usageOverflowed = false
    }

    override fun onEvents(events: List<MatchEvent>) {
        val trackedMatchId = currentMatchId ?: return
        events.forEach { event ->
            if (event.matchId != trackedMatchId || !processedEventIds.add(event.eventId)) {
                return@forEach
            }
            when (event) {
                is MatchEvent.ProjectileFired -> {
                    if (
                        event.shooterTeam == Team.BLUE &&
                        (inventory.ammunitionStocks[event.ammunitionType] ?: 0) > 0
                    ) {
                        ammunitionUsed.increment(event.ammunitionType)
                    }
                }
                is MatchEvent.SpellCast -> {
                    if (
                        event.playerId == inventory.playerId &&
                        (inventory.specialSpellStocks[event.spellType] ?: 0) > 0
                    ) {
                        specialSpellsUsed.increment(event.spellType)
                    }
                }
                else -> Unit
            }
        }
    }

    fun updateInventoryForNextGeneration(nextInventory: PlayerInventoryConfig) {
        inventory = nextInventory
    }

    fun hasUsageOverflowed(): Boolean = usageOverflowed

    fun settlementRequest(): MatchStockSettlementRequest? {
        val matchId = currentMatchId ?: return null
        if (usageOverflowed) return null
        return MatchStockSettlementRequest(
            id = MatchStockSettlementId(matchId, generation),
            usage = MatchStockUsage(
                ammunitionUsed = ammunitionUsed.toMap(),
                specialSpellsUsed = specialSpellsUsed.toMap(),
            ),
        )
    }

    private fun MutableMap<String, Long>.increment(identifier: String) {
        val current = this[identifier] ?: 0L
        if (current == Long.MAX_VALUE) {
            usageOverflowed = true
        } else {
            this[identifier] = current + 1L
        }
    }
}

data class MatchStockSettlementRequest(
    val id: MatchStockSettlementId,
    val usage: MatchStockUsage,
)
