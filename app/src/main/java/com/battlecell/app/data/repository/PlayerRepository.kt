package com.battlecell.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.battlecell.app.data.local.BattleCellPreferencesKeys
import com.battlecell.app.domain.model.PlayerCharacter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlayerRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val playerStream: Flow<PlayerCharacter?> = dataStore.data.map { preferences ->
        preferences[BattleCellPreferencesKeys.PLAYER_PROFILE]
            ?.let { json.decodeFromString(PlayerCharacter.serializer(), it) }
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BattleCellPreferencesKeys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun markOnboardingCompleted(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[BattleCellPreferencesKeys.ONBOARDING_COMPLETED] = value
        }
    }

    suspend fun upsert(player: PlayerCharacter): PlayerCharacter {
        dataStore.edit { preferences ->
            preferences[BattleCellPreferencesKeys.PLAYER_PROFILE] =
                json.encodeToString(PlayerCharacter.serializer(), player)
        }
        return player
    }

    suspend fun update(transform: (PlayerCharacter?) -> PlayerCharacter): PlayerCharacter {
        var updated: PlayerCharacter? = null
        dataStore.edit { preferences ->
            val current = preferences[BattleCellPreferencesKeys.PLAYER_PROFILE]
                ?.let { json.decodeFromString(PlayerCharacter.serializer(), it) }
            updated = transform(current)
            preferences[BattleCellPreferencesKeys.PLAYER_PROFILE] =
                json.encodeToString(PlayerCharacter.serializer(), requireNotNull(updated))
        }
        return requireNotNull(updated)
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(BattleCellPreferencesKeys.PLAYER_PROFILE)
            preferences.remove(BattleCellPreferencesKeys.ONBOARDING_COMPLETED)
        }
    }
}
