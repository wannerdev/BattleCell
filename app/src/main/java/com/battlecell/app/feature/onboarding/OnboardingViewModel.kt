package com.battlecell.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.service.MissionEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value.take(MAX_NAME_LENGTH), errorMessage = null) }
    }

    fun submit() {
        val trimmedName = _uiState.value.name.trim()
        when {
            trimmedName.length < MIN_NAME_LENGTH -> {
                _uiState.update {
                    it.copy(errorMessage = "Name must be at least $MIN_NAME_LENGTH characters long.")
                }
            }

            else -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
                    val newPlayer = MissionEngine.bootstrap(PlayerCharacter(name = trimmedName))
                    playerRepository.upsert(newPlayer)
                    playerRepository.markOnboardingCompleted(true)
                    _events.send(OnboardingEvent.Completed)
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    companion object {
        private const val MIN_NAME_LENGTH = 3
        private const val MAX_NAME_LENGTH = 20
    }
}

sealed interface OnboardingEvent {
    data object Completed : OnboardingEvent
}
