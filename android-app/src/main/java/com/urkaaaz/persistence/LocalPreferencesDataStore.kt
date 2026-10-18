package com.urkaaaz.persistence

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.urkaazPreferencesDataStore by preferencesDataStore(
    name = "urkaaz_local_progress",
)
