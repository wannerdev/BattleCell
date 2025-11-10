package com.battlecell.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val DATA_STORE_NAME = "battlecell_preferences"

private val Context.internalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME
)

object BattleCellPreferencesKeys {
    val PLAYER_PROFILE = stringPreferencesKey("player_profile")
    val ENCOUNTER_CACHE = stringPreferencesKey("encounter_cache")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

val Context.battleCellDataStore: DataStore<Preferences>
    get() = internalDataStore
