package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoLoadoutProgressionPortTest {
    private val catalog = LoadoutCatalog(GameBalanceFixtures.balance)

    @Test
    fun demoUnlocksAllCatalogSpellsAndMakesLimitedItemsAvailable() = runBlocking {
        val port = DemoLoadoutProgressionPort(catalog)

        val snapshot = (
            port.snapshot(1) as LoadoutProgressionRead.Available
            ).snapshot

        assertEquals(
            catalog.spells.filterNot { it.special }.map { it.id }.toSet(),
            snapshot.unlockedSpellTypes,
        )
        assertEquals(
            catalog.ammunition.filterNot { it.unlimited }
                .map { it.id to Int.MAX_VALUE }.toMap(),
            snapshot.ammunitionStocks,
        )
        assertEquals(
            catalog.spells.filter { it.special }
                .map { it.id to Int.MAX_VALUE }.toMap(),
            snapshot.specialSpellStocks,
        )
    }

    @Test
    fun demoPortStillRejectsInvalidLevelInput() = runBlocking {
        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_LEVEL),
            DemoLoadoutProgressionPort().snapshot(0),
        )
    }

    @Test
    fun configuredTestStockReplacesTheDefaultEffectivelyUnlimitedStock() = runBlocking {
        val port = DemoLoadoutProgressionPort(catalog, stockPerItem = 99)
        val snapshot = (
            port.snapshot(10) as LoadoutProgressionRead.Available
            ).snapshot

        assertTrue(snapshot.ammunitionStocks.values.all { it == 99 })
        assertTrue(snapshot.specialSpellStocks.values.all { it == 99 })
    }
}

private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
