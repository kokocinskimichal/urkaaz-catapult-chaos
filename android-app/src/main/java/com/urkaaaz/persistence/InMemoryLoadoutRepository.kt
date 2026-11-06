package com.urkaaaz.persistence

import com.urkaaaz.application.loadout.LoadoutRepository
import com.urkaaaz.application.loadout.LoadoutStorageFailure
import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.application.loadout.StoredLoadoutSelection

internal class InMemoryLoadoutRepository : LoadoutRepository {
    private val selections = mutableMapOf<Int, StoredLoadoutSelection>()

    override suspend fun read(level: Int): LoadoutStorageResult = synchronized(selections) {
        if (level <= 0) {
            LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_LEVEL)
        } else {
            LoadoutStorageResult.Success(selections[level])
        }
    }

    override suspend fun save(
        level: Int,
        loadout: MatchLoadout,
    ): LoadoutStorageWriteResult = synchronized(selections) {
        if (level <= 0) {
            LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.INVALID_LEVEL)
        } else {
            selections[level] = StoredLoadoutSelection(
                unitTypes = loadout.unitTypes.toList(),
                ammunitionSlots = loadout.ammunitionSlots.toList(),
                spellTypes = loadout.spellTypes.toList(),
            )
            LoadoutStorageWriteResult.Saved
        }
    }
}
