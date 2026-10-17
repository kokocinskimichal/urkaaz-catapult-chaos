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
    fun fireRainUsesHalfSpeedAndConfiguredApexFragmentationProfile() {
        val fireRain = AmmunitionCatalog.definition(AmmunitionType.FIRE_RAIN)
        val profile = requireNotNull(
            fireRain.flight.apexFragmentation,
        )

        assertEquals(4f, fireRain.flight.initialSpeedMultiplier)
        assertEquals(3, profile.fragmentsPerSide)
        assertEquals(120f, profile.minimumReferenceSpeed)
        assertEquals(0.18f, profile.horizontalSpreadPerStep)
        assertEquals(0.10f, profile.initialDownwardSpeed)
        assertEquals(0.025f, profile.downwardSpreadPerStep)
        assertEquals(
            null,
            AmmunitionCatalog.definition(AmmunitionType.ROCK)
                .flight.apexFragmentation,
        )
    }

    @Test
    fun rockDamagesUnitsAndFortressesAndCanCollideWithUnits() {
        val rock = AmmunitionCatalog.definition(AmmunitionType.ROCK)

        assertEquals(50, rock.damage.unitDamage)
        assertEquals(50, rock.damage.fortressDamage)
        assertEquals(28f, rock.damage.unitRadius)
        assertEquals(50, rock.damage.minimumDamage)
        assertTrue(AmmunitionHitLayer.UNIT in rock.collision.hitLayers)
        assertTrue(AmmunitionHitLayer.UNIT in rock.availableLayers)
    }

    @Test
    fun lingeringAmmunitionCarriesExplicitStatusAndWindPolicy() {
        val fireRain = AmmunitionCatalog.definition(AmmunitionType.FIRE_RAIN)
        val plague = AmmunitionCatalog.definition(AmmunitionType.PLAGUE_CAULDRON)
        val fireBehavior = fireRain.behavior as AmmunitionImpactBehavior.LingeringArea
        val plagueBehavior = plague.behavior as AmmunitionImpactBehavior.LingeringArea

        assertEquals(
            UnitStatus.BURNING,
            fireBehavior.status,
        )
        assertEquals(4, fireBehavior.damagePerTick)
        assertEquals(4, fireBehavior.fortressDamagePerTick)
        assertEquals(9f, fireBehavior.durationSeconds)
        assertEquals(0.5f, fireBehavior.tickIntervalSeconds)
        assertEquals(FriendlyFirePolicy.ALL_UNITS, fireBehavior.unitFriendlyFire)
        assertEquals(
            FriendlyFirePolicy.ENEMY_ONLY,
            fireRain.collision.friendlyFire,
        )
        assertTrue(plagueBehavior.driftsWithWind)
        assertEquals(8, plagueBehavior.damagePerTick)
        assertEquals(0, plague.damage.fortressDamage)
        assertEquals(0, plagueBehavior.fortressDamagePerTick)
    }

    @Test
    fun approvedAmmunitionBalanceIsExplicitInDefinitions() {
        val siegeBomb = AmmunitionCatalog.definition(AmmunitionType.SIEGE_BOMB)

        assertEquals(72, siegeBomb.damage.unitDamage)
        assertEquals(100, siegeBomb.damage.fortressDamage)
    }

    @Test
    fun powderBarrelHasLargeBlastAndFriendlyFire() {
        val powderBarrel = AmmunitionCatalog.definition(AmmunitionType.POWDER_BARREL)
        val explosion = powderBarrel.behavior as AmmunitionImpactBehavior.Explosion

        assertEquals(FriendlyFirePolicy.ALL_UNITS, powderBarrel.collision.friendlyFire)
        assertEquals(220f, powderBarrel.collision.hitRadius)
        assertEquals(120, powderBarrel.damage.unitDamage)
        assertEquals(120, powderBarrel.damage.fortressDamage)
        assertEquals(220f, powderBarrel.damage.unitRadius)
        assertEquals(AmmunitionFalloff.LINEAR, powderBarrel.damage.falloff)
        assertEquals(220f, explosion.radius)
        assertEquals(150f, powderBarrel.terrain.craterRadius)
        assertEquals(90f, powderBarrel.terrain.craterDepth)
    }
}
