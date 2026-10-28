package com.urkaaaz.application.loadout

import com.urkaaaz.contracts.PlayerLoadoutConfig

data class LoadoutAvailability(
    val level: Int,
    val unitTypes: Set<String>,
    val ammunitionTypes: Set<String>,
    val spellTypes: Set<String>,
) {
    init {
        require(level > 0) { "level must be positive" }
        require(unitTypes.all(String::isNotBlank)) { "unit identifiers must not be blank" }
        require(ammunitionTypes.all(String::isNotBlank)) {
            "ammunition identifiers must not be blank"
        }
        require(spellTypes.all(String::isNotBlank)) { "spell identifiers must not be blank" }
    }
}

data class LoadoutProgressionSnapshot(
    val unlockedSpellTypes: Set<String>,
    val ammunitionStocks: Map<String, Int>,
    val specialSpellStocks: Map<String, Int>,
    val goldBalance: Int,
    val defaultSpellTypes: Set<String> = emptySet(),
) {
    init {
        require(unlockedSpellTypes.all(String::isNotBlank)) {
            "spell identifiers must not be blank"
        }
        require(defaultSpellTypes.all(String::isNotBlank)) {
            "default spell identifiers must not be blank"
        }
        require(ammunitionStocks.keys.all(String::isNotBlank)) {
            "ammunition identifiers must not be blank"
        }
        require(specialSpellStocks.keys.all(String::isNotBlank)) {
            "special spell identifiers must not be blank"
        }
        require(goldBalance >= 0) { "gold balance must not be negative" }
        require(ammunitionStocks.values.all { it >= 0 }) {
            "ammunition stock must not be negative"
        }
        require(specialSpellStocks.values.all { it >= 0 }) {
            "special spell stock must not be negative"
        }
    }

    fun ammunitionStock(type: String): Int = ammunitionStocks[type] ?: 0

    fun specialSpellStock(type: String): Int = specialSpellStocks[type] ?: 0
}

/** Raw persisted values intentionally allow stale or malformed selections for explicit correction. */
data class StoredLoadoutSelection(
    val unitTypes: List<String>,
    val ammunitionSlots: List<String?>,
    val spellTypes: List<String>,
)

data class LoadoutCorrection(
    val category: LoadoutCategory,
    val itemId: String?,
    val reason: LoadoutCorrectionReason,
)

enum class LoadoutCategory {
    UNIT,
    AMMUNITION,
    SPELL,
}

enum class LoadoutCorrectionReason {
    UNKNOWN,
    UNAVAILABLE,
    LOCKED,
    OUT_OF_STOCK,
    DUPLICATE,
    EXCESS,
    INVALID_SLOT,
    DEFAULT_USED,
}

enum class LoadoutProgressionFailure {
    INVALID_LEVEL,
    CAMPAIGN_LEVEL_LOCKED,
    CAMPAIGN_LEVEL_COMPLETED,
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_PROFILE_VERSION,
}

enum class LoadoutStorageFailure {
    INVALID_LEVEL,
    STORAGE_UNAVAILABLE,
    INVALID_DATA,
    UNSUPPORTED_VERSION,
}

enum class LoadoutError {
    EMPTY_UNIT_SELECTION,
    EMPTY_SPELL_SELECTION,
    UNKNOWN_UNIT,
    UNIT_UNAVAILABLE,
    UNKNOWN_AMMUNITION,
    AMMUNITION_UNAVAILABLE,
    AMMUNITION_OUT_OF_STOCK,
    ROCK_SLOT_FIXED,
    UNKNOWN_SPELL,
    SPELL_UNAVAILABLE,
    SPELL_LOCKED,
    SPELL_ALREADY_UNLOCKED,
    SPECIAL_SPELL_OUT_OF_STOCK,
    INVALID_SLOT,
    DUPLICATE_SELECTION,
    SELECTION_LIMIT_REACHED,
    SPECIAL_SPELL_REQUIRES_SHOP,
}

sealed interface LoadoutProgressionRead {
    data class Available(val snapshot: LoadoutProgressionSnapshot) : LoadoutProgressionRead
    data class Blocked(val reason: LoadoutProgressionFailure) : LoadoutProgressionRead
}

sealed interface LoadoutPurchaseResult {
    data object Purchased : LoadoutPurchaseResult
    data object InsufficientGold : LoadoutPurchaseResult
    data object Blocked : LoadoutPurchaseResult
}

sealed interface LoadoutStorageResult {
    data class Success(val selection: StoredLoadoutSelection?) : LoadoutStorageResult
    data class Failure(val reason: LoadoutStorageFailure) : LoadoutStorageResult
}

sealed interface LoadoutStorageWriteResult {
    data object Saved : LoadoutStorageWriteResult
    data class Failure(val reason: LoadoutStorageFailure) : LoadoutStorageWriteResult
}

interface LoadoutProgressionPort {
    suspend fun snapshot(level: Int): LoadoutProgressionRead

    suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult
}

interface LoadoutRepository {
    suspend fun read(level: Int): LoadoutStorageResult

    suspend fun save(level: Int, loadout: MatchLoadout): LoadoutStorageWriteResult
}

sealed interface LoadoutOpenResult {
    data class Opened(
        val draft: MatchLoadout,
        val progression: LoadoutProgressionSnapshot,
        val corrections: List<LoadoutCorrection>,
    ) : LoadoutOpenResult

    data class ProgressionBlocked(val reason: LoadoutProgressionFailure) : LoadoutOpenResult
    data class StorageFailure(val reason: LoadoutStorageFailure) : LoadoutOpenResult
}

sealed interface LoadoutEditAction {
    data class SelectUnit(val unitType: String, val selected: Boolean) : LoadoutEditAction
    data class SetAmmunition(val slot: Int, val ammunitionType: String?) : LoadoutEditAction
    data class ToggleAmmunition(
        val ammunitionType: String,
        val selected: Boolean,
    ) : LoadoutEditAction
    data class ToggleSpell(val spellType: String, val selected: Boolean) : LoadoutEditAction
}

sealed interface LoadoutEditResult {
    data class Accepted(val draft: MatchLoadout) : LoadoutEditResult
    data class Rejected(val draft: MatchLoadout, val error: LoadoutError) : LoadoutEditResult
}

sealed interface LoadoutCommitResult {
    data class Committed(
        val loadout: MatchLoadout,
        val playerLoadout: PlayerLoadoutConfig,
    ) : LoadoutCommitResult

    data class Rejected(val error: LoadoutError) : LoadoutCommitResult
    data class ProgressionBlocked(val reason: LoadoutProgressionFailure) : LoadoutCommitResult
    data class StorageFailure(val reason: LoadoutStorageFailure) : LoadoutCommitResult
}

sealed interface LoadoutPurchaseOutcome {
    data object Purchased : LoadoutPurchaseOutcome
    data object InsufficientGold : LoadoutPurchaseOutcome
    data object ProgressionBlocked : LoadoutPurchaseOutcome
    data class Rejected(val error: LoadoutError) : LoadoutPurchaseOutcome
}
