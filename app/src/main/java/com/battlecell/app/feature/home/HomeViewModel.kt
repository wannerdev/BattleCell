package com.battlecell.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
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
                HomeUiState(
                    character = player,
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
