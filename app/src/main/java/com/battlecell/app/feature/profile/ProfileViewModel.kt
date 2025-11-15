package com.battlecell.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.AttributeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val toastMessages = MutableStateFlow<String?>(null)

    val uiState = combine(
        playerRepository.playerStream,
        toastMessages
    ) { player, message ->
        ProfileUiState(
            character = player,
            isLoading = false,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(isLoading = true)
    )

    fun allocatePoints(type: AttributeType, requestedAmount: Int) {
        viewModelScope.launch {
            val player = playerRepository.playerStream.first()
            if (player == null) {
                toastMessages.value = "Create your hero first."
                return@launch
            }
            val available = player.skillPoints + player.variantSkillPoints(type)
            if (available <= 0) {
                toastMessages.value = "No sigils remain for ${type.name.lowercase()}."
                return@launch
            }
            val amount = when {
                requestedAmount <= 0 -> 1
                requestedAmount > available -> available
                else -> requestedAmount
            }
            val updated = player.spendSkillPoints(type, amount)
            if (updated == player) {
                toastMessages.value = "Unable to allocate sigils right now."
                return@launch
            }
            playerRepository.upsert(updated)
            toastMessages.value = "Allocated $amount sigils to ${type.name.lowercase().replaceFirstChar { it.titlecase() }}"
        }
    }

    fun consumeMessage() {
        toastMessages.value = null
    }
}
