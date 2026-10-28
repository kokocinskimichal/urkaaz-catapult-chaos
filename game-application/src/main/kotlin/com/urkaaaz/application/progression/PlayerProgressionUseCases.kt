package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionPort
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SpellCatalog

class PlayerProgressionUseCases(
    private val repository: PlayerProgressionRepository,
) : LoadoutProgressionPort {
    override suspend fun snapshot(level: Int): LoadoutProgressionRead {
        if (level <= 0) {
            return LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_LEVEL)
        }

        return when (val result = repository.loadOrInitialize(INITIAL_PROFILE)) {
            is PlayerProgressionRepositoryResult.Available -> {
                if (isValidProfile(result.snapshot)) {
                    LoadoutProgressionRead.Available(result.snapshot)
                } else {
                    LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_PROFILE)
                }
            }
            is PlayerProgressionRepositoryResult.Failure ->
                LoadoutProgressionRead.Blocked(
                    when (result.reason) {
                        PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                            LoadoutProgressionFailure.STORAGE_UNAVAILABLE
                        PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                            LoadoutProgressionFailure.INVALID_PROFILE
                        PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                            LoadoutProgressionFailure.UNSUPPORTED_PROFILE_VERSION
                    },
                )
        }
    }

    override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
        LoadoutPurchaseResult.Blocked

    companion object {
        fun isValidProfile(snapshot: LoadoutProgressionSnapshot): Boolean {
            val knownSpells = SpellCatalog.all.associateBy { it.type.name }
            val knownAmmunition = AmmunitionType.values().associateBy { it.name }
            return snapshot.unlockedSpellTypes.all { id ->
                knownSpells[id]?.special == false
            } &&
                snapshot.defaultSpellTypes.isEmpty() &&
                snapshot.ammunitionStocks.all { (id, count) ->
                    knownAmmunition[id]?.unlimited == false && count >= 0
                } &&
                snapshot.specialSpellStocks.all { (id, count) ->
                    knownSpells[id]?.special == true && count >= 0
                }
        }

        val INITIAL_PROFILE = LoadoutProgressionSnapshot(
            unlockedSpellTypes = setOf(SpellType.FORTRESS_SHIELD.name),
            ammunitionStocks = emptyMap(),
            specialSpellStocks = emptyMap(),
            goldBalance = 0,
        )
    }
}
