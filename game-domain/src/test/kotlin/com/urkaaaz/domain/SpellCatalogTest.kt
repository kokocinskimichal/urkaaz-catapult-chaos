package com.urkaaaz.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpellCatalogTest {
    @Test
    fun catalogDefinesEveryStableSpellWithLegacyCostsAndTargetCategory() {
        assertEquals(SpellType.values().toSet(), SpellCatalog.all.map { it.type }.toSet())
        assertEquals(SpellCatalog.all.size, SpellCatalog.all.map { it.type }.distinct().size)
        assertTrue(SpellCatalog.all.all { it.goldCost >= 0 && it.manaCost > 0 })
        assertEquals(
            mapOf(
                SpellType.ICE_TRAP to 50,
                SpellType.RUNIC_BASTION to 70,
                SpellType.HASTE to 50,
                SpellType.HEAL to 50,
                SpellType.FORTRESS_SHIELD to 80,
                SpellType.CATAPULT_LOCK to 60,
                SpellType.SLOWING_FIELD to 50,
                SpellType.VAMPIRIC_BANNER to 60,
                SpellType.STONE_SKIN to 60,
                SpellType.SIEGE_FRENZY to 60,
                SpellType.BERSERKER to 100,
            ),
            SpellCatalog.all.associate { it.type to it.manaCost },
        )
        assertEquals(
            mapOf(
                SpellType.ICE_TRAP to 500,
                SpellType.RUNIC_BASTION to 1_200,
                SpellType.HASTE to 900,
                SpellType.HEAL to 900,
                SpellType.FORTRESS_SHIELD to 0,
                SpellType.CATAPULT_LOCK to 1_000,
                SpellType.SLOWING_FIELD to 1_000,
                SpellType.VAMPIRIC_BANNER to 1_100,
                SpellType.STONE_SKIN to 1_100,
                SpellType.SIEGE_FRENZY to 1_200,
                SpellType.BERSERKER to 2_000,
            ),
            SpellCatalog.all.associate { it.type to it.goldCost },
        )
        assertEquals(
            mapOf(
                SpellType.ICE_TRAP to SpellTargetCategory.ENEMY_FORCE,
                SpellType.RUNIC_BASTION to SpellTargetCategory.OWN_GATE,
                SpellType.HASTE to SpellTargetCategory.ALLIED_FORCE,
                SpellType.HEAL to SpellTargetCategory.ALLIED_FORCE,
                SpellType.FORTRESS_SHIELD to SpellTargetCategory.OWN_FORTRESS,
                SpellType.CATAPULT_LOCK to SpellTargetCategory.ENEMY_SIEGE_ENGINE,
                SpellType.SLOWING_FIELD to SpellTargetCategory.ENEMY_FORCE,
                SpellType.VAMPIRIC_BANNER to SpellTargetCategory.ALLIED_FORCE,
                SpellType.STONE_SKIN to SpellTargetCategory.ALLIED_FORCE,
                SpellType.SIEGE_FRENZY to SpellTargetCategory.ALLIED_FORCE,
                SpellType.BERSERKER to SpellTargetCategory.ALLIED_UNIT,
            ),
            SpellCatalog.all.associate { it.type to it.targetCategory },
        )
        assertEquals(
            setOf(
                SpellType.CATAPULT_LOCK,
                SpellType.SLOWING_FIELD,
                SpellType.VAMPIRIC_BANNER,
                SpellType.STONE_SKIN,
                SpellType.SIEGE_FRENZY,
                SpellType.BERSERKER,
            ),
            SpellCatalog.all.filter { it.special }.map { it.type }.toSet(),
        )
        assertEquals(
            setOf(
                SpellType.ICE_TRAP,
                SpellType.RUNIC_BASTION,
                SpellType.HASTE,
                SpellType.HEAL,
                SpellType.FORTRESS_SHIELD,
                SpellType.CATAPULT_LOCK,
                SpellType.SLOWING_FIELD,
                SpellType.VAMPIRIC_BANNER,
                SpellType.STONE_SKIN,
                SpellType.SIEGE_FRENZY,
                SpellType.BERSERKER,
            ),
            SpellCatalog.all.map { it.type }.toSet(),
        )
    }

    @Test
    fun onlyFortressShieldHasAnImplementedRuntimeEffect() {
        assertEquals(
            setOf(SpellType.FORTRESS_SHIELD),
            SpellCatalog.all.filter { it.isImplemented }.map { it.type }.toSet(),
        )
        val shield = SpellCatalog.definition(SpellType.FORTRESS_SHIELD)
        assertEquals(80, shield.manaCost)
        assertEquals(SpellTargetCategory.OWN_FORTRESS, shield.targetCategory)
        assertEquals(SpellEffectDefinition.FortressShield(150), shield.effect)
        assertFalse(SpellCatalog.definition(SpellType.ICE_TRAP).isImplemented)
    }
}
