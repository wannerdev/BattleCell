package com.battlecell.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.service.MissionEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    playerRepository: PlayerRepository
) : ViewModel() {

    val uiState = playerRepository.playerStream
        .map { player ->
            if (player == null) {
                HomeUiState(
                    character = null,
                    isLoading = false,
                    errorMessage = "Create your hero to begin."
                )
            } else {
                val missions = MissionEngine.entriesFor(player).map { (definition, state) ->
                    HomeMissionItem(
                        id = definition.id,
                        title = definition.title,
                        description = definition.description,
                        condition = definition.conditionSummary,
                        reward = definition.rewardDescription,
                        status = state.status,
                        progress = state.progress,
                        target = state.target
                    )
                }
                HomeUiState(
                    character = player,
                    missions = missions,
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(isLoading = true)
        )
}
