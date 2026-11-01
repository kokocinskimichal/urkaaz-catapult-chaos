package com.urkaaaz.application.balance

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import com.urkaaaz.domain.AmmunitionCatalog
import com.urkaaaz.domain.AmmunitionCollisionMode
import com.urkaaaz.domain.AmmunitionDefinition
import com.urkaaaz.domain.AmmunitionEffect
import com.urkaaaz.domain.AmmunitionFalloff
import com.urkaaaz.domain.AmmunitionHitLayer
import com.urkaaaz.domain.AmmunitionImpactBehavior
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.ApexFragmentationProfile
import com.urkaaaz.domain.AiProfileBalance
import com.urkaaaz.domain.BossAttackBalance
import com.urkaaaz.domain.BossAttackTarget
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.domain.BossCollisionBalance
import com.urkaaaz.domain.CollisionProfile
import com.urkaaaz.domain.DamageProfile
import com.urkaaaz.domain.FlightProfile
import com.urkaaaz.domain.FriendlyFirePolicy
import com.urkaaaz.domain.BossMovementBalance
import com.urkaaaz.domain.BossMovementKind
import com.urkaaaz.domain.BossSpecialAbility
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.RuntimeBalance
import com.urkaaaz.domain.EconomyBalance
import com.urkaaaz.domain.FortressBalance
import com.urkaaaz.domain.CatapultBalance
import com.urkaaaz.domain.MatchBalance
import com.urkaaaz.domain.PhysicsBalance
import com.urkaaaz.domain.WindBalance
import com.urkaaaz.domain.CombatBalance
import com.urkaaaz.domain.AiStrategyBalance
import com.urkaaaz.domain.AiAmmunitionScoringBalance
import com.urkaaaz.domain.AiUnitScoringBalance
import com.urkaaaz.domain.AiWaveDispatchBalance
import com.urkaaaz.domain.DemolisherBalance
import com.urkaaaz.domain.BossCombatBalance
import com.urkaaaz.domain.RewardBalance
import com.urkaaaz.domain.CampaignBalance
import com.urkaaaz.domain.CampaignAiDifficultyBand
import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.domain.InventoryProfile
import com.urkaaaz.domain.ManaConfiguration
import com.urkaaaz.domain.ReloadProfile
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.SpellCategory
import com.urkaaaz.domain.SpellDefinition
import com.urkaaaz.domain.SpellEffectDefinition
import com.urkaaaz.domain.SpellTargetCategory
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.TerrainProfile
import com.urkaaaz.domain.UnitCatalog
import com.urkaaaz.domain.UnitConfiguration
import com.urkaaaz.domain.UnitStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

data class LoadedGameBalance(
    val config: GameBalanceConfig,
    val contentHash: String,
)

sealed interface GameBalanceRead {
    data class Available(val balance: LoadedGameBalance) : GameBalanceRead
    data class Failure(val reason: GameBalanceFailure) : GameBalanceRead
}

enum class GameBalanceFailure {
    MISSING_RESOURCE,
    INVALID_DATA,
    STORAGE_UNAVAILABLE,
}

class GameBalanceRepository(
    private val cacheResult: Boolean = false,
    private val openResource: (String) -> java.io.InputStream?,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    @Volatile
    private var cachedRead: GameBalanceRead? = null

    @Synchronized
    fun load(): GameBalanceRead {
        if (!cacheResult) return readBalance()
        cachedRead?.let { return it }
        return readBalance().also { cachedRead = it }
    }

    fun requireConfig(): GameBalanceConfig = when (val result = load()) {
        is GameBalanceRead.Available -> result.balance.config
        is GameBalanceRead.Failure -> error("Unable to load game balance: ${result.reason}")
    }

    private fun readBalance(): GameBalanceRead = try {
        val input = openResource(GAME_BALANCE_RESOURCE)
            ?: return GameBalanceRead.Failure(GameBalanceFailure.MISSING_RESOURCE)
        val content = input.bufferedReader().use { it.readText() }
        val dto = json.decodeFromString<GameBalanceDocumentDto>(content)
        val balance = dto.toDomain()
        CampaignMilestoneRegistry.default().validate(balance.spells)
        GameBalanceRead.Available(
            LoadedGameBalance(
                config = balance,
                contentHash = sha256(content),
            ),
        )
    } catch (_: SerializationException) {
        GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA)
    } catch (_: IllegalArgumentException) {
        GameBalanceRead.Failure(GameBalanceFailure.INVALID_DATA)
    } catch (_: IOException) {
        GameBalanceRead.Failure(GameBalanceFailure.STORAGE_UNAVAILABLE)
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val GAME_BALANCE_RESOURCE = "balance/game-balance.json"

        val bundled = GameBalanceRepository(
            openResource = { path ->
                GameBalanceRepository::class.java.classLoader.getResourceAsStream(path)
            },
            cacheResult = true,
        )
    }
}

@Serializable
private data class GameBalanceDocumentDto(
    val schemaVersion: Int,
    val balanceRevision: String,
    val bosses: List<BossProfileDto>,
    val units: List<UnitConfigurationDto>,
    val ammunition: List<AmmunitionDefinitionDto>,
    val spells: List<SpellDefinitionDto>,
    val mana: ManaConfigurationDto,
    val runtime: RuntimeBalanceDto,
) {
    fun toDomain() = GameBalanceConfig(
        schemaVersion = schemaVersion,
        balanceRevision = balanceRevision,
        bosses = bosses.map(BossProfileDto::toDomain),
        units = UnitCatalog(units.map(UnitConfigurationDto::toDomain)),
        ammunition = AmmunitionCatalog(ammunition.map(AmmunitionDefinitionDto::toDomain)),
        spells = SpellCatalog(spells.map(SpellDefinitionDto::toDomain)),
        mana = mana.toDomain(),
        runtime = runtime.toDomain(),
    )
}

@Serializable
private data class BossProfileDto(
    val id: String,
    val presentationKey: String,
    val archetype: String,
    val maxHealth: Int,
    val collision: BossCollisionDto,
    val movement: BossMovementDto,
    val attacks: List<BossAttackDto>,
    val specialAbilities: List<BossAbilityDto>,
    val immuneAmmunitionTypes: List<String>,
) {
    fun toDomain() = BossBalanceProfile(
        id = id,
        presentationKey = presentationKey,
        archetype = BossArchetype.valueOf(archetype),
        maxHealth = maxHealth,
        collision = collision.toDomain(),
        movement = movement.toDomain(),
        attacks = attacks.map(BossAttackDto::toDomain),
        specialAbilities = specialAbilities.map(BossAbilityDto::toDomain),
        immuneAmmunitionTypes = immuneAmmunitionTypes.toSet().also {
            require(it.size == immuneAmmunitionTypes.size) {
                "boss ammunition immunities must be unique"
            }
        },
    )
}

@Serializable
private data class BossCollisionDto(
    val width: Float,
    val height: Float,
) {
    fun toDomain() = BossCollisionBalance(width, height)
}

@Serializable
private data class BossMovementDto(
    val kind: String,
    val speedWorldUnitsPerSecond: Float,
) {
    fun toDomain() = BossMovementBalance(
        kind = BossMovementKind.valueOf(kind),
        speedWorldUnitsPerSecond = speedWorldUnitsPerSecond,
    )
}

@Serializable
private data class BossAttackDto(
    val target: String,
    val damage: Int,
    val minimumDamage: Int,
    val rangeWorldUnits: Float,
    val cooldownSeconds: Float,
    val initialCooldownSeconds: Float,
    val windupSeconds: Float,
) {
    fun toDomain() = BossAttackBalance(
        target = BossAttackTarget.valueOf(target),
        damage = damage,
        minimumDamage = minimumDamage,
        rangeWorldUnits = rangeWorldUnits,
        cooldownSeconds = cooldownSeconds,
        initialCooldownSeconds = initialCooldownSeconds,
        windupSeconds = windupSeconds,
    )
}

@Serializable
private data class BossAbilityDto(
    val kind: String,
    val healthFractionThreshold: Float? = null,
    val speedMultiplier: Float? = null,
    val attackCooldownMultiplier: Float? = null,
) {
    fun toDomain(): BossSpecialAbility = when (kind) {
        "ENRAGE" -> BossSpecialAbility.Enrage(
            healthFractionThreshold = requireNotNull(healthFractionThreshold),
            speedMultiplier = requireNotNull(speedMultiplier),
            attackCooldownMultiplier = requireNotNull(attackCooldownMultiplier),
        )
        else -> throw IllegalArgumentException("unsupported boss ability kind: $kind")
    }
}

@Serializable
private data class UnitConfigurationDto(
    val id: String,
    val supplyCost: Int,
    val maxHealth: Int,
    val speed: Float,
    val attackDamage: Int,
    val attackCooldownSeconds: Float,
    val attackRange: Float,
    val fortressDamage: Int,
    val incomingDamageMultiplier: Float,
    val contactMovementSpeedMultiplier: Float,
) {
    fun toDomain() = UnitConfiguration(
        id = id,
        supplyCost = supplyCost,
        maxHealth = maxHealth,
        speed = speed,
        attackDamage = attackDamage,
        attackCooldownSeconds = attackCooldownSeconds,
        attackRange = attackRange,
        fortressDamage = fortressDamage,
        incomingDamageMultiplier = incomingDamageMultiplier,
        contactMovementSpeedMultiplier = contactMovementSpeedMultiplier,
    )
}

@Serializable
private data class AmmunitionDefinitionDto(
    val type: String,
    val effect: String,
    val reload: ReloadDto,
    val inventory: InventoryDto,
    val flight: FlightDto,
    val collision: CollisionDto,
    val damage: DamageDto,
    val terrain: TerrainDto,
    val behavior: AmmunitionBehaviorDto,
    val availableLayers: List<String>,
) {
    fun toDomain() = AmmunitionDefinition(
        type = AmmunitionType.valueOf(type),
        effect = AmmunitionEffect.valueOf(effect),
        reload = reload.toDomain(),
        inventory = inventory.toDomain(),
        flight = flight.toDomain(),
        collision = collision.toDomain(),
        damage = damage.toDomain(),
        terrain = terrain.toDomain(),
        behavior = behavior.toDomain(),
        availableLayers = uniqueEnums(availableLayers, "available ammunition layers")
            .map(AmmunitionHitLayer::valueOf).toSet(),
    )
}

@Serializable
private data class ReloadDto(val reloadSeconds: Float) {
    fun toDomain() = ReloadProfile(reloadSeconds)
}

@Serializable
private data class InventoryDto(
    val unlimited: Boolean,
    val startingCount: Int,
) {
    fun toDomain() = InventoryProfile(unlimited, startingCount)
}

@Serializable
private data class FlightDto(
    val mass: Float,
    val windResponse: Float,
    val gravityResponse: Float,
    val drag: Float,
    val initialSpeedMultiplier: Float,
    val maximumLifetimeSeconds: Float,
    val apexFragmentation: ApexFragmentationDto?,
) {
    fun toDomain() = FlightProfile(
        mass = mass,
        windResponse = windResponse,
        gravityResponse = gravityResponse,
        drag = drag,
        initialSpeedMultiplier = initialSpeedMultiplier,
        maximumLifetimeSeconds = maximumLifetimeSeconds,
        apexFragmentation = apexFragmentation?.toDomain(),
    )
}

@Serializable
private data class ApexFragmentationDto(
    val fragmentsPerSide: Int,
    val minimumReferenceSpeed: Float,
    val horizontalSpreadPerStep: Float,
    val initialDownwardSpeed: Float,
    val downwardSpreadPerStep: Float,
) {
    fun toDomain() = ApexFragmentationProfile(
        fragmentsPerSide = fragmentsPerSide,
        minimumReferenceSpeed = minimumReferenceSpeed,
        horizontalSpreadPerStep = horizontalSpreadPerStep,
        initialDownwardSpeed = initialDownwardSpeed,
        downwardSpreadPerStep = downwardSpreadPerStep,
    )
}

@Serializable
private data class CollisionDto(
    val hitLayers: List<String>,
    val mode: String,
    val hitRadius: Float,
    val friendlyFire: String,
) {
    fun toDomain() = CollisionProfile(
        hitLayers = uniqueEnums(hitLayers, "collision hit layers")
            .map(AmmunitionHitLayer::valueOf).toSet(),
        mode = AmmunitionCollisionMode.valueOf(mode),
        hitRadius = hitRadius,
        friendlyFire = FriendlyFirePolicy.valueOf(friendlyFire),
    )
}

@Serializable
private data class DamageDto(
    val unitDamage: Int,
    val fortressDamage: Int,
    val unitRadius: Float,
    val fortressRadius: Float,
    val falloff: String,
    val minimumDamage: Int,
) {
    fun toDomain() = DamageProfile(
        unitDamage = unitDamage,
        fortressDamage = fortressDamage,
        unitRadius = unitRadius,
        fortressRadius = fortressRadius,
        falloff = AmmunitionFalloff.valueOf(falloff),
        minimumDamage = minimumDamage,
    )
}

@Serializable
private data class TerrainDto(
    val deformsTerrain: Boolean,
    val craterRadius: Float,
    val craterDepth: Float,
) {
    fun toDomain() = TerrainProfile(deformsTerrain, craterRadius, craterDepth)
}

@Serializable
private data class AmmunitionBehaviorDto(
    val kind: String,
    val radius: Float? = null,
    val lifetimeSeconds: Float? = null,
    val durationSeconds: Float? = null,
    val tickIntervalSeconds: Float? = null,
    val damagePerTick: Int? = null,
    val fortressDamagePerTick: Int? = null,
    val driftsWithWind: Boolean? = null,
    val windDriftMultiplier: Float? = null,
    val followsTerrain: Boolean? = null,
    val status: String? = null,
    val unitFriendlyFire: String? = null,
) {
    fun toDomain(): AmmunitionImpactBehavior = when (kind) {
        "DIRECT_IMPACT" -> AmmunitionImpactBehavior.DirectImpact.also {
            require(
                listOf(
                    radius, lifetimeSeconds, durationSeconds, tickIntervalSeconds,
                    damagePerTick, fortressDamagePerTick, driftsWithWind, windDriftMultiplier,
                    followsTerrain, status, unitFriendlyFire,
                ).all { value -> value == null },
            ) { "direct impact behavior must not include variant-specific values" }
        }
        "EXPLOSION" -> AmmunitionImpactBehavior.Explosion(
            radius = requireNotNull(radius),
            lifetimeSeconds = requireNotNull(lifetimeSeconds),
        ).also {
            require(
                listOf(
                    durationSeconds, tickIntervalSeconds, damagePerTick, fortressDamagePerTick,
                    driftsWithWind, windDriftMultiplier, followsTerrain, status, unitFriendlyFire,
                ).all { value -> value == null },
            ) { "explosion behavior contains unsupported values" }
        }
        "LINGERING_AREA" -> AmmunitionImpactBehavior.LingeringArea(
            radius = requireNotNull(radius),
            durationSeconds = requireNotNull(durationSeconds),
            tickIntervalSeconds = requireNotNull(tickIntervalSeconds),
            damagePerTick = requireNotNull(damagePerTick),
            fortressDamagePerTick = requireNotNull(fortressDamagePerTick),
            driftsWithWind = requireNotNull(driftsWithWind),
            windDriftMultiplier = requireNotNull(windDriftMultiplier),
            followsTerrain = requireNotNull(followsTerrain),
            status = status?.let(UnitStatus::valueOf),
            unitFriendlyFire = FriendlyFirePolicy.valueOf(requireNotNull(unitFriendlyFire)),
        ).also {
            require(lifetimeSeconds == null) { "lingering behavior does not use lifetimeSeconds" }
        }
        else -> throw IllegalArgumentException("unsupported ammunition behavior kind: $kind")
    }
}

@Serializable
private data class SpellDefinitionDto(
    val type: String,
    val goldCost: Int,
    val category: String,
    val targetCategory: String,
    val special: Boolean,
    val manaCost: Int,
    val effect: SpellEffectDto?,
    val recastableWhileActive: Boolean,
) {
    fun toDomain() = SpellDefinition(
        type = SpellType.valueOf(type),
        goldCost = goldCost,
        category = SpellCategory.valueOf(category),
        targetCategory = SpellTargetCategory.valueOf(targetCategory),
        special = special,
        manaCost = manaCost,
        effect = effect?.toDomain(),
        recastableWhileActive = recastableWhileActive,
    )
}

@Serializable
private data class SpellEffectDto(
    val kind: String,
    val durationMilliseconds: Long? = null,
    val movementSpeedMultiplier: Float? = null,
    val hitPoints: Int? = null,
) {
    fun toDomain(): SpellEffectDefinition = when (kind) {
        "HASTE" -> SpellEffectDefinition.Haste(
            durationMilliseconds = requireNotNull(durationMilliseconds),
            movementSpeedMultiplier = requireNotNull(movementSpeedMultiplier),
        ).also { require(hitPoints == null) { "haste effect does not use hitPoints" } }
        "FORTRESS_SHIELD" -> SpellEffectDefinition.FortressShield(
            hitPoints = requireNotNull(hitPoints),
        ).also {
            require(durationMilliseconds == null && movementSpeedMultiplier == null) {
                "fortress shield effect contains unsupported values"
            }
        }
        "RUNIC_BASTION" -> SpellEffectDefinition.RunicBastion(
            hitPoints = requireNotNull(hitPoints),
        ).also {
            require(durationMilliseconds == null && movementSpeedMultiplier == null) {
                "runic bastion effect contains unsupported values"
            }
        }
        else -> throw IllegalArgumentException("unsupported spell effect kind: $kind")
    }
}

@Serializable
private data class ManaConfigurationDto(
    val startingMana: Int,
    val maximumMana: Int,
    val regenerationAmount: Int,
    val regenerationIntervalMilliseconds: Long,
) {
    fun toDomain() = ManaConfiguration(
        startingMana = startingMana,
        maximumMana = maximumMana,
        regenerationAmount = regenerationAmount,
        regenerationIntervalMilliseconds = regenerationIntervalMilliseconds,
    )
}

@Serializable
private data class RuntimeBalanceDto(
    val economy: EconomyBalanceDto,
    val fortresses: FortressBalanceDto,
    val catapult: CatapultBalanceDto,
    val match: MatchBalanceDto,
    val physics: PhysicsBalanceDto,
    val wind: WindBalanceDto,
    val combat: CombatBalanceDto,
    val bossCombat: BossCombatBalanceDto,
    val aiProfiles: List<AiProfileBalanceDto>,
    val aiStrategy: AiStrategyBalanceDto,
    val rewards: RewardBalanceDto,
    val campaign: CampaignBalanceDto,
) {
    fun toDomain() = RuntimeBalance(
        economy = economy.toDomain(),
        fortresses = fortresses.toDomain(),
        catapult = catapult.toDomain(),
        match = match.toDomain(),
        physics = physics.toDomain(),
        wind = wind.toDomain(),
        combat = combat.toDomain(),
        bossCombat = bossCombat.toDomain(),
        aiProfiles = aiProfiles.map(AiProfileBalanceDto::toDomain),
        aiStrategy = aiStrategy.toDomain(),
        rewards = rewards.toDomain(),
        campaign = campaign.toDomain(),
    )
}

@Serializable
private data class EconomyBalanceDto(
    val startingGold: Int,
    val startingSupply: Int,
    val maximumSupply: Int,
    val supplyRegenerationIntervalSeconds: Float,
) {
    fun toDomain() = EconomyBalance(
        startingGold = startingGold,
        startingSupply = startingSupply,
        maximumSupply = maximumSupply,
        supplyRegenerationIntervalSeconds = supplyRegenerationIntervalSeconds,
    )
}

@Serializable
private data class FortressBalanceDto(
    val standardMaximumHealth: Int,
    val demoBlueMaximumHealth: Int,
) {
    fun toDomain() = FortressBalance(standardMaximumHealth, demoBlueMaximumHealth)
}

@Serializable
private data class CatapultBalanceDto(
    val initialAimDirectionDegrees: Float,
    val initialAimPower: Float,
) {
    fun toDomain() = CatapultBalance(initialAimDirectionDegrees, initialAimPower)
}

@Serializable
private data class MatchBalanceDto(
    val durationMilliseconds: Long,
    val bossEncounterDurationMilliseconds: Long,
) {
    fun toDomain() = MatchBalance(durationMilliseconds, bossEncounterDurationMilliseconds)
}

@Serializable
private data class PhysicsBalanceDto(
    val gravityAccelerationY: Float,
    val windAccelerationScale: Float,
) {
    fun toDomain() = PhysicsBalance(gravityAccelerationY, windAccelerationScale)
}

@Serializable
private data class WindBalanceDto(
    val initialDelaySeconds: Float,
    val transitionSeconds: Float,
    val minimumChangeIntervalSeconds: Float,
    val maximumChangeIntervalSeconds: Float,
    val minimumStrength: Float,
    val maximumStrength: Float,
) {
    fun toDomain() = WindBalance(
        initialDelaySeconds = initialDelaySeconds,
        transitionSeconds = transitionSeconds,
        minimumChangeIntervalSeconds = minimumChangeIntervalSeconds,
        maximumChangeIntervalSeconds = maximumChangeIntervalSeconds,
        minimumStrength = minimumStrength,
        maximumStrength = maximumStrength,
    )
}

@Serializable
private data class CombatBalanceDto(
    val minimumDamageVarianceFactor: Float,
    val maximumDamageVarianceFactor: Float,
    val unitLuckBiasMaximumMagnitude: Float,
    val maximumInitialAttackDelayRatio: Float,
    val unitMovementSpeedMultiplier: Float,
    val unitMovementSpeedScale: Float,
    val garrisonMovementSpeedScale: Float,
    val unitInteractionRange: Float,
    val fortressAttackRange: Float,
    val sapperFortressStandoffDistance: Float,
    val sapperContactSpeedMultiplier: Float,
    val meleeAttackStandoffDistance: Float,
    val attackFormationSpacing: Float,
    val meleeSynergyDamagePerAdditionalUnit: Float,
    val maximumMeleeSynergyMultiplier: Float,
    val demolisher: DemolisherBalanceDto,
) {
    fun toDomain() = CombatBalance(
        minimumDamageVarianceFactor = minimumDamageVarianceFactor,
        maximumDamageVarianceFactor = maximumDamageVarianceFactor,
        unitLuckBiasMaximumMagnitude = unitLuckBiasMaximumMagnitude,
        maximumInitialAttackDelayRatio = maximumInitialAttackDelayRatio,
        unitMovementSpeedMultiplier = unitMovementSpeedMultiplier,
        unitMovementSpeedScale = unitMovementSpeedScale,
        garrisonMovementSpeedScale = garrisonMovementSpeedScale,
        unitInteractionRange = unitInteractionRange,
        fortressAttackRange = fortressAttackRange,
        sapperFortressStandoffDistance = sapperFortressStandoffDistance,
        sapperContactSpeedMultiplier = sapperContactSpeedMultiplier,
        meleeAttackStandoffDistance = meleeAttackStandoffDistance,
        attackFormationSpacing = attackFormationSpacing,
        meleeSynergyDamagePerAdditionalUnit = meleeSynergyDamagePerAdditionalUnit,
        maximumMeleeSynergyMultiplier = maximumMeleeSynergyMultiplier,
        demolisher = demolisher.toDomain(),
    )
}

@Serializable
private data class DemolisherBalanceDto(
    val explosionDamage: Int,
    val blastRadius: Float,
    val throwDurationSeconds: Float,
    val throwForwardDistance: Float,
) {
    fun toDomain() = DemolisherBalance(
        explosionDamage = explosionDamage,
        blastRadius = blastRadius,
        throwDurationSeconds = throwDurationSeconds,
        throwForwardDistance = throwForwardDistance,
    )
}

@Serializable
private data class BossCombatBalanceDto(
    val largerGroupTargetChance: Float,
    val maximumDistanceFalloff: Float,
) {
    fun toDomain() = BossCombatBalance(largerGroupTargetChance, maximumDistanceFalloff)
}

@Serializable
private data class AiProfileBalanceDto(
    val profileId: String,
    val angleErrorDegrees: Float,
    val powerError: Float,
    val reactionIntervalMilliseconds: Long,
    val unitRecruitmentIntervalMilliseconds: Long,
    val progressionErrorReduction: Float,
    val progressionReactionReduction: Float,
) {
    fun toDomain() = AiProfileBalance(
        profileId = AiProfileId(profileId),
        angleErrorDegrees = angleErrorDegrees,
        powerError = powerError,
        reactionIntervalMilliseconds = reactionIntervalMilliseconds,
        unitRecruitmentIntervalMilliseconds = unitRecruitmentIntervalMilliseconds,
        progressionErrorReduction = progressionErrorReduction,
        progressionReactionReduction = progressionReactionReduction,
    )
}

@Serializable
private data class AiStrategyBalanceDto(
    val aimAngleOffsetDegrees: Float,
    val horizontalDistancePerPowerPercent: Float,
    val ammunitionScoring: AiAmmunitionScoringBalanceDto,
    val unitScoring: AiUnitScoringBalanceDto,
    val waveDispatch: AiWaveDispatchBalanceDto,
) {
    fun toDomain() = AiStrategyBalance(
        aimAngleOffsetDegrees = aimAngleOffsetDegrees,
        horizontalDistancePerPowerPercent = horizontalDistancePerPowerPercent,
        ammunitionScoring = ammunitionScoring.toDomain(),
        unitScoring = unitScoring.toDomain(),
        waveDispatch = waveDispatch.toDomain(),
    )
}

@Serializable
private data class AiAmmunitionScoringBalanceDto(
    val situationalFortressDamageWeight: Float,
    val situationalUnitDamageWeight: Float,
    val situationalFortressRadiusWeight: Float,
    val situationalUnitRadiusWeight: Float,
    val counterplayUnitDamageWeight: Float,
    val counterplayUnitRadiusWeight: Float,
    val counterplayFortressDamageWeight: Float,
    val counterplayFortressRadiusWeight: Float,
) {
    fun toDomain() = AiAmmunitionScoringBalance(
        situationalFortressDamageWeight,
        situationalUnitDamageWeight,
        situationalFortressRadiusWeight,
        situationalUnitRadiusWeight,
        counterplayUnitDamageWeight,
        counterplayUnitRadiusWeight,
        counterplayFortressDamageWeight,
        counterplayFortressRadiusWeight,
    )
}

@Serializable
private data class AiUnitScoringBalanceDto(
    val sapperPriorityScore: Float,
    val demolisherPriorityScore: Float,
    val attackDamageWeight: Float,
    val speedWeight: Float,
    val defenderVsSapperRoleBonus: Float,
    val rangedVsRangedRoleBonus: Float,
    val rangedVsMeleeRoleBonus: Float,
    val supplyCostPenaltyWeight: Float,
) {
    fun toDomain() = AiUnitScoringBalance(
        sapperPriorityScore,
        demolisherPriorityScore,
        attackDamageWeight,
        speedWeight,
        defenderVsSapperRoleBonus,
        rangedVsRangedRoleBonus,
        rangedVsMeleeRoleBonus,
        supplyCostPenaltyWeight,
    )
}

@Serializable
private data class AiWaveDispatchBalanceDto(
    val situationalMinimumReadyUnits: Int,
    val counterplayMinimumReadyUnits: Int,
    val counterplayMinimumReadyUnitsAgainstMovingEnemies: Int,
    val lowFortressHealthFraction: Float,
) {
    fun toDomain() = AiWaveDispatchBalance(
        situationalMinimumReadyUnits,
        counterplayMinimumReadyUnits,
        counterplayMinimumReadyUnitsAgainstMovingEnemies,
        lowFortressHealthFraction,
    )
}

@Serializable
private data class RewardBalanceDto(
    val winGold: Long,
    val directEnemyUnitHitGold: Long,
    val fastWinGold: Long,
    val fastWinThresholdMilliseconds: Long,
    val untouchedFortressGold: Long,
    val fortressHitStreakGoldPerHitBeyondFirst: Long,
    val fortressHitStreakStartHits: Int,
) {
    fun toDomain() = RewardBalance(
        winGold = winGold,
        directEnemyUnitHitGold = directEnemyUnitHitGold,
        fastWinGold = fastWinGold,
        fastWinThresholdMilliseconds = fastWinThresholdMilliseconds,
        untouchedFortressGold = untouchedFortressGold,
        fortressHitStreakGoldPerHitBeyondFirst = fortressHitStreakGoldPerHitBeyondFirst,
        fortressHitStreakStartHits = fortressHitStreakStartHits,
    )
}

@Serializable
private data class CampaignBalanceDto(
    val initialWindStrength: Float,
    val initialWindStrengthStep: Float,
    val initialWindStrengthBands: Int,
    val demoInitialWindStrength: Float,
    val aiDifficultyBands: List<CampaignAiDifficultyBandDto>,
) {
    fun toDomain() = CampaignBalance(
        initialWindStrength = initialWindStrength,
        initialWindStrengthStep = initialWindStrengthStep,
        initialWindStrengthBands = initialWindStrengthBands,
        demoInitialWindStrength = demoInitialWindStrength,
        aiDifficultyBands = aiDifficultyBands.map(CampaignAiDifficultyBandDto::toDomain),
    )
}

@Serializable
private data class CampaignAiDifficultyBandDto(
    val firstLevel: Int,
    val lastLevel: Int,
    val profileId: String,
    val progressionStartPercent: Int,
    val progressionEndPercent: Int,
) {
    fun toDomain() = CampaignAiDifficultyBand(
        firstLevel = firstLevel,
        lastLevel = lastLevel,
        profileId = AiProfileId(profileId),
        progressionStartPercent = progressionStartPercent,
        progressionEndPercent = progressionEndPercent,
    )
}

private fun uniqueEnums(values: List<String>, field: String): List<String> =
    values.also { require(it.toSet().size == it.size) { "$field must be unique" } }
