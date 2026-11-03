package com.urkaaaz.domain

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.AiProfileId

/** Test-only profiles matching the bundled balance baseline. */
object GameBalanceFixtures {
    val balance = GameBalanceConfig(
        schemaVersion = GameBalanceConfig.CURRENT_SCHEMA_VERSION,
        balanceRevision = "test-fixture",
        bosses = listOf(
            BossBalanceProfile(
                id = "boss.central-test.level-05",
                presentationKey = "central_test",
                archetype = BossArchetype.CENTRAL_OBJECTIVE,
                maxHealth = 1_000,
                collision = BossCollisionBalance(100f, 100f),
                movement = BossMovementBalance(BossMovementKind.STATIC, 0f),
                attacks = listOf(
                    BossAttackBalance(
                        target = BossAttackTarget.UNIT,
                        damage = 10,
                        minimumDamage = 1,
                        rangeWorldUnits = 100f,
                        cooldownSeconds = 1f,
                        initialCooldownSeconds = 1f,
                        windupSeconds = 0f,
                    ),
                ),
                specialAbilities = emptyList(),
            ),
        ),
        units = UnitCatalog(
            listOf(
                UnitConfiguration(UnitIds.SAPPER, 10, 20, 260f, 180, 1.75f, 0f, 180, 0.45f, 0.45f),
                UnitConfiguration(UnitIds.DEFENDER, 5, 50, 150f, 30, 0.8f, 0f, 30, 1f, 1f),
                UnitConfiguration(UnitIds.DEMOLISHER, 10, 25, 110f, 0, 0.8f, 0f, 0, 1f, 1f),
                UnitConfiguration(UnitIds.RAGING_BOAR, 10, 80, 240f, 52, 0.8f, 0f, 52, 1f, 1f),
                UnitConfiguration(UnitIds.SLINGMASTER, 5, 25, 120f, 20, 1.2f, 260f, 20, 1f, 1f),
            ),
        ),
        ammunition = AmmunitionCatalog(
            listOf(
                AmmunitionDefinition(
                    AmmunitionType.ROCK,
                    AmmunitionEffect.DIRECT,
                    ReloadProfile(0f),
                    InventoryProfile(true, 0),
                    null,
                    FlightProfile(1.5f, 1f, 1f, 0f, 8f, 30f, null),
                    CollisionProfile(
                        setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.UNIT, AmmunitionHitLayer.FORTRESS),
                        AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
                        14f,
                        FriendlyFirePolicy.ENEMY_ONLY,
                    ),
                    DamageProfile(50, 50, 28f, 28f, AmmunitionFalloff.LINEAR, 50),
                    TerrainProfile(true, 24f, 14f),
                    AmmunitionImpactBehavior.DirectImpact,
                    setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.UNIT, AmmunitionHitLayer.FORTRESS),
                ),
                AmmunitionDefinition(
                    AmmunitionType.SIEGE_BOMB,
                    AmmunitionEffect.EXPLOSIVE,
                    ReloadProfile(2f),
                    InventoryProfile(true, 0),
                    null,
                    FlightProfile(2.5f, 0.8f, 1f, 0f, 8f, 30f, null),
                    CollisionProfile(
                        setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                        AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
                        57.5f,
                        FriendlyFirePolicy.ENEMY_ONLY,
                    ),
                    DamageProfile(72, 100, 115f, 115f, AmmunitionFalloff.LINEAR, 0),
                    TerrainProfile(true, 82f, 48f),
                    AmmunitionImpactBehavior.Explosion(115f, 0.8f),
                    setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                ),
                AmmunitionDefinition(
                    AmmunitionType.POWDER_BARREL,
                    AmmunitionEffect.EXPLOSIVE,
                    ReloadProfile(6.5f),
                    InventoryProfile(false, 3),
                    250,
                    FlightProfile(2f, 0.9f, 1f, 0f, 8f, 30f, null),
                    CollisionProfile(
                        setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                        AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
                        110f,
                        FriendlyFirePolicy.ALL_UNITS,
                    ),
                    DamageProfile(120, 120, 220f, 220f, AmmunitionFalloff.LINEAR, 0),
                    TerrainProfile(true, 150f, 90f),
                    AmmunitionImpactBehavior.Explosion(220f, 0.8f),
                    setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                ),
                AmmunitionDefinition(
                    AmmunitionType.FIRE_RAIN,
                    AmmunitionEffect.INCENDIARY,
                    ReloadProfile(8f),
                    InventoryProfile(false, 3),
                    350,
                    FlightProfile(
                        1.2f,
                        1.1f,
                        gravityResponse = 1f,
                        drag = 0f,
                        initialSpeedMultiplier = 8f,
                        apexFragmentation = ApexFragmentationProfile(3, 120f, 0.09f, 0.1f, 0.025f),
                        maximumLifetimeSeconds = 30f,
                    ),
                    CollisionProfile(
                        setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                        AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
                        34f,
                        FriendlyFirePolicy.ENEMY_ONLY,
                    ),
                    DamageProfile(4, 4, 68f, 68f, AmmunitionFalloff.LINEAR, 0),
                    TerrainProfile(true, 36f, 10f),
                    AmmunitionImpactBehavior.LingeringArea(
                        68f, 9f, 0.5f, 4, 4, false, status = UnitStatus.BURNING,
                        windDriftMultiplier = 1f,
                        followsTerrain = false,
                        unitFriendlyFire = FriendlyFirePolicy.ALL_UNITS,
                    ),
                    setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                ),
                AmmunitionDefinition(
                    AmmunitionType.PLAGUE_CAULDRON,
                    AmmunitionEffect.PLAGUE,
                    ReloadProfile(15f),
                    InventoryProfile(false, 3),
                    500,
                    FlightProfile(3f, 0.6f, 1f, 0f, 8f, 30f, null),
                    CollisionProfile(
                        setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                        AmmunitionCollisionMode.IMPACT_ON_TERRAIN,
                        130f,
                        FriendlyFirePolicy.ENEMY_ONLY,
                    ),
                    DamageProfile(8, 0, 300f, 300f, AmmunitionFalloff.LINEAR, 0),
                    TerrainProfile(false, 0f, 0f),
                    AmmunitionImpactBehavior.LingeringArea(
                        300f, 6f, 1f, 8, 0, true, 0.75f, true,
                        UnitStatus.POISONED, FriendlyFirePolicy.ENEMY_ONLY,
                    ),
                    setOf(AmmunitionHitLayer.TERRAIN, AmmunitionHitLayer.FORTRESS),
                ),
            ),
        ),
        spells = SpellCatalog(
            listOf(
                SpellDefinition(SpellType.ICE_TRAP, 500, SpellCategory.BATTLEFIELD_CONTROL, SpellTargetCategory.ENEMY_FORCE, false, 50, null, false),
                SpellDefinition(SpellType.RUNIC_BASTION, 1_200, SpellCategory.DEFENSIVE, SpellTargetCategory.OWN_GATE, false, 70, SpellEffectDefinition.RunicBastion(360), false),
                SpellDefinition(SpellType.HASTE, 900, SpellCategory.SUPPORT, SpellTargetCategory.ALLIED_FORCE, false, 50, SpellEffectDefinition.Haste(15_000L, 2f), true),
                SpellDefinition(SpellType.HEAL, 900, SpellCategory.SUPPORT, SpellTargetCategory.ALLIED_FORCE, false, 50, null, false),
                SpellDefinition(SpellType.FORTRESS_SHIELD, 0, SpellCategory.DEFENSIVE, SpellTargetCategory.OWN_FORTRESS, false, 80, SpellEffectDefinition.FortressShield(150), false),
                SpellDefinition(SpellType.CATAPULT_LOCK, 1_000, SpellCategory.SIEGE, SpellTargetCategory.ENEMY_SIEGE_ENGINE, true, 60, null, false),
                SpellDefinition(SpellType.SLOWING_FIELD, 1_000, SpellCategory.BATTLEFIELD_CONTROL, SpellTargetCategory.ENEMY_FORCE, true, 50, null, false),
                SpellDefinition(SpellType.VAMPIRIC_BANNER, 1_100, SpellCategory.SUPPORT, SpellTargetCategory.ALLIED_FORCE, true, 60, null, false),
                SpellDefinition(SpellType.STONE_SKIN, 1_100, SpellCategory.DEFENSIVE, SpellTargetCategory.ALLIED_FORCE, true, 60, null, false),
                SpellDefinition(SpellType.SIEGE_FRENZY, 1_200, SpellCategory.OFFENSIVE, SpellTargetCategory.ALLIED_FORCE, true, 60, null, false),
                SpellDefinition(SpellType.BERSERKER, 2_000, SpellCategory.OFFENSIVE, SpellTargetCategory.ALLIED_UNIT, true, 100, null, false),
            ),
        ),
        mana = ManaConfiguration(100, 100, 1, 800L),
        runtime = RuntimeBalance(
            economy = EconomyBalance(100, 20, 20, 3f),
            fortresses = FortressBalance(500, 5_000),
            catapult = CatapultBalance(45f, 50f),
            match = MatchBalance(300_000L, 300_000L),
            physics = PhysicsBalance(420f, 390f),
            wind = WindBalance(8f, 3f, 18f, 24f, 0.35f, 0.9f),
            combat = CombatBalance(
                minimumDamageVarianceFactor = 0.8f,
                maximumDamageVarianceFactor = 1.2f,
                unitLuckBiasMaximumMagnitude = 0.04f,
                maximumInitialAttackDelayRatio = 0.5f,
                gameplaySpeedMultiplier = 1.2f,
                unitMovementSpeedScale = 0.9f,
                garrisonMovementSpeedScale = 0.44f,
                unitInteractionRange = 260f,
                fortressAttackRange = 82f,
                sapperFortressStandoffDistance = 200f,
                sapperContactSpeedMultiplier = 0.45f,
                meleeAttackStandoffDistance = 150f,
                attackFormationSpacing = 55f,
                meleeSynergyDamagePerAdditionalUnit = 0.35f,
                maximumMeleeSynergyMultiplier = 2f,
                demolisher = DemolisherBalance(300, 260f, 0.5f, 90f),
            ),
            bossCombat = BossCombatBalance(0.72f, 0.7f),
            aiProfiles = listOf(
                AiProfileBalance(AiProfileId.CADET, 28f, 45f, 1_100L, 8_000L, 0f, 0f),
                AiProfileBalance(AiProfileId.VETERAN, 3f, 6f, 700L, 0L, 0f, 0f),
                AiProfileBalance(AiProfileId.ELITE, 1.25f, 2.5f, 400L, 0L, 0.5f, 0.5f),
            ),
            aiStrategy = AiStrategyBalance(
                aimAngleOffsetDegrees = 42f,
                horizontalDistancePerPowerPercent = 12f,
                ammunitionScoring = AiAmmunitionScoringBalance(
                    situationalFortressDamageWeight = 1.25f,
                    situationalUnitDamageWeight = 1f,
                    situationalFortressRadiusWeight = 0.1f,
                    situationalUnitRadiusWeight = 0.1f,
                    counterplayUnitDamageWeight = 1.5f,
                    counterplayUnitRadiusWeight = 1f,
                    counterplayFortressDamageWeight = 0.5f,
                    counterplayFortressRadiusWeight = 0.1f,
                ),
                unitScoring = AiUnitScoringBalance(
                    sapperPriorityScore = 1_000f,
                    demolisherPriorityScore = 900f,
                    attackDamageWeight = 2f,
                    speedWeight = 1f,
                    defenderVsSapperRoleBonus = 1_000f,
                    rangedVsRangedRoleBonus = 100f,
                    rangedVsMeleeRoleBonus = 50f,
                    supplyCostPenaltyWeight = 0.25f,
                ),
                waveDispatch = AiWaveDispatchBalance(
                    situationalMinimumReadyUnits = 3,
                    counterplayMinimumReadyUnits = 3,
                    counterplayMinimumReadyUnitsAgainstMovingEnemies = 2,
                    lowFortressHealthFraction = 0.35f,
                ),
            ),
            rewards = RewardBalance(100L, 10L, 25L, 120_000L, 25L, 10L, 2),
            campaign = CampaignBalance(
                initialWindStrength = 0.25f,
                initialWindStrengthStep = 0.15f,
                initialWindStrengthBands = 4,
                demoInitialWindStrength = 0.25f,
                aiDifficultyBands = listOf(
                    CampaignAiDifficultyBand(1, 10, AiProfileId.CADET, 0, 0),
                    CampaignAiDifficultyBand(11, 30, AiProfileId.VETERAN, 0, 0),
                    CampaignAiDifficultyBand(31, 100, AiProfileId.ELITE, 0, 100),
                ),
            ),
        ),
    )

    fun unit(id: String): UnitDefinition = UnitFactory.byId(id, balance.units)

    fun withMana(
        mana: ManaConfiguration,
        source: GameBalanceConfig = balance,
    ): GameBalanceConfig = source.copy(mana = mana)

    fun withStartingLimitedAmmunition(
        count: Int,
        source: GameBalanceConfig = balance,
    ): GameBalanceConfig = source.copy(
        ammunition = AmmunitionCatalog(
            source.ammunition.all.map { definition ->
                if (definition.unlimited) definition
                else definition.copy(
                    inventory = definition.inventory.copy(startingCount = count),
                )
            },
        ),
    )
}
