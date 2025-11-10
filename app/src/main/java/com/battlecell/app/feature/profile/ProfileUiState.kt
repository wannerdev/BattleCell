package com.battlecell.app.feature.profile

import com.battlecell.app.domain.model.PlayerCharacter

data class ProfileUiState(
    val character: PlayerCharacter? = null,
    val isLoading: Boolean = true,
    val message: String? = null
)
