package com.battlecell.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.nearby.NearbyDiscoveryManager
import com.battlecell.app.data.nearby.NearbyDiscoverySnapshot
import com.battlecell.app.data.repository.EncounterRepository
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.service.EncounterGenerator
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
    private val encounterGenerator: EncounterGenerator
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        encounterRepository.encounterStream,
        isScanning,
        toastMessage
    ) { encounters, scanning, message ->
        SearchUiState(
            encounters = encounters.sortedByDescending { it.lastSeenEpoch },
            isScanning = scanning,
            message = message
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
                val encounters = synthesizeEncounters(snapshot)
                encounterRepository.replaceAll(encounters)
                toastMessage.value = when {
                    encounters.isEmpty() -> "No nearby signals detected."
                    encounters.size == 1 -> "1 opponent found."
                    else -> "${encounters.size} opponents found."
                }
            } catch (ex: Exception) {
                toastMessage.value = "Scan failed: ${ex.message ?: "unknown error"}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun consumeMessage() {
        toastMessage.value = null
    }

    private fun synthesizeEncounters(snapshot: NearbyDiscoverySnapshot): List<EncounterProfile> {
        val existing = uiState.value.encounters.associateBy { it.deviceFingerprint }
        val merged = mutableMapOf<String, EncounterProfile>()

        snapshot.wifiDevices.forEach { device ->
            val incoming = encounterGenerator.fromWifi(device)
            val current = merged[incoming.deviceFingerprint] ?: existing[incoming.deviceFingerprint]
            merged[incoming.deviceFingerprint] = encounterGenerator.merge(current, incoming)
        }

        snapshot.bluetoothDevices.forEach { device ->
            val incoming = encounterGenerator.fromBluetooth(device)
            val current = merged[incoming.deviceFingerprint] ?: existing[incoming.deviceFingerprint]
            merged[incoming.deviceFingerprint] = encounterGenerator.merge(current, incoming)
        }

        return merged.values.sortedByDescending { it.powerScore }
    }
}