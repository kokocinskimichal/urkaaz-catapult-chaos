package com.urkaaaz.application.shop

import com.urkaaaz.application.balance.GameBalanceRepository
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.SpellType

class MatchStockSettlementUseCase(
    private val port: MatchStockSettlementPort,
    private val balance: GameBalanceConfig = GameBalanceRepository.bundled.requireConfig(),
) {
    suspend fun settle(
        id: MatchStockSettlementId,
        usage: MatchStockUsage,
    ): MatchStockSettlementResult {
        if (!usage.isValidFor(balance)) {
            return MatchStockSettlementResult.Rejected(
                MatchStockSettlementRejection.INVALID_USAGE,
            )
        }
        return port.settle(id, usage)
    }

    private fun MatchStockUsage.isValidFor(balance: GameBalanceConfig): Boolean =
        ammunitionUsed.all { (identifier, count) ->
            val type = AmmunitionType.entries.firstOrNull { it.name == identifier }
                ?: return@all false
            count <= Int.MAX_VALUE.toLong() &&
                count > 0L &&
                !balance.ammunition.definition(type).unlimited &&
                balance.ammunition.definition(type).shopCostGold != null
        } &&
            specialSpellsUsed.all { (identifier, count) ->
                val type = SpellType.entries.firstOrNull { it.name == identifier }
                    ?: return@all false
                val definition = balance.spells.find(identifier) ?: return@all false
                count <= Int.MAX_VALUE.toLong() &&
                    count > 0L &&
                    definition.special &&
                    type != SpellType.HASTE &&
                    type != SpellType.HEAL
            }
}
