package com.urkaaaz.domain

import com.urkaaaz.domain.UnitIds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmmunitionDefinitionsTest {
    @Test
    fun catalogContainsOneExplicitDefinitionForEveryAmmunitionType() {
        assertEquals(AmmunitionType.entries.toSet(), GameBalanceFixtures.balance.ammunition.all.map { it.type }.toSet())
        assertEquals(AmmunitionType.entries.size, GameBalanceFixtures.balance.ammunition.all.size)
    }

    @Test
    fun definitionsExposeIndependentReloadAndFlightProfiles() {
        assertTrue(
            GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.PLAGUE_CAULDRON)
                .reloadSeconds >
                GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK).reloadSeconds,
        )
        assertTrue(
            GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.PLAGUE_CAULDRON)
                .flight.mass >
                GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK).flight.mass,
        )
    }

    @Test
    fun fireRainUsesStandardSpeedAndConfiguredApexFragmentationProfile() {
        val fireRain = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.FIRE_RAIN)
        val profile = requireNotNull(
            fireRain.flight.apexFragmentation,
        )

        assertEquals(8f, fireRain.flight.initialSpeedMultiplier)
        assertEquals(3, profile.fragmentsPerSide)
        assertEquals(120f, profile.minimumReferenceSpeed)
        assertEquals(0.09f, profile.horizontalSpreadPerStep)
        assertEquals(0.10f, profile.initialDownwardSpeed)
        assertEquals(0.025f, profile.downwardSpreadPerStep)
        assertEquals(
            null,
            GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK)
                .flight.apexFragmentation,
        )
    }

    @Test
    fun rockDamagesUnitsAndFortressesAndCanCollideWithUnits() {
        val rock = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK)

        assertEquals(14f, rock.collision.hitRadius)
        assertEquals(50, rock.damage.unitDamage)
        assertEquals(50, rock.damage.fortressDamage)
        assertEquals(28f, rock.damage.unitRadius)
        assertEquals(50, rock.damage.minimumDamage)
        assertTrue(AmmunitionHitLayer.UNIT in rock.collision.hitLayers)
        assertTrue(AmmunitionHitLayer.UNIT in rock.availableLayers)
    }

    @Test
    fun lingeringAmmunitionCarriesExplicitStatusAndWindPolicy() {
        val fireRain = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.FIRE_RAIN)
        val plague = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.PLAGUE_CAULDRON)
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
        assertEquals(1f, fireBehavior.windDriftMultiplier)
        assertFalse(fireBehavior.followsTerrain)
        assertEquals(
            FriendlyFirePolicy.ENEMY_ONLY,
            fireRain.collision.friendlyFire,
        )
        assertTrue(plagueBehavior.driftsWithWind)
        assertEquals(0.75f, plagueBehavior.windDriftMultiplier)
        assertTrue(plagueBehavior.followsTerrain)
        assertEquals(8, plagueBehavior.damagePerTick)
        assertEquals(
            300f,
            GameBalanceFixtures.balance.ammunition
                .definition(AmmunitionType.PLAGUE_CAULDRON).damage.unitRadius,
        )
        assertEquals(300f, plague.damage.unitRadius)
        assertEquals(300f, plague.damage.fortressRadius)
        assertEquals(300f, plagueBehavior.radius)
        assertEquals(6f, plagueBehavior.durationSeconds)
        assertEquals(0, plague.damage.fortressDamage)
        assertEquals(0, plagueBehavior.fortressDamagePerTick)
    }

    @Test
    fun approvedAmmunitionBalanceIsExplicitInDefinitions() {
        val siegeBomb = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.SIEGE_BOMB)

        assertEquals(72, siegeBomb.damage.unitDamage)
        assertEquals(100, siegeBomb.damage.fortressDamage)
    }

    @Test
    fun powderBarrelHasLargeBlastAndFriendlyFire() {
        val powderBarrel = GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.POWDER_BARREL)
        val explosion = powderBarrel.behavior as AmmunitionImpactBehavior.Explosion

        assertEquals(FriendlyFirePolicy.ALL_UNITS, powderBarrel.collision.friendlyFire)
        assertEquals(110f, powderBarrel.collision.hitRadius)
        assertEquals(120, powderBarrel.damage.unitDamage)
        assertEquals(120, powderBarrel.damage.fortressDamage)
        assertEquals(220f, powderBarrel.damage.unitRadius)
        assertEquals(AmmunitionFalloff.LINEAR, powderBarrel.damage.falloff)
        assertEquals(220f, explosion.radius)
        assertEquals(150f, powderBarrel.terrain.craterRadius)
        assertEquals(90f, powderBarrel.terrain.craterDepth)
    }

    @Test
    fun allAmmunitionProjectileHitboxesAreHalfTheirPreviousRadii() {
        val expectedHitRadii = mapOf(
            AmmunitionType.ROCK to 14f,
            AmmunitionType.SIEGE_BOMB to 57.5f,
            AmmunitionType.POWDER_BARREL to 110f,
            AmmunitionType.FIRE_RAIN to 34f,
            AmmunitionType.PLAGUE_CAULDRON to 130f,
        )

        expectedHitRadii.forEach { (type, expectedRadius) ->
            assertEquals(
                expectedRadius,
                GameBalanceFixtures.balance.ammunition.definition(type).collision.hitRadius,
                0.001f,
                "$type hit radius",
            )
        }
    }
}
