package com.urkaaaz.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameBalanceTest {
    @Test
    fun defaultBalanceMatchesShippedCatalogs() {
        assertEquals(UnitConfigurations.DEFENDER.maxHealth, GameBalance.default.units.byId("DEFENDER").maxHealth)
        assertEquals(
            AmmunitionCatalog.default.definition(AmmunitionType.SIEGE_BOMB).reloadSeconds,
            GameBalance.default.ammunition.definition(AmmunitionType.SIEGE_BOMB).reloadSeconds,
        )
    }

    @Test
    fun tunedBalanceScalesOnlyRequestedEntriesAndLeavesGlobalsUntouched() {
        val balance = GameBalance.tuned(
            unitTunings = mapOf("DEFENDER" to UnitTuning(healthMultiplier = 2f)),
            ammunitionTunings = mapOf(AmmunitionType.SIEGE_BOMB to AmmunitionTuning(reloadMultiplier = 2f)),
            supply = SupplyBalance(startingSupply = 10),
        )

        assertEquals(UnitConfigurations.DEFENDER.maxHealth * 2, balance.units.byId("DEFENDER").maxHealth)
        assertEquals(UnitConfigurations.SAPPER.maxHealth, balance.units.byId("SAPPER").maxHealth)
        assertEquals(
            AmmunitionCatalog.default.definition(AmmunitionType.SIEGE_BOMB).reloadSeconds * 2f,
            balance.ammunition.definition(AmmunitionType.SIEGE_BOMB).reloadSeconds,
        )
        assertEquals(10, balance.supply.startingSupply)
        assertEquals(UnitConfigurations.DEFENDER.maxHealth, UnitFactory.defender().maxHealth)
    }

    @Test
    fun unknownUnitTuningIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            GameBalance.tuned(unitTunings = mapOf("GHOST" to UnitTuning()))
        }
    }
}
