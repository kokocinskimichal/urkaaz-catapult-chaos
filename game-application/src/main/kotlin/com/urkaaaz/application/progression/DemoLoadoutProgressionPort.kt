package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult

class DemoLoadoutProgressionPort : LoadoutProgressionPort {
    override suspend fun snapshot(level: Int): LoadoutProgressionRead {
        if (level <= 0) {
            return LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_LEVEL)
        }
        return LoadoutProgressionRead.Available(DEMO_SNAPSHOT)
    }

    override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
        LoadoutPurchaseResult.Blocked

    private companion object {
        val DEMO_SNAPSHOT = LoadoutProgressionSnapshot(
            unlockedSpellTypes = LoadoutCatalog.spells
                .asSequence()
                .filterNot { it.special }
                .mapTo(mutableSetOf()) { it.id },
            ammunitionStocks = LoadoutCatalog.ammunition
                .asSequence()
                .filterNot { it.unlimited }
                .associate { it.id to Int.MAX_VALUE },
            specialSpellStocks = LoadoutCatalog.spells
                .asSequence()
                .filter { it.special }
                .associate { it.id to Int.MAX_VALUE },
            goldBalance = 0,
        )
    }
}
