package com.battlecell.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.nearby.NearbyDiscoveryManager
import com.battlecell.app.data.nearby.NearbyDiscoverySnapshot
import com.battlecell.app.data.repository.EncounterRepository
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.EncounterArchetype
import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.service.EncounterGenerator
import com.battlecell.app.domain.service.MissionEngine
import com.battlecell.app.domain.model.mission.MissionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(
    private val encounterRepository: EncounterRepository,
    private val nearbyDiscoveryManager: NearbyDiscoveryManager,
    private val encounterGenerator: EncounterGenerator,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)
    private val playerState = playerRepository.playerStream.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val uiState = combine(
        encounterRepository.encounterStream,
        isScanning,
        toastMessage,
        playerState
    ) { encounters, scanning, message, player ->
        SearchUiState(
            encounters = encounters.sortedByDescending { it.lastSeenEpoch },
            isScanning = scanning,
            message = message,
            player = player
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun manualScan() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    nearbyDiscoveryManager.discover()
                }
                val encounters = synthesizeEncounters(snapshot, playerState.value)
                encounterRepository.replaceAll(encounters)
                updateMissionsAfterScan(encounters)
                toastMessage.value = when {
                    encounters.isEmpty() -> "No rival banners answered the horn."
                    encounters.size == 1 -> "One challenger stirs nearby."
                    else -> "${encounters.size} challengers muster in the vicinity."
                }
            } catch (ex: Exception) {
                toastMessage.value = "The horn faltered: ${ex.message ?: "unknown cause"}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun consumeMessage() {
        toastMessage.value = null
    }

    fun lockEncounter(profile: EncounterProfile) {
        viewModelScope.launch {
            encounterRepository.upsert(profile.copy(isChallenged = true))
        }
    }

    internal fun synthesizeEncounters(
        snapshot: NearbyDiscoverySnapshot,
        player: PlayerCharacter? = playerState.value
    ): List<EncounterProfile> {
        val existing = uiState.value.encounters.associateBy { it.deviceFingerprint }
        return EncounterMerge.merge(existing, snapshot, encounterGenerator, player)
    }

    private suspend fun updateMissionsAfterScan(encounters: List<EncounterProfile>) {
        val player = playerState.value ?: return
        var updated = MissionEngine.process(player, MissionEvent.HornSounded)
        if (encounters.any { it.archetype == EncounterArchetype.DRAGON }) {
            updated = MissionEngine.process(updated, MissionEvent.DragonSighted)
        }
        if (updated != player) {
            playerRepository.upsert(updated)
        }
    }
}