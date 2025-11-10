package com.battlecell.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.usecase.GetTrainingGamesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TrainingViewModel(
    playerRepository: PlayerRepository,
    getTrainingGamesUseCase: GetTrainingGamesUseCase
) : ViewModel() {

    private val trainingGames = MutableStateFlow(getTrainingGamesUseCase())

    val uiState = combine(
        trainingGames,
        playerRepository.playerStream
    ) { games, character ->
        TrainingUiState(
            games = games,
            character = character,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrainingUiState(isLoading = true)
    )
}
