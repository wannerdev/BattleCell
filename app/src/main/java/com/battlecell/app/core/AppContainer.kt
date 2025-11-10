package com.battlecell.app.core

import android.content.Context
import com.battlecell.app.data.local.battleCellDataStore
import com.battlecell.app.data.nearby.NearbyDiscoveryManager
import com.battlecell.app.data.repository.EncounterRepository
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.service.EncounterGenerator
import com.battlecell.app.domain.usecase.GetTrainingGamesUseCase
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val dataStore = appContext.battleCellDataStore

    val playerRepository: PlayerRepository = PlayerRepository(dataStore, json)
    val encounterRepository: EncounterRepository = EncounterRepository(dataStore, json)
    val getTrainingGamesUseCase: GetTrainingGamesUseCase = GetTrainingGamesUseCase()
    val nearbyDiscoveryManager: NearbyDiscoveryManager = NearbyDiscoveryManager(appContext)
    val encounterGenerator: EncounterGenerator = EncounterGenerator()
}
