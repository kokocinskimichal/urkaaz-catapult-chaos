package com.urkaaaz.application.loadout

import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.domain.SpellType
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadoutCatalogTest {
    private val catalog = LoadoutCatalog(GameBalanceFixtures.balance)

    @Test
    fun presentationCatalogProjectsCanonicalDomainDefinitions() {
        assertEquals(
            GameBalanceFixtures.balance.units.all.map { it.id to it.supplyCost },
            catalog.units.map { it.id to it.supplyCost },
        )
        assertEquals(
            GameBalanceFixtures.balance.ammunition.all.map { it.type.name to it.unlimited },
            catalog.ammunition.map { it.id to it.unlimited },
        )
        assertEquals(
            GameBalanceFixtures.balance.ammunition.all.map {
                Triple(it.type.name, it.damage.unitDamage, it.damage.fortressDamage)
            },
            catalog.ammunition.map {
                Triple(it.id, it.unitDamage, it.fortressDamage)
            },
        )
        assertEquals(
            GameBalanceFixtures.balance.spells.all.map { Triple(it.type.name, it.goldCost, it.special) },
            catalog.spells.map { Triple(it.id, it.goldCost, it.special) },
        )
        assertEquals(
            GameBalanceFixtures.balance.spells.all.map { Triple(it.type.name, it.manaCost, it.isImplemented) },
            catalog.spells.map { Triple(it.id, it.manaCost, it.implemented) },
        )
        assertEquals(
            GameBalanceFixtures.balance.spells.definition(SpellType.HASTE).recastableWhileActive,
            catalog.findSpell(SpellType.HASTE.name)?.recastableWhileActive,
        )
        assertEquals(
            15_000L,
            catalog.findSpell(SpellType.HASTE.name)?.activeDurationMilliseconds,
        )
        assertEquals(20, catalog.maximumSupply)
        assertEquals(100, catalog.maximumMana)
    }
}
