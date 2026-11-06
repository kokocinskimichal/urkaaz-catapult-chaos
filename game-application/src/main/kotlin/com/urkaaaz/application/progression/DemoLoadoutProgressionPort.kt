package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult

class DemoLoadoutProgressionPort(
    loadoutCatalog: LoadoutCatalog = LoadoutCatalog.bundled(),
    stockPerItem: Int = Int.MAX_VALUE,
) : LoadoutProgressionPort {
    init {
        require(stockPerItem > 0) { "demo loadout stock must be positive" }
    }

    private val demoSnapshot = LoadoutProgressionSnapshot(
        unlockedSpellTypes = loadoutCatalog.spells
            .asSequence()
            .filterNot { it.special }
            .mapTo(mutableSetOf()) { it.id },
        ammunitionStocks = loadoutCatalog.ammunition
            .asSequence()
            .filterNot { it.unlimited }
            .associate { it.id to stockPerItem },
        specialSpellStocks = loadoutCatalog.spells
            .asSequence()
            .filter { it.special }
            .associate { it.id to stockPerItem },
        goldBalance = 0,
    )

    override suspend fun snapshot(level: Int): LoadoutProgressionRead {
        if (level <= 0) {
            return LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_LEVEL)
        }
        return LoadoutProgressionRead.Available(demoSnapshot)
    }

    override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
        LoadoutPurchaseResult.Blocked

}
