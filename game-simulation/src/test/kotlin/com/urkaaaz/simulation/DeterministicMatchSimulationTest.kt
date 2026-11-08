package com.urkaaaz.simulation

import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.domain.UnitIds

import com.urkaaaz.contracts.CatapultPresentationContract
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.RunicBastionAnimationTiming
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitHitboxProfile
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.Aim
import com.urkaaaz.domain.Catapult
import com.urkaaaz.domain.Fortress
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.WorldPosition
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeterministicMatchSimulationTest {
    private val fortressMaximumHealth =
        GameBalanceFixtures.balance.runtime.fortresses.standardMaximumHealth
    private val config = SimulationConfig(
        balance = GameBalanceFixtures.balance,
        bounds = WorldBounds(width = 1_000f, height = 600f),
        gravityAccelerationY = 0f,
        windAccelerationX = 2f,
    )

    private fun freeUnit(type: UnitDefinition) =
        UnitFactory.create(type.configuration.copy(supplyCost = 0))

    private fun durableDefender() =
        UnitFactory.create(GameBalanceFixtures.balance.units.configuration(UnitIds.DEFENDER).copy(maxHealth = 260, supplyCost = 0))

    private fun redDefenderPosition(): com.urkaaaz.contracts.UnitSnapshot {
        val simulation = DeterministicMatchSimulation(
            MatchId("red-defender-position"),
            config,
        )
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.RED)
        return simulation.snapshot().units.single()
    }

    private fun horizontalProjectileSimulation(matchId: String, y: Float) =
        DeterministicMatchSimulation(
            MatchId(matchId),
            config.copy(windAccelerationX = 0f),
            blueCatapult = Catapult(
                id = EntityId("blue-catapult"),
                team = Team.BLUE,
                position = WorldPosition(100f, y),
                anchorPosition = WorldPosition(100f, y),
                aim = Aim(directionDegrees = 0f, power = 100f),
            ),
            redFortress = Fortress(
                id = EntityId("red-fortress"),
                team = Team.RED,
                center = WorldPosition(950f, -500f),
                maxHealth = fortressMaximumHealth,
            ),
        )

    private fun balanceWithGameplaySpeed(multiplier: Float) =
        GameBalanceFixtures.balance.copy(
            runtime = GameBalanceFixtures.balance.runtime.copy(
                combat = GameBalanceFixtures.balance.runtime.combat.copy(
                    gameplaySpeedMultiplier = multiplier,
                ),
            ),
        )

    private fun configWithGameplaySpeed(multiplier: Float) = config.copy(
        balance = balanceWithGameplaySpeed(multiplier),
        bounds = WorldBounds(width = 7_200f, height = 4_800f),
    )

    @Test
    fun startProducesDeterministicLifecycleEventsAndSnapshot() {
        val simulation = DeterministicMatchSimulation(MatchId("match-1"), config)

        val events = simulation.start()
        val snapshot = simulation.snapshot()

        assertEquals(listOf("event-1", "event-2"), events.map { it.eventId.value })
        assertEquals(MatchStatus.RUNNING, snapshot.status)
        assertEquals(MatchPhase.PLAYER_TURN, snapshot.phase)
        assertEquals(Team.BLUE, snapshot.activeTeam)
    }

    @Test
    fun campaignBlueInventoryUsesProfileStockWhileRedKeepsBalanceStartingCount() {
        val playerId = PlayerId("inventory-player")
        val playerLoadout = PlayerLoadoutConfig(
            playerId = playerId,
            unitTypes = listOf(UnitIds.DEFENDER),
            ammunitionSlots = listOf("ROCK", "POWDER_BARREL", null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val simulation = DeterministicMatchSimulation(
            MatchId("profile-ammunition"),
            config.copy(
                playerLoadout = playerLoadout,
                playerInventory = PlayerInventoryConfig(
                    playerId = playerId,
                    ammunitionStocks = mapOf("POWDER_BARREL" to 1),
                    specialSpellStocks = emptyMap(),
                ),
                availableAmmunitionTypes = setOf("ROCK", "POWDER_BARREL"),
            ),
        )
        simulation.start()

        assertEquals(
            1,
            simulation.snapshot().resourcesByTeam[Team.BLUE]?.ammunition?.get("POWDER_BARREL"),
        )
        assertEquals(
            3,
            simulation.snapshot().resourcesByTeam[Team.RED]?.ammunition?.get("POWDER_BARREL"),
        )

        val fired = simulation.fire(
            EntityId("blue-catapult"),
            AmmunitionType.POWDER_BARREL,
            playerId,
        )
        assertTrue(fired.any {
            it is MatchEvent.ProjectileFired &&
                it.ammunitionType == AmmunitionType.POWDER_BARREL.name &&
                it.shooterTeam == Team.BLUE
        })
        simulation.advance(6_500)

        assertEquals(
            0,
            simulation.snapshot().resourcesByTeam[Team.BLUE]?.ammunition?.get("POWDER_BARREL"),
        )
        assertFailsWith<IllegalStateException> {
            simulation.fire(
                EntityId("blue-catapult"),
                AmmunitionType.POWDER_BARREL,
                playerId,
            )
        }
        assertEquals(
            0,
            simulation.snapshot().resourcesByTeam[Team.BLUE]?.ammunition?.get("POWDER_BARREL"),
        )
    }

    @Test
    fun defaultFortressCollisionMarginIsHalfThePreviousRadius() {
        assertEquals(
            23f,
            SimulationConfig(balance = GameBalanceFixtures.balance, bounds = WorldBounds(width = 1_000f, height = 600f))
                .fortressCollisionRadius,
        )
    }

    @Test
    fun manaStartsAtConfiguredValueAndRegeneratesOnDeterministicIntervals() {
        val simulation = DeterministicMatchSimulation(
            MatchId("mana-regeneration"),
            SimulationConfig(
                balance = GameBalanceFixtures.withMana(
                    GameBalanceFixtures.balance.mana.copy(startingMana = 20),
                ),
                bounds = WorldBounds(width = 1_000f, height = 600f),
            ),
        )
        simulation.start()

        assertEquals(20, simulation.snapshot().resources.mana)
        simulation.advance(799)
        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        simulation.advance(1)
        assertEquals(21, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        simulation.advance(7_200)
        assertEquals(30, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        simulation.advance(56_000)
        assertEquals(100, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        assertEquals(
            null,
            simulation.castFortressShield(PlayerId("blue-player"), Team.BLUE, targetId = null),
        )
        simulation.advance(799)
        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        simulation.advance(1)
        assertEquals(21, simulation.snapshot().resourcesByTeam[Team.BLUE]?.mana)
    }

    @Test
    fun fortressShieldRejectsActivationWhenManaIsInsufficient() {
        val simulation = DeterministicMatchSimulation(
            MatchId("insufficient-spell-mana"),
            SimulationConfig(
                balance = GameBalanceFixtures.withMana(
                    GameBalanceFixtures.balance.mana.copy(startingMana = 79),
                ),
                bounds = WorldBounds(width = 1_000f, height = 600f),
            ),
        )
        simulation.start()

        assertEquals(
            MatchError.INSUFFICIENT_RESOURCE,
            simulation.castFortressShield(PlayerId("blue-player"), Team.BLUE, targetId = null),
        )
        assertEquals(79, simulation.snapshot().resources.mana)
        assertEquals(null, simulation.snapshot().fortresses.single { it.team == Team.BLUE }.shieldHealth)
    }

    @Test
    fun fortressShieldInterceptsEnemyProjectileWithoutDamagingFortress() {
        val redFortress = Fortress(
            id = EntityId("red-fortress"),
            team = Team.RED,
            center = WorldPosition(400f, 0f),
            maxHealth = fortressMaximumHealth,
        ).withShield(150)
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("shield-impact"),
            config = SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 0f,
            ),
            blueCatapult = Catapult(
                id = EntityId("blue-catapult"),
                team = Team.BLUE,
                position = WorldPosition(50f, 0f),
                anchorPosition = WorldPosition(100f, 0f),
                aim = Aim(directionDegrees = 0f, power = 100f),
            ),
            blueFortress = Fortress(
                id = EntityId("blue-fortress"),
                team = Team.BLUE,
                center = WorldPosition(50f, 0f),
                maxHealth = fortressMaximumHealth,
            ),
            redFortress = redFortress,
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK)

        val events = simulation.advance(100)

        assertTrue(events.any { it is MatchEvent.ShieldDamaged })
        assertEquals(500, simulation.snapshot().fortresses.single { it.team == Team.RED }.health)
        assertEquals(
            150 - GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK).damage.fortressDamage,
            simulation.snapshot().fortresses.single { it.team == Team.RED }.shieldHealth,
        )
        assertTrue(simulation.snapshot().projectiles.isEmpty())
    }

    @Test
    fun fortressShieldBreaksWithoutSpillingDamageToFortress() {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("shield-break"),
            config = SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 0f,
            ),
            blueCatapult = Catapult(
                id = EntityId("blue-catapult"),
                team = Team.BLUE,
                position = WorldPosition(50f, 0f),
                anchorPosition = WorldPosition(100f, 0f),
                aim = Aim(directionDegrees = 0f, power = 100f),
            ),
            blueFortress = Fortress(
                id = EntityId("blue-fortress"),
                team = Team.BLUE,
                center = WorldPosition(50f, 0f),
                maxHealth = fortressMaximumHealth,
            ),
            redFortress = Fortress(
                id = EntityId("red-fortress"),
                team = Team.RED,
                center = WorldPosition(400f, 0f),
                maxHealth = fortressMaximumHealth,
            ).withShield(1),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK)

        val shieldDamage = simulation.advance(100)
            .filterIsInstance<MatchEvent.ShieldDamaged>()
            .single()

        val redFortress = simulation.snapshot().fortresses.single { it.team == Team.RED }
        assertEquals(0, shieldDamage.remainingHealth)
        assertEquals(null, redFortress.shieldHealth)
        assertEquals(500, redFortress.health)
        assertTrue(simulation.snapshot().projectiles.isEmpty())
    }

    @Test
    fun runicBastionAttackersWaitForDestructionAnimationBeforeMovingAndCanRecast() {
        val simulation = DeterministicMatchSimulation(
            MatchId("runic-bastion-wave"),
            SimulationConfig(
                balance = GameBalanceFixtures.withMana(
                    GameBalanceFixtures.balance.mana.copy(
                        regenerationAmount = 70,
                        regenerationIntervalMilliseconds = 1_000L,
                    ),
                ),
                bounds = WorldBounds(width = 2_000f, height = 600f),
                combatRandomSeed = 41L,
            ),
        )
        simulation.start()
        assertEquals(null, simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null))

        val firstBastion = simulation.snapshot().runicBastions.single()
        assertEquals(360, firstBastion.health)
        assertEquals(
            MatchError.SPELL_ALREADY_ACTIVE,
            simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null),
        )
        assertEquals(30, simulation.snapshot().resourcesByTeam[Team.RED]?.mana)

        val deployed = simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.sendWave(Team.BLUE)
        val events = mutableListOf<MatchEvent>()
        var elapsedSteps = 0
        while (
            simulation.snapshot().runicBastions.any { it.entityId == firstBastion.entityId } &&
            elapsedSteps < 480
        ) {
            events += simulation.advance(100L)
            elapsedSteps += 1
        }

        assertTrue(
            events.filterIsInstance<MatchEvent.AttackStarted>()
                .any { it.targetId == firstBastion.entityId },
        )
        assertTrue(
            simulation.snapshot().runicBastions.none {
                it.entityId == firstBastion.entityId
            },
        )
        val attackerXAtDestruction = simulation.snapshot().units
            .single { it.entityId == deployed.unitId }
            .x

        repeat(40) { simulation.advance(100L) }
        simulation.advance(7L)
        assertEquals(
            attackerXAtDestruction,
            simulation.snapshot().units.single { it.entityId == deployed.unitId }.x,
            0.001f,
        )
        simulation.advance(1L)
        assertEquals(
            attackerXAtDestruction,
            simulation.snapshot().units.single { it.entityId == deployed.unitId }.x,
            0.001f,
        )
        simulation.advance(100L)
        assertTrue(
            simulation.snapshot().units.single { it.entityId == deployed.unitId }.x >
                attackerXAtDestruction,
        )

        assertEquals(null, simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null))
        assertNotEquals(
            firstBastion.entityId,
            simulation.snapshot().runicBastions.single().entityId,
        )
        assertEquals(360, simulation.snapshot().runicBastions.single().health)
    }

    @Test
    fun runicBastionUsesReducedSizeAndSitsFartherAheadOfEachGate() {
        val blueFortress = Fortress(
            id = EntityId("blue-fortress"),
            team = Team.BLUE,
            center = WorldPosition(300f, 300f),
            maxHealth = fortressMaximumHealth,
        )
        val redFortress = Fortress(
            id = EntityId("red-fortress"),
            team = Team.RED,
            center = WorldPosition(1_700f, 300f),
            maxHealth = fortressMaximumHealth,
        )
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("runic-bastion-size-and-position"),
            config = SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
            ),
            blueFortress = blueFortress,
            redFortress = redFortress,
        )
        simulation.start()

        assertEquals(null, simulation.castRunicBastion(PlayerId("blue-player"), Team.BLUE, null))
        assertEquals(null, simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null))

        val bastions = simulation.snapshot().runicBastions.associateBy { it.team }
        val frontOffset = RunicBastionGeometry.FRONT_OFFSET_FROM_GATE

        assertEquals(288f, RunicBastionGeometry.WIDTH)
        assertEquals(216f, RunicBastionGeometry.HEIGHT)
        assertEquals(WorldScale.FORTRESS_WIDTH / 2f + 100f, frontOffset)
        assertEquals(
            blueFortress.center.x + 60f + frontOffset,
            bastions.getValue(Team.BLUE).centerX,
            0.001f,
        )
        assertEquals(
            redFortress.center.x - 60f - frontOffset,
            bastions.getValue(Team.RED).centerX,
            0.001f,
        )
    }

    @Test
    fun enemyProjectileStopsAtAndDamagesRunicBastion() {
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("runic-bastion-projectile"),
            config = SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
                gravityAccelerationY = 0f,
                windAccelerationX = 0f,
            ),
            blueCatapult = Catapult(
                id = EntityId("blue-catapult"),
                team = Team.BLUE,
                position = WorldPosition(100f, 400f),
                anchorPosition = WorldPosition(100f, 400f),
                aim = Aim(directionDegrees = 0f, power = 100f),
            ),
            redFortress = Fortress(
                id = EntityId("red-fortress"),
                team = Team.RED,
                center = WorldPosition(700f, 400f),
                maxHealth = fortressMaximumHealth,
            ),
        )
        simulation.start()
        simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null)
        val bastionId = simulation.snapshot().runicBastions.single().entityId
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK)

        val events = buildList {
            repeat(50) { addAll(simulation.advance(100L)) }
        }

        assertTrue(
            events.filterIsInstance<MatchEvent.ProjectileHitEntity>()
                .any { it.targetId == bastionId },
        )
        assertTrue(simulation.snapshot().projectiles.isEmpty())
        assertTrue(simulation.snapshot().runicBastions.single().health < 360)
    }

    @Test
    fun rockProjectileHitsTheUnitBodyHitboxAndDamagesTheDirectTarget() {
        val referenceUnit = redDefenderPosition()
        val hitbox = UnitHitboxProfile.forUnitType(referenceUnit.unitType)
        val projectileY = referenceUnit.y - hitbox.height / 2f
        val simulation = horizontalProjectileSimulation("rock-unit-body-hit", projectileY)
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.RED)
        val target = simulation.snapshot().units.single()
        val events = buildList {
            addAll(simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK))
            repeat(50) { addAll(simulation.advance(33L)) }
        }

        val hit = events.filterIsInstance<MatchEvent.ProjectileHitEntity>()
            .firstOrNull { it.targetId == target.entityId && it.targetLayer == "UNIT" }
            ?: error("expected a direct projectile hit on the unit body")
        assertEquals(projectileY, hit.y, 0.001f)
        assertTrue(
            events.filterIsInstance<MatchEvent.DamageApplied>().any {
                it.targetId == target.entityId &&
                    it.sourceProjectileId == hit.projectileId &&
                    it.targetLayer == "UNIT"
            },
            "expected direct-hit damage for the selected target",
        )
        assertTrue(
            simulation.snapshot().units.single { it.entityId == target.entityId }.health <
                target.maxHealth,
        )
    }

    @Test
    fun rockProjectileMissesWhenItsPathStaysOutsideTheExpandedUnitHitbox() {
        val target = redDefenderPosition()
        val hitbox = UnitHitboxProfile.forUnitType(target.unitType)
        val collisionRadius = GameBalanceFixtures.balance.ammunition
            .definition(AmmunitionType.ROCK)
            .collision
            .hitRadius
        val projectileY = target.y - hitbox.height - collisionRadius - 20f
        val simulation = horizontalProjectileSimulation("rock-unit-near-miss", projectileY)
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.RED)
        val deployed = simulation.snapshot().units.single()
        val events = buildList {
            addAll(simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK))
            repeat(30) { addAll(simulation.advance(33L)) }
        }

        assertTrue(
            events.filterIsInstance<MatchEvent.ProjectileHitEntity>()
                .none { it.targetId == deployed.entityId && it.targetLayer == "UNIT" },
        )
        assertEquals(
            deployed.maxHealth,
            simulation.snapshot().units.single { it.entityId == deployed.entityId }.health,
        )
    }

    @Test
    fun sapperSpendsItsBombOnRunicBastionAndReturns() {
        val simulation = DeterministicMatchSimulation(
            MatchId("sapper-runic-bastion"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
                combatRandomSeed = 12L,
            ),
        )
        simulation.start()
        simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null)
        val bastionId = simulation.snapshot().runicBastions.single().entityId
        val deployed = simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        val events = mutableListOf<MatchEvent>()
        for (step in 0 until 160) {
            events += simulation.advance(100L)
            if (events.any {
                    it is MatchEvent.DamageApplied && it.targetId == bastionId
                }
            ) {
                break
            }
        }

        assertTrue(
            events.filterIsInstance<MatchEvent.AttackStarted>()
                .any { it.targetId == bastionId },
        )
        assertTrue(simulation.snapshot().runicBastions.single().health < 360)
        val sapper = simulation.snapshot().units.firstOrNull {
            it.entityId == deployed.unitId
        }
        assertEquals(false, sapper?.sapperHasBomb)
    }

    @Test
    fun ordinaryEnemyUnitsCannotTargetOrDamageSapper() {
        val simulation = DeterministicMatchSimulation(
            MatchId("sapper-untargetable-by-units"),
            config.copy(bounds = WorldBounds(width = 2_000f, height = 600f)),
        )
        simulation.start()
        val deployedSapper = simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val initialSapperX = simulation.snapshot().units
            .single { it.entityId == deployedSapper.unitId }
            .x
        var furthestSapperX = initialSapperX
        val events = mutableListOf<MatchEvent>()
        repeat(200) {
            events += simulation.advance(50L)
            simulation.snapshot().units.firstOrNull {
                it.entityId == deployedSapper.unitId
            }?.let { sapper ->
                furthestSapperX = maxOf(furthestSapperX, sapper.x)
            }
        }

        assertTrue(furthestSapperX > initialSapperX + 50f)
        assertTrue(
            events.none { event ->
                when (event) {
                    is MatchEvent.TargetAcquired -> event.targetId == deployedSapper.unitId
                    is MatchEvent.AttackStarted -> event.targetId == deployedSapper.unitId
                    is MatchEvent.AttackHit -> event.targetId == deployedSapper.unitId
                    is MatchEvent.DamageApplied -> event.targetId == deployedSapper.unitId
                    else -> false
                }
            },
        )
        assertEquals(
            GameBalanceFixtures.unit(UnitIds.SAPPER).maxHealth,
            simulation.snapshot().units.single { it.entityId == deployedSapper.unitId }.health,
        )
    }

    @Test
    fun specialSpellStocksUsePlayerInventoryForBlueAndBalanceForRedAi() {
        val playerId = PlayerId("special-stock-player")
        val balance = GameBalanceFixtures.balance.copy(
            runtime = GameBalanceFixtures.balance.runtime.copy(
                aiStrategy = GameBalanceFixtures.balance.runtime.aiStrategy.copy(
                    specialSpellStockPerMatch = 2,
                ),
            ),
        )
        val simulation = DeterministicMatchSimulation(
            MatchId("special-spell-stock"),
            config.copy(
                balance = balance,
                playerLoadout = PlayerLoadoutConfig(
                    playerId = playerId,
                    unitTypes = listOf(UnitIds.DEFENDER),
                    ammunitionSlots = listOf("ROCK", null, null),
                    spellTypes = listOf("CATAPULT_LOCK", "SLOWING_FIELD"),
                ),
                playerInventory = PlayerInventoryConfig(
                    playerId = playerId,
                    ammunitionStocks = emptyMap(),
                    specialSpellStocks = mapOf(
                        "CATAPULT_LOCK" to 3,
                        "SLOWING_FIELD" to 0,
                    ),
                ),
                availableSpellTypes = setOf("CATAPULT_LOCK", "SLOWING_FIELD"),
            ),
        )
        simulation.start()

        val resources = simulation.snapshot().resourcesByTeam
        assertEquals(3, resources[Team.BLUE]?.specialSpellStocks?.get("CATAPULT_LOCK"))
        assertEquals(0, resources[Team.BLUE]?.specialSpellStocks?.get("SLOWING_FIELD"))
        assertEquals(2, resources[Team.RED]?.specialSpellStocks?.get("CATAPULT_LOCK"))
        assertEquals(2, resources[Team.RED]?.specialSpellStocks?.get("SLOWING_FIELD"))
        assertTrue(
            simulation.consumeSpecialSpellStock(
                Team.RED,
                com.urkaaaz.domain.SpellType.CATAPULT_LOCK,
            ),
        )
        assertEquals(
            1,
            simulation.snapshot().resourcesByTeam[Team.RED]
                ?.specialSpellStocks?.get("CATAPULT_LOCK"),
        )
    }

    @Test
    fun demolisherDetonatesOnRunicBastionContact() {
        val simulation = DeterministicMatchSimulation(
            MatchId("demolisher-runic-bastion"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
                combatRandomSeed = 23L,
            ),
        )
        simulation.start()
        simulation.castRunicBastion(PlayerId("red-player"), Team.RED, null)
        val bastionId = simulation.snapshot().runicBastions.single().entityId
        val deployed = simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEMOLISHER), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        val events = mutableListOf<MatchEvent>()
        var sawThrowing = false
        var previousThrowX: Float? = null
        var movedDuringThrow = false
        repeat(180) {
            events += simulation.advance(100L)
            val demolisher = simulation.snapshot().units.firstOrNull {
                it.entityId == deployed.unitId
            }
            if (demolisher?.actionState == "THROWING") {
                sawThrowing = true
                assertEquals(360, simulation.snapshot().runicBastions.single().health)
                previousThrowX?.let { previousX ->
                    movedDuringThrow = movedDuringThrow || demolisher.x > previousX
                }
                previousThrowX = demolisher.x
            }
        }

        assertTrue(
            events.filterIsInstance<MatchEvent.AttackStarted>()
                .any { it.targetId == bastionId && it.attackType == "CONTACT_EXPLOSIVE" },
        )
        assertTrue(
            events.filterIsInstance<MatchEvent.ExplosionTriggered>()
                .any { it.sourceId == deployed.unitId },
        )
        assertTrue(sawThrowing)
        assertTrue(movedDuringThrow)
        assertTrue(simulation.snapshot().runicBastions.single().health < 360)
    }

    @Test
    fun demolisherDetonatesOnEnemyFortressContactForBothTeams() {
        Team.entries.forEach { team ->
            val simulation = DeterministicMatchSimulation(
                MatchId("demolisher-fortress-${team.name.lowercase()}"),
                SimulationConfig(
                    balance = GameBalanceFixtures.balance,
                    bounds = WorldBounds(width = 4_800f, height = 600f),
                    combatRandomSeed = 23L,
                ),
            )
            simulation.start()
            val enemyTeam = if (team == Team.BLUE) Team.RED else Team.BLUE
            val enemyFortress = simulation.snapshot().fortresses
                .single { it.team == enemyTeam }
            val fortressCenterX = requireNotNull(enemyFortress.x)
            val initialHealth = enemyFortress.health
            val deployed = simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEMOLISHER), team)
            simulation.sendWave(team)

            val events = mutableListOf<MatchEvent>()
            var previousX = simulation.snapshot().units.single { it.entityId == deployed.unitId }.x
            var sawContactTarget = false
            var sawThrowing = false
            var movedDuringThrow = false
            var throwStartX: Float? = null
            var previousThrowX: Float? = null
            for (step in 0 until 1_200) {
                events += simulation.advance(50L)
                val snapshot = simulation.snapshot()
                val unit = snapshot.units.firstOrNull {
                    it.entityId == deployed.unitId
                }
                if (unit != null) {
                    sawContactTarget = sawContactTarget ||
                        (unit.targetId == enemyFortress.entityId &&
                            unit.actionState == "THROWING")
                    assertTrue(
                        if (team == Team.BLUE) unit.x >= previousX else unit.x <= previousX,
                        "$team Demolisher reversed direction at x=${unit.x}",
                    )
                    previousX = unit.x
                    if (unit.actionState == "THROWING") {
                        sawThrowing = true
                        assertTrue(
                            unit.moving,
                            "$team Demolisher should move during its throw",
                        )
                        assertEquals(
                            initialHealth,
                            snapshot.fortresses.single { it.team == enemyTeam }.health,
                        )
                        if (throwStartX == null) {
                            throwStartX = unit.x
                        } else {
                            movedDuringThrow = movedDuringThrow ||
                                (unit.x - requireNotNull(previousThrowX)) *
                                (if (team == Team.BLUE) 1f else -1f) > 0f
                        }
                        previousThrowX = unit.x
                    }
                }
                if (events.any {
                        it is MatchEvent.ExplosionTriggered && it.sourceId == deployed.unitId
                    }
                ) {
                    break
                }
                if (simulation.snapshot().status == MatchStatus.FINISHED) break
            }

            assertTrue(
                events.filterIsInstance<MatchEvent.AttackStarted>().any {
                    it.targetId == enemyFortress.entityId &&
                        it.attackType == "CONTACT_EXPLOSIVE"
                },
                "$team Demolisher should reach and attack the enemy fortress; " +
                    "unit=${simulation.snapshot().units.firstOrNull { it.entityId == deployed.unitId }}; " +
                    "attacks=${events.filterIsInstance<MatchEvent.AttackStarted>()}",
            )
            assertTrue(
                events.filterIsInstance<MatchEvent.ExplosionTriggered>()
                    .any { it.sourceId == deployed.unitId },
                "$team Demolisher should detonate at the enemy fortress",
            )
            assertTrue(
                sawContactTarget,
                "$team Demolisher should expose its fortress target during the throw",
            )
            assertTrue(sawThrowing, "$team Demolisher should enter the throw state")
            assertTrue(movedDuringThrow, "$team Demolisher should advance while throwing")
            val explosion = events.filterIsInstance<MatchEvent.ExplosionTriggered>()
                .single { it.sourceId == deployed.unitId }
            val attackStarted = events.filterIsInstance<MatchEvent.AttackStarted>()
                .single { it.attackerId == deployed.unitId && it.targetId == enemyFortress.entityId }
            assertTrue(
                explosion.simulationTimeMilliseconds - attackStarted.simulationTimeMilliseconds in 450L..550L,
                "The explosion should immediately follow the throw",
            )
            assertTrue(
                (explosion.x - requireNotNull(throwStartX)) *
                    (if (team == Team.BLUE) 1f else -1f) > 0f,
                "$team Demolisher should move forward before exploding",
            )
            val direction = if (team == Team.BLUE) 1f else -1f
            val frontFaceOffset = if (direction > 0f) {
                FortressHitboxProfile.rectangles.minOf { it.left }
            } else {
                FortressHitboxProfile.rectangles.maxOf { it.right }
            }
            val distanceToFront = (
                fortressCenterX + frontFaceOffset - explosion.x
                ) * direction
            val throwStartDistanceToFront = (
                fortressCenterX + frontFaceOffset - requireNotNull(throwStartX)
                ) * direction
            assertEquals(
                UnitHitboxProfile.forUnitType("DEMOLISHER").halfWidth,
                throwStartDistanceToFront,
                1f,
                "$team Demolisher should begin throwing at the fortress front face",
            )
            assertTrue(
                distanceToFront < UnitHitboxProfile.forUnitType("DEMOLISHER").halfWidth,
                "$team Demolisher should start at the fortress face and lunge forward",
            )
            assertTrue(
                initialHealth - simulation.snapshot().fortresses
                    .single { it.team == enemyTeam }.health >= 150,
                "$team Demolisher explosion should damage the fortress footprint",
            )
            assertTrue(
                events.filterIsInstance<MatchEvent.UnitDefeated>()
                    .any { it.unitId == deployed.unitId },
                "$team Demolisher should be consumed by its explosion",
            )
        }
    }

    @Test
    fun snapshotExposesFortressWorldPositionsAndDefaultFoundation() {
        val simulation = DeterministicMatchSimulation(
            MatchId("fortress-render-state"),
            SimulationConfig(balance = GameBalanceFixtures.balance, bounds = WorldBounds(width = 7_200f, height = 4_800f)),
        )
        simulation.start()

        val snapshot = simulation.snapshot()
        val blueFortress = snapshot.fortresses.first { it.team == Team.BLUE }
        val redFortress = snapshot.fortresses.first { it.team == Team.RED }

        assertEquals(600f, blueFortress.x)
        assertEquals(6_600f, redFortress.x)
        val expectedFortressFoundationY =
            3_456f + CatapultPresentationContract.FORTRESS_VERTICAL_OFFSET
        assertEquals(
            expectedFortressFoundationY,
            blueFortress.foundationY ?: error("Missing blue foundation"),
            0.01f,
        )
        assertEquals(
            expectedFortressFoundationY,
            redFortress.foundationY ?: error("Missing red foundation"),
            0.01f,
        )
        val blueCatapult = snapshot.catapults.first { it.team == Team.BLUE }
        val redCatapult = snapshot.catapults.first { it.team == Team.RED }
        val expectedCatapultY =
            expectedFortressFoundationY + CatapultPresentationContract.CATAPULT_VERTICAL_OFFSET
        assertEquals(490f, blueCatapult.x, 0.01f)
        assertEquals(6_710f, redCatapult.x, 0.01f)
        assertEquals(expectedCatapultY, blueCatapult.y, 0.01f)
        assertEquals(expectedCatapultY, redCatapult.y, 0.01f)
        assertEquals(560f, blueCatapult.anchorX, 0.01f)
        assertEquals(6_640f, redCatapult.anchorX, 0.01f)
        assertEquals(
            CatapultPresentationContract.anchorY(expectedFortressFoundationY, 4_800f),
            blueCatapult.anchorY,
            0.01f,
        )
        assertEquals(
            CatapultPresentationContract.anchorY(expectedFortressFoundationY, 4_800f),
            redCatapult.anchorY,
            0.01f,
        )
    }

    @Test
    fun projectileAdvancesWithWindAndCanBeInspectedInSnapshot() {
        val simulation = DeterministicMatchSimulation(MatchId("match-1"), config)
        simulation.start()

        val fired = simulation.fire(EntityId("blue-catapult"))
        simulation.advance(100)
        val snapshot = simulation.snapshot()

        assertTrue(fired.single() is MatchEvent.ProjectileFired)
        assertEquals(1, snapshot.projectiles.size)
        assertTrue(snapshot.projectiles.single().velocityX > 0f)
        assertEquals(0L, snapshot.terrain.revision)
    }

    @Test
    fun reloadIsTrackedPerAmmunitionAndSelectionDoesNotBlockAnotherType() {
        val simulation = DeterministicMatchSimulation(
            MatchId("per-ammunition-reload"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.SIEGE_BOMB)
        simulation.advance(1_000)

        val snapshotAfterImpact = simulation.snapshot()
        assertTrue(
            snapshotAfterImpact
                .reloadRemainingSecondsByAmmunition[Team.BLUE]
                ?.get(AmmunitionType.SIEGE_BOMB.name)
                ?.let { it > 0f } == true,
        )
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK)
    }

    @Test
    fun terrainImpactEmitsImpactAndDeformationAndIncrementsRevision() {
        val simulation = DeterministicMatchSimulation(
            MatchId("match-1"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"))

        val events = buildList {
            repeat(40) {
                addAll(simulation.advance(100))
            }
        }

        assertTrue(events.any { it is MatchEvent.ProjectileHitTerrain })
        assertTrue(events.any { it is MatchEvent.TerrainDeformed })
        assertEquals(1L, simulation.snapshot().terrain.revision)
    }

    @Test
    fun terrainImpactResolvesOnSurfaceForBothTeams() {
        Team.entries.forEach { team ->
            val simulation = DeterministicMatchSimulation(
                MatchId("terrain-impact-${team.name.lowercase()}"),
                SimulationConfig(
                    balance = balanceWithGameplaySpeed(1.5f),
                    bounds = WorldBounds(width = 2_000f, height = 600f),
                    gravityAccelerationY = 1_000f,
                    windAccelerationX = 0f,
                ),
            )
            simulation.start()
            val snapshotBeforeShot = simulation.snapshot()
            val terrainSnapshotBeforeShot = snapshotBeforeShot.terrain
            val terrainBeforeShot = TerrainState(
                baselineHeight = terrainSnapshotBeforeShot.heightSamples.first(),
                worldWidth = terrainSnapshotBeforeShot.worldWidth,
                sampleSpacing = terrainSnapshotBeforeShot.sampleSpacing,
                heightSamples = terrainSnapshotBeforeShot.heightSamples,
            )
            val catapultId = EntityId("${team.name.lowercase()}-catapult")
            val catapult = snapshotBeforeShot.catapults.first { it.team == team }
            assertTrue(catapult.anchorY < terrainBeforeShot.heightAt(catapult.anchorX))
            simulation.aim(catapultId, directionDegrees = 0f, power = 50f)
            simulation.fire(catapultId)

            val events = buildList {
                repeat(10) {
                    addAll(simulation.advance(100))
                }
            }
            val impact = events.filterIsInstance<MatchEvent.TerrainDeformed>().single()

            assertTrue(
                abs(impact.centerY - terrainBeforeShot.heightAt(impact.centerX)) <= 1f,
                "$team projectile impact (${impact.centerX}, ${impact.centerY}) should match " +
                    "terrain ${terrainBeforeShot.heightAt(impact.centerX)}",
            )
        }
    }

    @Test
    fun plagueEffectFollowsTerrainAndDriftsSlowerWithoutCreatingCrater() {
        val simulation = DeterministicMatchSimulation(
            MatchId("plague-match"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 2_000f, height = 600f),
                gravityAccelerationY = 1_000f,
                windAccelerationX = 0.2f,
                windAccelerationScale = 50f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.PLAGUE_CAULDRON)

        val events = buildList {
            repeat(40) {
                addAll(simulation.advance(100))
            }
        }

        assertTrue(events.any { it is MatchEvent.ProjectileHitTerrain })
        assertEquals(0L, simulation.snapshot().terrain.revision)
        val beforeDrift = simulation.snapshot()
        val initialEffect = beforeDrift.effects.single()
        assertEquals(300f, initialEffect.radius)
        assertEquals("PLAGUE_CAULDRON", initialEffect.effectType)

        simulation.advance(100L)

        val afterDrift = simulation.snapshot()
        val movedEffect = afterDrift.effects.single()
        assertEquals(
            beforeDrift.wind.direction * beforeDrift.wind.strength *
                50f * 0.75f * 0.1f,
            movedEffect.x - initialEffect.x,
            0.01f,
        )
        assertEquals(
            terrainHeightAt(afterDrift, movedEffect.x),
            movedEffect.y,
            0.01f,
        )
    }

    @Test
    fun fireRainCreatesAVisibleLingeringEffectAfterImpact() {
        val simulation = DeterministicMatchSimulation(
            MatchId("fire-rain-match"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 1_000f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)

        simulation.advance(2_000)

        assertTrue(simulation.snapshot().effects.isNotEmpty())
        assertTrue(simulation.snapshot().effects.all { it.effectType == "FIRE_RAIN" })
    }

    private fun terrainHeightAt(snapshot: MatchSnapshot, x: Float): Float {
        val terrain = snapshot.terrain
        val position = x.coerceIn(0f, terrain.worldWidth) / terrain.sampleSpacing
        val left = position.toInt().coerceIn(0, terrain.heightSamples.lastIndex)
        val right = (left + 1).coerceAtMost(terrain.heightSamples.lastIndex)
        val fraction = position - left
        return terrain.heightSamples[left] +
            (terrain.heightSamples[right] - terrain.heightSamples[left]) * fraction
    }

    @Test
    fun fireRainStartsAtTheSameSpeedAsRock() {
        val fireRainSimulation = DeterministicMatchSimulation(
            MatchId("fire-rain-speed-match"),
            config,
        )
        val rockSimulation = DeterministicMatchSimulation(
            MatchId("rock-speed-match"),
            config,
        )
        fireRainSimulation.start()
        rockSimulation.start()

        fireRainSimulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)
        rockSimulation.fire(EntityId("blue-catapult"), AmmunitionType.ROCK)

        val fireRain = fireRainSimulation.snapshot().projectiles.single()
        val rock = rockSimulation.snapshot().projectiles.single()
        assertEquals(rock.velocityX, fireRain.velocityX)
        assertEquals(rock.velocityY, fireRain.velocityY)
        assertEquals(
            GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.FIRE_RAIN).collision.hitRadius,
            fireRain.collisionRadius,
        )
        assertEquals(
            GameBalanceFixtures.balance.ammunition.definition(AmmunitionType.ROCK).collision.hitRadius,
            rock.collisionRadius,
        )
    }

    @Test
    fun fireRainLingeringTicksDamageUnitsFromBothTeams() {
        val simulation = DeterministicMatchSimulation(
            MatchId("fire-rain-friendly-fire-match"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(width = 1_060f, height = 600f),
                gravityAccelerationY = 1_000f,
            ),
        )
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.BLUE)
        simulation.deployUnit(durableDefender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)
        simulation.aim(EntityId("blue-catapult"), directionDegrees = 20f, power = 50f)
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)

        repeat(80) { simulation.advance(100) }

        assertTrue(
            simulation.snapshot().units.all {
                it.statuses.containsKey("BURNING")
            },
            "the lingering Fire Rain area should burn units from both teams: " +
                "units=${simulation.snapshot().units}, effects=${simulation.snapshot().effects}",
        )
    }

    @Test
    fun fireRainSplitsIntoSevenIndependentProjectilesAtItsApex() {
        val simulation = DeterministicMatchSimulation(
            MatchId("fire-rain-split-match"),
            SimulationConfig(
                balance = GameBalanceFixtures.balance,
                bounds = WorldBounds(
                    width = WorldDimensions.WIDTH,
                    height = WorldDimensions.HEIGHT,
                ),
                gravityAccelerationY = 420f,
            ),
        )
        simulation.start()
        simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)
        val firedProjectile = simulation.snapshot().projectiles.single()
        val events = mutableListOf<MatchEvent>()

        events += simulation.advance(1_000)
        val projectiles = simulation.snapshot().projectiles
        val split = events.filterIsInstance<MatchEvent.ProjectileSplit>().single()

        assertEquals(7, projectiles.size)
        assertEquals(firedProjectile.entityId, split.projectileId)
        assertEquals(6, split.fragmentProjectileIds.size)
        assertEquals(
            split.fragmentProjectileIds.toSet(),
            projectiles.map { it.entityId }.filter { it != firedProjectile.entityId }.toSet(),
        )
        assertTrue(projectiles.all { it.ammunitionType == AmmunitionType.FIRE_RAIN.name })

        val referenceSpeed = abs(firedProjectile.velocityX).coerceAtLeast(120f)
        val fragments = projectiles.filter { it.entityId != firedProjectile.entityId }
        val expectedHorizontalVelocities = listOf(-3, -2, -1, 1, 2, 3)
            .map { firedProjectile.velocityX + it * referenceSpeed * 0.09f }
            .sorted()
        assertEquals(expectedHorizontalVelocities, fragments.map { it.velocityX }.sorted())
        assertTrue(
            projectiles.single { it.entityId == firedProjectile.entityId }.velocityY >
                referenceSpeed * 0.10f,
        )
        assertTrue(fragments.all { it.velocityY > 0f })
    }

    @Test
    fun gameplayTempoPreservesProjectileImpactAndFragmentationGeometry() {
        fun rockImpact(multiplier: Float): MatchEvent.TerrainDeformed {
            val simulation = DeterministicMatchSimulation(
                MatchId("rock-tempo-${multiplier.toString().replace('.', '-')}"),
                SimulationConfig(
                    balance = balanceWithGameplaySpeed(multiplier),
                    bounds = WorldBounds(width = 2_000f, height = 600f),
                    gravityAccelerationY = 1_000f,
                    windAccelerationX = 0.2f,
                ),
            )
            simulation.start()
            simulation.aim(EntityId("blue-catapult"), directionDegrees = 0f, power = 50f)
            simulation.fire(EntityId("blue-catapult"))

            var impact: MatchEvent.TerrainDeformed? = null
            var elapsedMilliseconds = 0
            while (impact == null && elapsedMilliseconds < 10_000) {
                impact = simulation.advance(10L)
                    .filterIsInstance<MatchEvent.TerrainDeformed>()
                    .firstOrNull()
                elapsedMilliseconds += 10
            }
            return requireNotNull(impact) { "projectile did not reach terrain" }
        }

        fun fireRainSplit(multiplier: Float): MatchEvent.ProjectileSplit {
            val simulation = DeterministicMatchSimulation(
                MatchId("fire-rain-tempo-${multiplier.toString().replace('.', '-')}"),
                SimulationConfig(
                    balance = balanceWithGameplaySpeed(multiplier),
                    bounds = WorldBounds(
                        width = WorldDimensions.WIDTH,
                        height = WorldDimensions.HEIGHT,
                    ),
                    gravityAccelerationY = 420f,
                    windAccelerationX = 0.2f,
                ),
            )
            simulation.start()
            simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)

            var split: MatchEvent.ProjectileSplit? = null
            var elapsedMilliseconds = 0
            while (split == null && elapsedMilliseconds < 10_000) {
                split = simulation.advance(10L)
                    .filterIsInstance<MatchEvent.ProjectileSplit>()
                    .firstOrNull()
                elapsedMilliseconds += 10
            }
            return requireNotNull(split) { "Fire Rain did not split at its apex" }
        }

        val normalImpact = rockImpact(1f)
        val fasterImpact = rockImpact(1.2f)
        assertTrue(fasterImpact.simulationTimeMilliseconds < normalImpact.simulationTimeMilliseconds)
        assertTrue(
            abs(
                fasterImpact.simulationTimeMilliseconds -
                    normalImpact.simulationTimeMilliseconds / 1.2f,
            ) <= 20f,
        )
        assertEquals(normalImpact.centerX, fasterImpact.centerX, 2f)
        assertEquals(normalImpact.centerY, fasterImpact.centerY, 2f)

        val normalSplit = fireRainSplit(1f)
        val fasterSplit = fireRainSplit(1.2f)
        assertEquals(6, normalSplit.fragmentProjectileIds.size)
        assertEquals(normalSplit.fragmentProjectileIds.size, fasterSplit.fragmentProjectileIds.size)
        assertTrue(fasterSplit.simulationTimeMilliseconds < normalSplit.simulationTimeMilliseconds)
        assertTrue(
            abs(
                fasterSplit.simulationTimeMilliseconds -
                    normalSplit.simulationTimeMilliseconds / 1.2f,
            ) <= 20f,
        )
        assertEquals(normalSplit.x, fasterSplit.x, 2f)
        assertEquals(normalSplit.y, fasterSplit.y, 2f)
    }

    @Test
    fun bothTeamsStartWithTwentySupplyAndRegenerateUpToTwenty() {
        val simulation = DeterministicMatchSimulation(MatchId("supply-match"), config)
        simulation.start()

        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.RED]?.supply)

        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)

        assertEquals(5, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(2_999)
        assertEquals(5, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(1)
        assertEquals(6, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(42_000)
        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        simulation.advance(3_000)
        assertEquals(20, simulation.snapshot().resourcesByTeam[Team.BLUE]?.supply)
    }

    @Test
    fun deployingAnotherUnitMovesTheExistingUnitToTheNextGarrisonSlot() {
        val simulation = DeterministicMatchSimulation(MatchId("garrison-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.advance(15_000)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)

        val before = simulation.snapshot()
        assertTrue(before.units[0].moving)
        assertTrue(before.units[1].moving)
        assertTrue(before.units.last().moving)
        assertEquals(420f, before.units.last().x)

        simulation.advance(1_000)
        val after = simulation.snapshot()
        assertTrue(after.units[0].x > before.units[0].x)
        assertTrue(after.units[1].x > before.units[1].x)
        assertTrue(after.units.last().x > before.units.last().x)
    }

    @Test
    fun snapshotDistinguishesGarrisonMovementFromBeingSentOnAWave() {
        val simulation = DeterministicMatchSimulation(MatchId("garrison-state-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)

        val repositioning = simulation.snapshot().units.single()
        assertTrue(repositioning.moving)
        assertTrue(repositioning.inGarrison)

        simulation.sendWave(Team.BLUE)

        val sent = simulation.snapshot().units.single()
        assertTrue(sent.moving)
        assertFalse(sent.inGarrison)
    }

    @Test
    fun gameplaySpeedMultiplierScalesWaveAndGarrisonMovement() {
        fun waveDistance(multiplier: Float): Float {
            val simulation = DeterministicMatchSimulation(
                MatchId("wave-tempo-${multiplier.toString().replace('.', '-')}"),
                configWithGameplaySpeed(multiplier),
            )
            simulation.start()
            simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)), Team.BLUE)
            simulation.sendWave(Team.BLUE)
            val startX = simulation.snapshot().units.single().x

            simulation.advance(100L)

            return simulation.snapshot().units.single().x - startX
        }

        fun garrisonDisplacements(multiplier: Float): List<Float> {
            val simulation = DeterministicMatchSimulation(
                MatchId("garrison-tempo-${multiplier.toString().replace('.', '-')}"),
                configWithGameplaySpeed(multiplier),
            )
            simulation.start()
            repeat(3) {
                simulation.deployUnit(
                    freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)),
                    Team.BLUE,
                )
            }
            val before = simulation.snapshot().units.associate { it.entityId to it.x }

            simulation.advance(100L)

            return simulation.snapshot().units.map { unit ->
                unit.x - requireNotNull(before[unit.entityId])
            }
        }

        val baselineWaveDistance = waveDistance(1f)
        val fasterWaveDistance = waveDistance(1.2f)
        assertTrue(baselineWaveDistance > 0f)
        assertEquals(baselineWaveDistance * 1.2f, fasterWaveDistance, 0.02f)

        val baselineGarrisonDisplacements = garrisonDisplacements(1f)
        val fasterGarrisonDisplacements = garrisonDisplacements(1.2f)
        assertTrue(baselineGarrisonDisplacements.any { it != 0f })
        baselineGarrisonDisplacements.zip(fasterGarrisonDisplacements).forEach {
                (baseline, faster) ->
            assertEquals(baseline * 1.2f, faster, 0.02f)
        }
    }

    @Test
    fun deployingAnotherRedUnitMovesExistingRedUnitsIntoMirroredGarrisonSlots() {
        val simulation = DeterministicMatchSimulation(MatchId("red-garrison-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.advance(15_000)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)

        val before = simulation.snapshot()
        assertTrue(before.units.filter { it.team == Team.RED }.all { it.moving })
        assertEquals(config.bounds.width - 420f, before.units.last().x)

        simulation.advance(1_000)
        val after = simulation.snapshot()
        val redBefore = before.units.filter { it.team == Team.RED }.map { it.x }
        val redAfter = after.units.filter { it.team == Team.RED }.map { it.x }
        assertTrue(redAfter.zip(redBefore).all { (afterX, beforeX) -> afterX < beforeX })
    }

    @Test
    fun slingmasterDoesNotMoveBackwardAsRagingBoarClosesIntoAttackRange() {
        val simulation = DeterministicMatchSimulation(
            MatchId("slingmaster-ranged-standoff"),
            config.copy(bounds = WorldBounds(width = 7_200f, height = 4_800f)),
        )
        val ragingBoar = UnitFactory.create(
            GameBalanceFixtures.balance.units.configuration(UnitIds.RAGING_BOAR).copy(
                supplyCost = 0,
                maxHealth = 10_000,
                attackDamage = 1,
            ),
        )
        simulation.start()
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SLINGMASTER)), Team.BLUE)
        simulation.deployUnit(ragingBoar, Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val boarId = simulation.snapshot().units.single { it.unitType == UnitIds.RAGING_BOAR }.entityId
        val attackRange =
            GameBalanceFixtures.balance.units.configuration(UnitIds.SLINGMASTER).attackRange
        var previousSlingmasterX =
            simulation.snapshot().units.single { it.unitType == UnitIds.SLINGMASTER }.x
        var lockedFrames = 0
        var targetWasLocked = false

        for (tick in 0 until 1_000) {
            simulation.advance(33)
            val units = simulation.snapshot().units
            val slingmaster = units.single { it.unitType == UnitIds.SLINGMASTER }
            val boar = units.single { it.entityId == boarId }

            if (slingmaster.targetId == boarId) {
                lockedFrames += 1
                if (targetWasLocked) {
                    assertEquals(previousSlingmasterX, slingmaster.x, 0.001f)
                } else {
                    assertTrue(
                        slingmaster.x >= previousSlingmasterX,
                        "Slingmaster moved backward from $previousSlingmasterX to ${slingmaster.x}",
                    )
                }
                assertTrue(abs(boar.x - slingmaster.x) <= attackRange)
                targetWasLocked = true
                if (lockedFrames >= 30) break
            } else {
                targetWasLocked = false
            }
            previousSlingmasterX = slingmaster.x
        }

        assertTrue(lockedFrames >= 30, "Slingmaster did not remain engaged with the Raging Boar")
    }

    @Test
    fun slingmasterAcquiresTargetAcrossItsFullAttackRange() {
        val simulation = DeterministicMatchSimulation(
            MatchId("slingmaster-full-range"),
            config.copy(bounds = WorldBounds(width = 7_200f, height = 4_800f)),
        )
        val ragingBoar = UnitFactory.create(
            GameBalanceFixtures.balance.units.configuration(UnitIds.RAGING_BOAR).copy(
                supplyCost = 0,
                maxHealth = 10_000,
                attackDamage = 1,
            ),
        )
        simulation.start()
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SLINGMASTER)), Team.BLUE)
        simulation.deployUnit(ragingBoar, Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val boarId = simulation.snapshot().units.single { it.unitType == UnitIds.RAGING_BOAR }.entityId
        val attackRange =
            GameBalanceFixtures.balance.units.configuration(UnitIds.SLINGMASTER).attackRange
        val interactionRange = GameBalanceFixtures.balance.runtime.combat.unitInteractionRange
        var distanceWhenTargeted: Float? = null

        for (tick in 0 until 1_000) {
            simulation.advance(33)
            val units = simulation.snapshot().units
            val slingmaster = units.single { it.unitType == UnitIds.SLINGMASTER }
            val boar = units.single { it.entityId == boarId }
            if (slingmaster.targetId == boarId) {
                distanceWhenTargeted = abs(boar.x - slingmaster.x)
                break
            }
        }

        val distance = requireNotNull(distanceWhenTargeted) {
            "Slingmaster did not acquire the Raging Boar"
        }
        assertTrue(distance <= attackRange)
        assertTrue(
            distance > interactionRange,
            "Slingmaster acquired the target only at the generic interaction range: $distance",
        )
    }

    @Test
    fun deployingDuringAnActiveWaveKeepsTheNewUnitInGarrison() {
        val simulation = DeterministicMatchSimulation(MatchId("wave-garrison-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.sendWave(Team.BLUE)
        simulation.advance(1_000)

        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SAPPER)), Team.BLUE)

        val deployed = simulation.snapshot().units.first { it.unitType == "SAPPER" }
        assertEquals("GARRISONED", deployed.actionState)
        assertEquals(420f, deployed.x)

        simulation.advance(1_000)

        val after = simulation.snapshot().units.first { it.unitType == "SAPPER" }
        assertTrue(after.x > deployed.x)
        assertTrue(after.x < 460f)
    }

    @Test
    fun deployingMultipleUnitsDuringAnActiveWaveShiftsOnlyGarrisonSlots() {
        val simulation = DeterministicMatchSimulation(MatchId("wave-garrison-slots-match"), config)
        simulation.start()
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)), Team.BLUE)
        simulation.sendWave(Team.BLUE)
        simulation.advance(1_000)

        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SAPPER)), Team.BLUE)
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SLINGMASTER)), Team.BLUE)

        val blueUnits = simulation.snapshot().units.filter { it.team == Team.BLUE }
        val activeDefender = blueUnits.first { it.unitType == "DEFENDER" }
        val garrisonedSapper = blueUnits.first { it.unitType == "SAPPER" }
        val garrisonedSlingmaster = blueUnits.first { it.unitType == "SLINGMASTER" }

        assertTrue(activeDefender.x > 440f, "blue unit positions: $blueUnits")
        assertEquals(420f, garrisonedSapper.x, "blue unit positions: $blueUnits")
        assertEquals(420f, garrisonedSlingmaster.x, "blue unit positions: $blueUnits")

        simulation.advance(1_000)

        val after = simulation.snapshot().units.filter { it.team == Team.BLUE }
        val afterSapper = after.first { it.unitType == "SAPPER" }
        val afterSlingmaster = after.first { it.unitType == "SLINGMASTER" }
        assertTrue(afterSapper.x > afterSlingmaster.x)
        assertTrue(afterSlingmaster.x > 420f)
    }

    @Test
    fun garrisonSlotsMoveTowardCenterAndUseSeventyUnitSpacingForBothTeams() {
        Team.entries.forEach { team ->
            val simulation = DeterministicMatchSimulation(
                MatchId("garrison-spacing-${team.name.lowercase()}"),
                config.copy(bounds = WorldBounds(width = 7_200f, height = 4_800f)),
            )
            simulation.start()
            repeat(3) {
                simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)), team)
            }
            simulation.advance(20_000)

            val positions = simulation.snapshot().units
                .filter { it.team == team }
                .map { it.x }
            val expectedPositions = if (team == Team.BLUE) {
                listOf(940f, 870f, 800f)
            } else {
                listOf(6_260f, 6_330f, 6_400f)
            }

            expectedPositions.zip(positions).forEach { (expected, actual) ->
                assertEquals(expected, actual, 0.5f)
            }
        }
    }

    @Test
    fun opposingDefendersStopAndDamageEachOtherAfterTheirWavesMeet() {
        val simulation = DeterministicMatchSimulation(MatchId("unit-combat-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(120) { simulation.advance(100) }

        val units = simulation.snapshot().units
        assertTrue(units.size <= 2)
        assertTrue(units.isEmpty() || units.all { it.health < GameBalanceFixtures.balance.units.configuration(UnitIds.DEFENDER).maxHealth })
    }

    @Test
    fun opposingDefendersKeepVisualSpacingWhileRemainingInMeleeRange() {
        val simulation = DeterministicMatchSimulation(MatchId("unit-combat-spacing-match"), config)
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.BLUE)
        simulation.deployUnit(durableDefender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(25) { simulation.advance(100) }

        val units = simulation.snapshot().units
        assertEquals(2, units.size)
        val blue = units.single { it.team == Team.BLUE }
        val red = units.single { it.team == Team.RED }
        val distance = abs(blue.x - red.x)
        assertTrue(distance in 130f..165f, "opposing Defender distance was $distance: $units")
        assertTrue(units.all { it.targetId != null }, "both Defenders should retain a combat target")
        assertTrue(
            units.any { it.actionState == "ATTACKING" || it.actionState == "RECOVERING" },
            "the wider melee spacing must still allow attacks",
        )
    }

    @Test
    fun combatLuckCanProduceAWinningDefenderInsteadOfAForcedDraw() {
        val simulation = DeterministicMatchSimulation(MatchId("defender-luck-match"), config)
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.BLUE)
        simulation.deployUnit(durableDefender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        var defeatEvents = emptyList<MatchEvent.UnitDefeated>()
        for (step in 0 until 200) {
            defeatEvents = simulation.advance(100)
                .filterIsInstance<MatchEvent.UnitDefeated>()
            if (defeatEvents.isNotEmpty()) break
        }

        assertEquals(1, defeatEvents.size)
        assertEquals(1, simulation.snapshot().units.count { it.health > 0 })
    }

    @Test
    fun multipleUnitsKeepFormationSpacingAndEnterCombatInsteadOfRunningThroughEachOther() {
        val simulation = DeterministicMatchSimulation(MatchId("formation-combat-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(durableDefender(), Team.BLUE)
            simulation.deployUnit(durableDefender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(90) { simulation.advance(100) }

        val units = simulation.snapshot().units
        val bluePositions = units.filter { it.team == Team.BLUE }.map { it.x }.sorted()
        val redPositions = units.filter { it.team == Team.RED }.map { it.x }.sorted()
        assertTrue(
            bluePositions.zipWithNext().all { (left, right) -> right - left >= 20f },
            "blue positions: $bluePositions",
        )
        assertTrue(
            redPositions.zipWithNext().all { (left, right) -> right - left >= 20f },
            "red positions: $redPositions",
        )
        assertTrue(units.any { it.actionState == "ATTACKING" || it.actionState == "RECOVERING" })
    }

    @Test
    fun rearUnitsJoinTheFrontUnitsAttackGroupInsteadOfRemainingIdle() {
        val simulation = DeterministicMatchSimulation(
            MatchId("rear-group-match"),
            config.copy(bounds = WorldBounds(width = 1_900f, height = 600f)),
        )
        simulation.start()
        repeat(3) {
            simulation.deployUnit(durableDefender(), Team.BLUE)
            simulation.deployUnit(durableDefender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(55) { simulation.advance(100) }

        val blueUnits = simulation.snapshot().units.filter { it.team == Team.BLUE }
        assertTrue(blueUnits.size >= 2)
        assertTrue(
            blueUnits.all { it.targetId != null && it.attackGroupId == "melee-${blueUnits.first().targetId?.value}" },
            "blue units did not join one attack group: $blueUnits",
        )
        assertTrue(
            blueUnits.none { !it.moving && it.actionState == "GARRISONED" },
            "rear blue units remained idle: $blueUnits",
        )
        assertTrue(
            blueUnits.zipWithNext().all { (left, right) ->
                kotlin.math.abs(right.x - left.x) >= 16f
            },
            "blue units overlap while joining the group: $blueUnits",
        )
    }

    @Test
    fun rearGroupMembersStartMeleeAttacksFromTheirFormationSlots() {
        val simulation = DeterministicMatchSimulation(MatchId("rear-slot-attack-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(durableDefender(), Team.BLUE)
            simulation.deployUnit(durableDefender(), Team.RED)
        }
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(40) { simulation.advance(100) }

        val units = simulation.snapshot().units
        val groupedAttackers = units.filter {
            it.attackGroupId != null && it.targetId != null
        }
        assertTrue(groupedAttackers.any { it.attackCycleId > 0 })
        assertTrue(
            groupedAttackers.any { attacker ->
                attacker.attackCycleId > 0 &&
                    units.first { target -> target.entityId == attacker.targetId }.let { target ->
                        kotlin.math.abs(target.x - attacker.x) >
                            UnitHitboxProfile.forUnitType(attacker.unitType).halfWidth +
                            UnitHitboxProfile.forUnitType(target.unitType).halfWidth
                    }
            },
            "no rear group member attacked from a formation slot: $groupedAttackers",
        )
    }

    @Test
    fun combatLoggerCapturesUniqueUnitsStateAndTargetDistance() {
        val records = mutableListOf<CombatLogRecord>()
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("combat-log-match"),
            config = config,
            combatLogSink = CombatLogSink { records += it },
        )
        simulation.start()
        simulation.deployUnit(durableDefender(), Team.BLUE)
        simulation.deployUnit(durableDefender(), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        repeat(60) { simulation.advance(100) }

        assertTrue(records.isNotEmpty())
        val combatRecord = records.last { record ->
            record.units.any { it.targetId != null }
        }
        assertEquals(2, combatRecord.units.size)
        assertEquals(
            combatRecord.units.size,
            combatRecord.units.map { it.id }.toSet().size,
        )
        assertTrue(combatRecord.units.all { it.action.isNotBlank() })
        assertTrue(combatRecord.units.any { it.distanceToTarget != null })
    }

    @Test
    fun attackStartsBeforeItsHitIsResolved() {
        val simulation = DeterministicMatchSimulation(MatchId("attack-timing-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        val events = buildList {
            repeat(120) { addAll(simulation.advance(100)) }
        }

        val started = events.filterIsInstance<MatchEvent.AttackStarted>()
        val hits = events.filterIsInstance<MatchEvent.AttackHit>()

        assertTrue(started.isNotEmpty())
        assertTrue(hits.isNotEmpty())
        assertTrue(
            hits.all { hit ->
                started.any { attack ->
                    attack.attackerId == hit.attackerId &&
                        attack.attackCycleId == hit.attackCycleId &&
                        attack.simulationTimeMilliseconds < hit.simulationTimeMilliseconds
                }
            },
        )
    }

    @Test
    fun defeatedUnitsRemainVisibleForTheirDeathAnimationWindow() {
        val simulation = DeterministicMatchSimulation(MatchId("death-window-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.RED)
        simulation.sendWave(Team.BLUE)
        simulation.sendWave(Team.RED)

        var defeatEvents = emptyList<MatchEvent.UnitDefeated>()
        repeat(120) {
            if (defeatEvents.isEmpty()) {
                defeatEvents = simulation.advance(100)
                    .filterIsInstance<MatchEvent.UnitDefeated>()
            }
        }

        assertTrue(defeatEvents.isNotEmpty())
        assertTrue(
            simulation.snapshot().units.any {
                it.entityId == defeatEvents.first().unitId &&
                    it.actionState == "DEAD" &&
                    it.health == 0
            },
        )
    }

    @Test
    fun onlyOneSapperCanBeActivePerTeam() {
        val simulation = DeterministicMatchSimulation(MatchId("sapper-limit-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), Team.BLUE)

        assertFailsWith<IllegalStateException> {
            simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), Team.BLUE)
        }
    }

    @Test
    fun aFasterAlliedUnitCanOvertakeASlowerUnit() {
        val simulation = DeterministicMatchSimulation(
            MatchId("allied-overtake-match"),
            config.copy(bounds = WorldBounds(width = 4_800f, height = 600f)),
        )
        simulation.start()
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)), Team.BLUE)
        simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.SAPPER)), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        repeat(20) { simulation.advance(100) }

        val blueUnits = simulation.snapshot().units.filter { it.team == Team.BLUE }
        assertEquals(2, blueUnits.size)
        val defender = blueUnits.first { it.unitType == "DEFENDER" }
        val sapper = blueUnits.first { it.unitType == "SAPPER" }
        assertTrue(
            sapper.x > defender.x,
            "blue unit positions: ${blueUnits.map { it.x }}",
        )
    }

    @Test
    fun aSapperDamagesTheEnemyFortressAndReturnsWithARefilledBomb() {
        val simulation = DeterministicMatchSimulation(MatchId("sapper-attack-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        val initialFortressHealth = simulation.snapshot().fortresses
            .first { it.team == Team.RED }
            .health
        var hitObserved = false
        var returnedWithBomb = false

        for (attempt in 0 until 1_200) {
            simulation.advance(50)
            val snapshot = simulation.snapshot()
            val redFortress = snapshot.fortresses.first { it.team == Team.RED }
            val sapper = snapshot.units.single { it.unitType == "SAPPER" }
            if (!hitObserved && redFortress.health < initialFortressHealth) {
                hitObserved = true
                assertFalse(sapper.sapperHasBomb)
            }
            if (hitObserved && sapper.sapperHasBomb && sapper.actionState == "GARRISONED") {
                returnedWithBomb = true
                break
            }
        }

        assertTrue(hitObserved, "Sapper should damage the enemy fortress")
        assertTrue(returnedWithBomb, "Sapper should return and refill its bomb")
    }

    @Test
    fun aSapperStopsBeforeTheEnemyFortressForEitherTeam() {
        Team.entries.forEach { team ->
            val simulation = DeterministicMatchSimulation(
                MatchId("sapper-stand-off-${team.name.lowercase()}"),
                config,
            )
            simulation.start()
            simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.SAPPER), team)
            simulation.sendWave(team)
            val enemyTeam = if (team == Team.BLUE) Team.RED else Team.BLUE
            val initialFortress = simulation.snapshot().fortresses.first { it.team == enemyTeam }
            var firstHit: Pair<MatchSnapshot, Float>? = null

            for (attempt in 0 until 600) {
                if (firstHit != null) break
                simulation.advance(50)
                val snapshot = simulation.snapshot()
                val fortress = snapshot.fortresses.first { it.team == enemyTeam }
                if (fortress.health < fortress.maxHealth) {
                    firstHit = snapshot to requireNotNull(initialFortress.x)
                }
            }

            assertTrue(firstHit != null, "$team Sapper should damage the enemy fortress")
            val (snapshot, fortressCenterX) = requireNotNull(firstHit)
            val sapper = snapshot.units.single { it.unitType == "SAPPER" }
            val direction = if (team == Team.BLUE) 1f else -1f
            val frontFaceOffset = if (direction > 0f) {
                FortressHitboxProfile.rectangles.minOf { it.left }
            } else {
                FortressHitboxProfile.rectangles.maxOf { it.right }
            }
            val distanceToFront = (
                fortressCenterX + frontFaceOffset - sapper.x
                ) * direction

            assertTrue(
                kotlin.math.abs(
                    distanceToFront -
                        GameBalanceFixtures.balance.runtime.combat.sapperFortressStandoffDistance,
                ) <= 1f,
                "$team Sapper attacked $distanceToFront units from the fortress hitbox",
            )
        }
    }

    @Test
    fun aMeleeUnitDamagesTheEnemyFortress() {
        val simulation = DeterministicMatchSimulation(MatchId("melee-fortress-attack-match"), config)
        simulation.start()
        simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
        simulation.sendWave(Team.BLUE)

        repeat(120) { simulation.advance(100) }

        val redFortress = simulation.snapshot().fortresses.first { it.team == Team.RED }
        assertTrue(
            redFortress.health < redFortress.maxHealth,
            "fortress=${redFortress.health}/${redFortress.maxHealth}, units=${simulation.snapshot().units}",
        )
    }

    @Test
    fun allMeleeFormationMembersCanAttackTheEnemyFortress() {
        val simulation = DeterministicMatchSimulation(MatchId("melee-formation-fortress-match"), config)
        simulation.start()
        repeat(3) {
            simulation.deployUnit(freeUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER)), Team.BLUE)
        }
        simulation.sendWave(Team.BLUE)

        repeat(60) { simulation.advance(100) }

        val redFortress = simulation.snapshot().fortresses.first { it.team == Team.RED }
        assertTrue(
            redFortress.health <= redFortress.maxHealth - 90,
            "fortress=${redFortress.health}/${redFortress.maxHealth}, units=${simulation.snapshot().units}",
        )
    }

    @Test
    fun combatDamageIsSeededExplicitlyAndReplaysExactly() {
        fun trace(seed: Long): Pair<List<MatchEvent>, com.urkaaaz.contracts.MatchSnapshot> {
            val simulation = DeterministicMatchSimulation(
                MatchId("seeded-combat"),
                config.copy(combatRandomSeed = seed),
            )
            val events = buildList {
                addAll(simulation.start())
                add(simulation.deployUnit(durableDefender(), Team.BLUE))
                add(simulation.deployUnit(durableDefender(), Team.RED))
                simulation.sendWave(Team.BLUE)
                simulation.sendWave(Team.RED)
                repeat(90) { addAll(simulation.advance(100)) }
            }
            return events to simulation.snapshot()
        }

        val first = trace(713L)
        assertEquals(first, trace(713L))
        val differentSeed = trace(714L)
        val firstRolls = first.first.filterIsInstance<MatchEvent.AttackHit>().map { it.rolledDamage }
        val differentRolls = differentSeed.first
            .filterIsInstance<MatchEvent.AttackHit>()
            .map { it.rolledDamage }

        assertTrue(firstRolls.isNotEmpty())
        assertNotEquals(firstRolls, differentRolls)
        first.first.filterIsInstance<MatchEvent.AttackHit>().forEach { hit ->
            assertTrue(
                hit.rolledDamage in
                    (hit.baseDamage * 0.8f).toInt()..(hit.baseDamage * 1.2f).toInt(),
                "rolled damage ${hit.rolledDamage} out of range for base ${hit.baseDamage}",
            )
        }
    }

    @Test
    fun firstAttackTimingVariesBySeedButLaterAttacksKeepTheUnitCooldown() {
        fun attackStartTimes(seed: Long): List<Long> {
            val simulation = DeterministicMatchSimulation(
                MatchId("initiative-match"),
                config.copy(
                    bounds = WorldBounds(width = 1_000f, height = 600f),
                    windAccelerationX = 0f,
                    combatRandomSeed = seed,
                ),
            )
            simulation.start()
            simulation.deployUnit(GameBalanceFixtures.unit(UnitIds.DEFENDER), Team.BLUE)
            simulation.sendWave(Team.BLUE)

            val events = mutableListOf<MatchEvent>()
            var attackCount = 0
            var steps = 0
            while (
                steps < 1_500 &&
                simulation.snapshot().status == MatchStatus.RUNNING &&
                attackCount < 3
            ) {
                val nextEvents = simulation.advance(10)
                events.addAll(nextEvents)
                attackCount += nextEvents
                    .filterIsInstance<MatchEvent.AttackStarted>()
                    .count { it.attackType == "SIEGE_MISSION" }
                steps += 1
            }
            return events.filterIsInstance<MatchEvent.AttackStarted>()
                .filter { it.attackType == "SIEGE_MISSION" }
                .map { it.simulationTimeMilliseconds }
        }

        val seeds = (1L..12L).map(::attackStartTimes)
        val firstAttackTimes = seeds.map { times -> times.first() }

        assertTrue(
            firstAttackTimes.max() - firstAttackTimes.min() <= 410L,
            "initial attack timing exceeded the 400ms maximum: $firstAttackTimes",
        )
        val repeatedAttacks = seeds.first()
        assertTrue(repeatedAttacks.size >= 3, "not enough repeated attacks: $repeatedAttacks")
        assertEquals(
            listOf(810L, 810L),
            repeatedAttacks.take(3).zipWithNext { first, second -> second - first },
        )
    }

    @Test
    fun ammunitionDamageUsesOneSeededRollForTheWholeLingeringEffect() {
        fun damageTrace(seed: Long): List<MatchEvent.DamageApplied> {
            val simulation = DeterministicMatchSimulation(
                MatchId("ammunition-variance-match"),
                SimulationConfig(
                    balance = GameBalanceFixtures.balance,
                    bounds = WorldBounds(width = 1_060f, height = 600f),
                    gravityAccelerationY = 1_000f,
                    combatRandomSeed = seed,
                ),
            )
            simulation.start()
            simulation.deployUnit(durableDefender(), Team.BLUE)
            simulation.deployUnit(durableDefender(), Team.RED)
            simulation.sendWave(Team.BLUE)
            simulation.sendWave(Team.RED)
            simulation.aim(EntityId("blue-catapult"), directionDegrees = 20f, power = 50f)
            simulation.fire(EntityId("blue-catapult"), AmmunitionType.FIRE_RAIN)

            return buildList {
                repeat(100) {
                    addAll(simulation.advance(100))
                }
            }.filterIsInstance<MatchEvent.DamageApplied>()
                .filter { it.targetLayer == "UNIT" && it.sourceProjectileId != null }
        }

        val first = damageTrace(31L)
        assertTrue(first.size >= 2, "expected lingering ammunition damage, got $first")
        val repeatedHits = first.groupBy { it.sourceProjectileId to it.targetId }
            .values
            .firstOrNull { hits ->
                hits.size >= 2 && hits.map { it.amount }.distinct().size == 1
            }
            ?: error(
                "no target received a stable repeated roll: " +
                    first.groupBy { it.sourceProjectileId to it.targetId }
                        .mapValues { (_, hits) -> hits.map { it.amount } },
            )
        assertNotEquals(first.map { it.amount }, damageTrace(32L).map { it.amount })
    }
}
