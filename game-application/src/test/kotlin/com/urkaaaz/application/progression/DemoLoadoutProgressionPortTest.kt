package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoLoadoutProgressionPortTest {
    @Test
    fun demoUnlocksAllCatalogSpellsAndMakesLimitedItemsAvailable() = runBlocking {
        val port = DemoLoadoutProgressionPort()

        val snapshot = (
            port.snapshot(1) as LoadoutProgressionRead.Available
            ).snapshot

        assertEquals(
            LoadoutCatalog.spells.filterNot { it.special }.map { it.id }.toSet(),
            snapshot.unlockedSpellTypes,
        )
        assertEquals(
            LoadoutCatalog.ammunition.filterNot { it.unlimited }
                .map { it.id to Int.MAX_VALUE }.toMap(),
            snapshot.ammunitionStocks,
        )
        assertEquals(
            LoadoutCatalog.spells.filter { it.special }
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
}

private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
