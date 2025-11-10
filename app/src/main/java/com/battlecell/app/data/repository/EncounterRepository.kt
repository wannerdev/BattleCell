package com.battlecell.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.battlecell.app.data.local.BattleCellPreferencesKeys
import com.battlecell.app.domain.model.EncounterProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncounterRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val serializer = ListSerializer(EncounterProfile.serializer())

    val encounterStream: Flow<List<EncounterProfile>> = dataStore.data.map { preferences ->
        preferences[BattleCellPreferencesKeys.ENCOUNTER_CACHE]
            ?.takeIf { it.isNotEmpty() }
            ?.let { json.decodeFromString(serializer, it) }
            ?: emptyList()
    }

    suspend fun upsert(profile: EncounterProfile) {
        dataStore.edit { preferences ->
            val current = preferences[BattleCellPreferencesKeys.ENCOUNTER_CACHE]
                ?.let { json.decodeFromString(serializer, it) }
                ?.toMutableList()
                ?: mutableListOf()
            val existingIndex = current.indexOfFirst { it.deviceFingerprint == profile.deviceFingerprint }
            if (existingIndex >= 0) {
                current[existingIndex] = profile
            } else {
                current.add(profile)
            }
            preferences[BattleCellPreferencesKeys.ENCOUNTER_CACHE] = json.encodeToString(serializer, current)
        }
    }

    suspend fun replaceAll(profiles: List<EncounterProfile>) {
        dataStore.edit { preferences ->
            preferences[BattleCellPreferencesKeys.ENCOUNTER_CACHE] =
                json.encodeToString(serializer, profiles)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(BattleCellPreferencesKeys.ENCOUNTER_CACHE)
        }
    }
}
