package com.urkaaaz.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmmunitionDefinitionsTest {
    @Test
    fun catalogContainsOneExplicitDefinitionForEveryAmmunitionType() {
        assertEquals(AmmunitionType.entries.toSet(), AmmunitionCatalog.all.map { it.type }.toSet())
        assertEquals(AmmunitionType.entries.size, AmmunitionCatalog.all.size)
    }

    @Test
    fun definitionsExposeIndependentReloadAndFlightProfiles() {
        assertTrue(
            AmmunitionCatalog.definition(AmmunitionType.PLAGUE_CAULDRON)
                .reloadSeconds >
                AmmunitionCatalog.definition(AmmunitionType.ROCK).reloadSeconds,
        )
        assertTrue(
            AmmunitionCatalog.definition(AmmunitionType.PLAGUE_CAULDRON)
                .flight.mass >
                AmmunitionCatalog.definition(AmmunitionType.ROCK).flight.mass,
        )
    }

    @Test
    fun lingeringAmmunitionCarriesExplicitStatusAndWindPolicy() {
        val fireRain = AmmunitionCatalog.definition(AmmunitionType.FIRE_RAIN)
        val plague = AmmunitionCatalog.definition(AmmunitionType.PLAGUE_CAULDRON)

        assertEquals(
            UnitStatus.BURNING,
            (fireRain.behavior as AmmunitionImpactBehavior.LingeringArea).status,
        )
        assertTrue(
            (plague.behavior as AmmunitionImpactBehavior.LingeringArea).driftsWithWind,
        )
    }
}
