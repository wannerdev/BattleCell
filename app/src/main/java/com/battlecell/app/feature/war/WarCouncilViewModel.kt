package com.battlecell.app.feature.war

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.service.MissionEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WarCouncilViewModel(
    playerRepository: PlayerRepository
) : ViewModel() {

    val uiState = playerRepository.playerStream
        .map { player ->
            if (player == null) {
                WarCouncilUiState(
                    errorMessage = "Raise a hero to receive orders.",
                    isLoading = false
                )
            } else {
                val missions = MissionEngine.entriesFor(player).map { (definition, state) ->
                    MissionDetailItem(
                        id = definition.id,
                        title = definition.title,
                        description = definition.description,
                        condition = definition.conditionSummary,
                        reward = definition.rewardDescription,
                        status = state.status,
                        progress = state.progress,
                        target = state.target,
                        notes = state.notes
                    )
                }
                WarCouncilUiState(
                    character = player,
                    missions = missions,
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WarCouncilUiState(isLoading = true)
        )
}
