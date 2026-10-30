package com.urkaaaz.simulation

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossEncounterConfig
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.BossVictoryRule
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.BossAttackBalance
import com.urkaaaz.domain.BossAttackTarget
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.domain.BossCollisionBalance
import com.urkaaaz.domain.BossMovementBalance
import com.urkaaaz.domain.BossMovementKind
import com.urkaaaz.domain.BossSpecialAbility
import com.urkaaaz.domain.Unit
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.WorldPosition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BossSystemTest {
    @Test
    fun centralObjectiveScoresDamageByTeamAndKeepsBlueTieBreak() {
        val boss = system(centralProfile(health = 300))
        assertNull(
            boss.applyDamage(
                amount = 100,
                sourceTeam = Team.RED,
                ammunitionType = "PLAGUE_CAULDRON",
            ),
        )
        boss.applyDamage(150, Team.BLUE)
        val result = boss.applyDamage(150, Team.RED)

        assertNotNull(result)
        assertTrue(result.defeated)
        assertEquals(MatchOutcome.BLUE_WIN, result.winner)
        assertEquals(150, boss.snapshot().blueDamage)
        assertEquals(150, boss.snapshot().redDamage)
        assertEquals(BossPhase.DEFEATED, boss.snapshot().phase)
    }

    @Test
    fun centralObjectiveDefendsAgainstUnitsFromBothTeams() {
        val boss = system(centralProfile(health = 1_000))
        val blue = unit("blue-unit", Team.BLUE, x = 480f)
        val red = unit("red-unit", Team.RED, x = 520f)

        val result = boss.advance(
            deltaSeconds = 0.1f,
            units = listOf(blue, red),
            movingTeams = setOf(Team.BLUE, Team.RED),
            blueGateX = 500f,
            random = Random(5),
        )

        assertEquals(1, result.unitDamage.size)
        assertTrue(result.unitDamage.keys.all { it == blue.id || it == red.id })
        assertTrue(result.unitDamage.values.all { it in 35..150 })
        assertTrue(
            boss.advance(
                deltaSeconds = 3.9f,
                units = listOf(blue, red),
                movingTeams = setOf(Team.BLUE, Team.RED),
                blueGateX = 500f,
                random = Random(5),
            ).unitDamage.isEmpty(),
        )
        assertTrue(
            boss.advance(
                deltaSeconds = 0.1f,
                units = listOf(blue, red),
                movingTeams = setOf(Team.BLUE, Team.RED),
                blueGateX = 500f,
                random = Random(5),
            ).unitDamage.isNotEmpty(),
        )
    }

    @Test
    fun mobileBossEnragesThenAdvancesTowardAndAttacksTheBlueGate() {
        val boss = system(mobileProfile(health = 1_000), spawnRatio = 0.78f)
        val enrage = boss.applyDamage(amount = 700, sourceTeam = Team.BLUE)
        assertNotNull(enrage)
        assertEquals(BossPhase.ENRAGED, boss.snapshot().phase)

        val beforeAdvance = boss.snapshot().x
        boss.advance(
            deltaSeconds = 1f,
            units = emptyList(),
            movingTeams = emptySet(),
            blueGateX = 100f,
            random = Random(5),
        )
        assertEquals(beforeAdvance - 49.14f, boss.snapshot().x, 0.01f)

        val gateBoss = system(mobileProfile(), spawnRatio = 0.2f)
        gateBoss.advance(1f, emptyList(), emptySet(), 100f, Random(5))
        assertEquals(BossPhase.AT_GATE, gateBoss.snapshot().phase)
        val strike = gateBoss.advance(1f, emptyList(), emptySet(), 100f, Random(5))
        assertEquals(45, strike.fortressDamage)
    }

    @Test
    fun mobileBossDefeatIsAttributedToTheLastDamageSource() {
        val boss = system(mobileProfile(health = 10))
        boss.applyDamage(9, Team.BLUE)
        val result = boss.applyDamage(1, Team.RED)

        assertNotNull(result)
        assertEquals(MatchOutcome.RED_WIN, result.winner)
        assertFalse(boss.isAlive)
    }

    @Test
    fun slingmasterDoesNotAcquireBossAsTargetWhenLegacyHasNoBossAttackPath() {
        val boss = system(centralProfile(health = 1_000))
        val slingmaster = Unit(
            id = EntityId("blue-slingmaster"),
            type = UnitFactory.slingmaster(),
            team = Team.BLUE,
            position = WorldPosition(480f, 200f),
            moving = true,
            inGarrison = false,
        )

        assertNull(boss.approachX(slingmaster, setOf(Team.BLUE)))
        assertFalse(boss.canUnitAttack(slingmaster, setOf(Team.BLUE)))
    }

    @Test
    fun defendersKeepTheirSixteenUnitSpacingAndGainBossAttackReachByFormationRank() {
        val boss = system(centralProfile(health = 1_000))
        val defenders = listOf(
            unit("blue-defender-1", Team.BLUE, x = 318f),
            unit("blue-defender-2", Team.BLUE, x = 302f),
            unit("blue-defender-3", Team.BLUE, x = 286f),
            unit("blue-defender-4", Team.BLUE, x = 270f),
        )

        assertEquals(listOf(0, 1, 2, 3), defenders.map {
            boss.formationRank(it, defenders, setOf(Team.BLUE))
        })
        assertTrue(
            defenders.zipWithNext().all { (front, rear) ->
                front.position.x - rear.position.x == 16f
            },
        )
        defenders.forEachIndexed { index, defender ->
            val formationOffset = index * 55
            assertEquals(index == 0, boss.canUnitAttack(defender, setOf(Team.BLUE)))
            assertTrue(
                boss.canUnitAttack(
                    defender,
                    setOf(Team.BLUE),
                    formationOffset.toFloat(),
                ),
            )
        }
    }

    @Test
    fun bossEncounterUsesItsOwnDurationWithTheStandardDurationAsDefault() {
        val profile = centralProfile(health = 300)
        val encounter = BossEncounterConfig(
            profileId = profile.id,
            spawnXRatio = 0.5f,
            victoryRule = BossVictoryRule.CENTRAL_DAMAGE_RACE,
        )
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("boss-duration"),
            config = SimulationConfig(
                bounds = WorldBounds(7_200f, 4_800f),
                matchDurationMilliseconds = 100L,
                bossEncounterMatchDurationMilliseconds = 300L,
                bossEncounter = encounter,
                bossProfile = profile,
            ),
        )
        simulation.start()

        assertEquals(300L, simulation.snapshot().remainingMilliseconds)
        simulation.advance(50L)
        assertEquals(250L, simulation.snapshot().remainingMilliseconds)
    }

    @Test
    fun deployedUnitCanAttackCentralBossThroughSimulation() {
        val profile = centralProfile(health = 100_000).copy(
            collision = BossCollisionBalance(200f, 300f),
            attacks = listOf(
                BossAttackBalance(
                    target = BossAttackTarget.UNIT,
                    damage = 1,
                    minimumDamage = 1,
                    rangeWorldUnits = 0f,
                    cooldownSeconds = 100f,
                    initialCooldownSeconds = 100f,
                ),
            ),
        )
        val simulation = DeterministicMatchSimulation(
            matchId = MatchId("boss-unit-attack"),
            config = SimulationConfig(
                bounds = WorldBounds(7_200f, 4_800f),
                bossEncounter = BossEncounterConfig(
                    profileId = profile.id,
                    spawnXRatio = 0.5f,
                    victoryRule = BossVictoryRule.CENTRAL_DAMAGE_RACE,
                ),
                bossProfile = profile,
            ),
        )
        simulation.start()
        repeat(4) { simulation.deployUnit(UnitFactory.defender(), Team.BLUE) }
        simulation.sendWave(Team.BLUE)

        val events = buildList {
            repeat(150) { addAll(simulation.advance(1_000L)) }
        }

        val attackingDefenders = events
            .filterIsInstance<com.urkaaaz.contracts.MatchEvent.BossDamaged>()
            .filter { it.sourceTeam == Team.BLUE }
            .mapNotNull { it.sourceId }
            .toSet()
        assertEquals(4, attackingDefenders.size)
        assertTrue(simulation.snapshot().boss!!.health < profile.maxHealth)
    }

    private fun system(
        profile: BossBalanceProfile,
        spawnRatio: Float = 0.5f,
    ) = BossSystem(
        profile = profile,
        encounter = BossEncounterConfig(
            profileId = profile.id,
            spawnXRatio = spawnRatio,
            victoryRule = if (profile.archetype == BossArchetype.CENTRAL_OBJECTIVE) {
                BossVictoryRule.CENTRAL_DAMAGE_RACE
            } else {
                BossVictoryRule.DEFEAT_BOSS
            },
        ),
        worldWidth = 1_000f,
        unitCollisionRadius = 24f,
        groundHeightAt = { 200f },
    )

    private fun centralProfile(health: Int) = BossBalanceProfile(
        id = "boss.central-test.level-05",
        presentationKey = "test_boss",
        archetype = BossArchetype.CENTRAL_OBJECTIVE,
        maxHealth = health,
        collision = BossCollisionBalance(200f, 300f),
        movement = BossMovementBalance(BossMovementKind.STATIC, 0f),
        attacks = listOf(
            BossAttackBalance(
                target = BossAttackTarget.UNIT,
                damage = 150,
                minimumDamage = 35,
                rangeWorldUnits = 500f,
                cooldownSeconds = 4f,
                initialCooldownSeconds = 0f,
            ),
        ),
        specialAbilities = emptyList(),
        immuneAmmunitionTypes = setOf("PLAGUE_CAULDRON"),
    )

    private fun mobileProfile(health: Int = 1_000) = BossBalanceProfile(
        id = "boss.mobile-test.level-10",
        presentationKey = "test_boss",
        archetype = BossArchetype.MOBILE_SIEGE_BOSS,
        maxHealth = health,
        collision = BossCollisionBalance(200f, 300f),
        movement = BossMovementBalance(BossMovementKind.ADVANCE, 36.4f),
        attacks = listOf(
            BossAttackBalance(
                target = BossAttackTarget.UNIT,
                damage = 180,
                minimumDamage = 45,
                rangeWorldUnits = 300f,
                cooldownSeconds = 4.5f,
                initialCooldownSeconds = 2.5f,
            ),
            BossAttackBalance(
                target = BossAttackTarget.FORTRESS_GATE,
                damage = 45,
                minimumDamage = 45,
                rangeWorldUnits = 80f,
                cooldownSeconds = 1f,
                initialCooldownSeconds = 1f,
            ),
        ),
        specialAbilities = listOf(
            BossSpecialAbility.Enrage(
                healthFractionThreshold = 0.35f,
                speedMultiplier = 1.35f,
                attackCooldownMultiplier = 0.666667f,
            ),
        ),
    )

    private fun unit(id: String, team: Team, x: Float) = Unit(
        id = EntityId(id),
        type = UnitFactory.defender(),
        team = team,
        position = WorldPosition(x, 200f),
        moving = true,
        inGarrison = false,
    )
}
