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

    fun allocatePoints(type: AttributeType, amount: Int) {
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
            val desiredAmount = amount.coerceAtLeast(1)
            val spendAmount = desiredAmount.coerceAtMost(available)
            if (spendAmount <= 0) {
                toastMessages.value = "No sigils remain for ${type.name.lowercase()}."
                return@launch
            }
            val updated = player.spendSkillPoints(type, spendAmount)
            playerRepository.upsert(updated)
            val label = type.name.lowercase().replaceFirstChar { it.titlecase() }
            val noun = if (spendAmount == 1) "sigil" else "sigils"
            val suffix = if (spendAmount < desiredAmount) " (limited by reserves)" else ""
            toastMessages.value = "Allocated $spendAmount $noun to $label$suffix"
        }
    }

    fun allocatePoint(type: AttributeType) {
        allocatePoints(type, 1)
    }

    fun consumeMessage() {
        toastMessages.value = null
    }
}
