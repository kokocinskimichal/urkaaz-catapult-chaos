package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.urkaaaz.application.progression.PlayerProgressResetFailure
import com.urkaaaz.application.progression.PlayerProgressResetRepository
import com.urkaaaz.application.progression.PlayerProgressResetResult
import java.io.IOException

class DataStorePlayerProgressResetRepository(
    private val dataStore: DataStore<Preferences>,
) : PlayerProgressResetRepository {
    override suspend fun reset(): PlayerProgressResetResult =
        try {
            dataStore.updateData { emptyPreferences() }
            PlayerProgressResetResult.Reset
        } catch (error: IOException) {
            PlayerProgressResetResult.Failure(
                PlayerProgressResetFailure.STORAGE_UNAVAILABLE,
            )
        }
}
