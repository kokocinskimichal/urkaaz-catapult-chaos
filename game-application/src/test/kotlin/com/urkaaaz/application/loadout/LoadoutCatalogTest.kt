package com.urkaaaz.application.loadout

import com.urkaaaz.domain.AmmunitionCatalog as DomainAmmunitionCatalog
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.UnitConfigurations
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadoutCatalogTest {
    @Test
    fun presentationCatalogProjectsCanonicalDomainDefinitions() {
        assertEquals(
            UnitConfigurations.all.map { it.id to it.supplyCost },
            LoadoutCatalog.units.map { it.id to it.supplyCost },
        )
        assertEquals(
            AmmunitionType.values().map { it.name to it.unlimited },
            LoadoutCatalog.ammunition.map { it.id to it.unlimited },
        )
        assertEquals(
            DomainAmmunitionCatalog.all.map {
                Triple(it.type.name, it.damage.unitDamage, it.damage.fortressDamage)
            },
            LoadoutCatalog.ammunition.map {
                Triple(it.id, it.unitDamage, it.fortressDamage)
            },
        )
        assertEquals(
            SpellCatalog.all.map { Triple(it.type.name, it.goldCost, it.special) },
            LoadoutCatalog.spells.map { Triple(it.id, it.goldCost, it.special) },
        )
        assertEquals(
            SpellCatalog.all.map { Triple(it.type.name, it.manaCost, it.isImplemented) },
            LoadoutCatalog.spells.map { Triple(it.id, it.manaCost, it.implemented) },
        )
        assertEquals(
            SpellCatalog.definition(SpellType.HASTE).recastableWhileActive,
            LoadoutCatalog.findSpell(SpellType.HASTE.name)?.recastableWhileActive,
        )
        assertEquals(
            15_000L,
            LoadoutCatalog.findSpell(SpellType.HASTE.name)?.activeDurationMilliseconds,
        )
        assertEquals(20, LoadoutCatalog.maximumSupply)
        assertEquals(100, LoadoutCatalog.maximumMana)
    }
}
