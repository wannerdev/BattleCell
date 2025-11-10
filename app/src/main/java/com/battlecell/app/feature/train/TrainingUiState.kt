package com.battlecell.app.feature.train

import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.model.TrainingGameDefinition

data class TrainingUiState(
    val games: List<TrainingGameDefinition> = emptyList(),
    val character: PlayerCharacter? = null,
    val isLoading: Boolean = true
)
