package com.battlecell.app.feature.home

import com.battlecell.app.domain.model.PlayerCharacter

data class HomeUiState(
    val character: PlayerCharacter? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
