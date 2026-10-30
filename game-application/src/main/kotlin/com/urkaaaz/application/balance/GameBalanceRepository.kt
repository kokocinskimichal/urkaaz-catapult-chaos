package com.urkaaaz.application.balance

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.domain.BossAttackBalance
import com.urkaaaz.domain.BossAttackTarget
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.domain.BossCollisionBalance
import com.urkaaaz.domain.BossMovementBalance
import com.urkaaaz.domain.BossMovementKind
import com.urkaaaz.domain.BossSpecialAbility
import com.urkaaaz.domain.GameBalanceConfig
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
    private val openResource: (String) -> java.io.InputStream?,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun load(): GameBalanceRead = try {
        val input = openResource(GAME_BALANCE_RESOURCE)
            ?: return GameBalanceRead.Failure(GameBalanceFailure.MISSING_RESOURCE)
        val content = input.bufferedReader().use { it.readText() }
        val dto = json.decodeFromString<GameBalanceDocumentDto>(content)
        val balance = dto.toDomain()
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

        val bundled = GameBalanceRepository { path ->
            GameBalanceRepository::class.java.classLoader.getResourceAsStream(path)
        }
    }
}

@Serializable
private data class GameBalanceDocumentDto(
    val schemaVersion: Int,
    val balanceRevision: String,
    val bosses: List<BossProfileDto>,
) {
    fun toDomain() = GameBalanceConfig(
        schemaVersion = schemaVersion,
        balanceRevision = balanceRevision,
        bosses = bosses.map(BossProfileDto::toDomain),
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
    val immuneAmmunitionTypes: List<String> = emptyList(),
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
) {
    fun toDomain() = BossAttackBalance(
        target = BossAttackTarget.valueOf(target),
        damage = damage,
        minimumDamage = minimumDamage,
        rangeWorldUnits = rangeWorldUnits,
        cooldownSeconds = cooldownSeconds,
        initialCooldownSeconds = initialCooldownSeconds,
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
